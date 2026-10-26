package co.wethinkcode.logisticsconnect.mq;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Manages a durable ActiveMQ topic subscription.
 *
 * A background thread keeps the REST application independent of broker
 * startup. Failed connections are retried rather than crashing the service.
 */

public final class PackageStatusConsumer implements AutoCloseable {

    private final Consumer<PackageStatusEvent> eventHandler;
    private final String clientId;
    private final String subscriptionName;

    private volatile boolean running;
    private volatile boolean connected;
    private volatile Connection activeConnection;
    private Thread worker;

    public PackageStatusConsumer(
            Consumer<PackageStatusEvent> eventHandler,
            String clientId,
            String subscriptionName) {

        this.eventHandler = eventHandler;
        this.clientId = clientId;
        this.subscriptionName = subscriptionName;
    }

    /**
     * Starts the consumer asynchronously so REST can start even if the
     * broker is temporarily offline.
     */
    public synchronized void start() {
        if (running) {
            return;
        }

        running = true;

        worker = new Thread(
                this::consumeForever,
                "package-status-consumer-" + clientId);

        worker.setDaemon(true);
        worker.start();
    }

    public boolean isConnected() {
        return connected;
    }

    private void consumeForever() {
        while (running) {
            Connection connection = null;
            Session session = null;
            MessageConsumer subscriber = null;

            // The JMS exception listener sets this when the connection fails.
            AtomicBoolean connectionFailed = new AtomicBoolean(false);

            try {
                connection = MqConfig.createConnection();
                activeConnection = connection;

                // Durable subscribers require a stable client ID.
                connection.setClientID(clientId);

                session = connection.createSession(
                        false,
                        Session.AUTO_ACKNOWLEDGE);

                Topic topic = session.createTopic(MqConfig.TOPIC);

                // The broker retains persistent topic events for this
                // subscription while the consumer is offline.
                subscriber = session.createDurableSubscriber(
                        topic,
                        subscriptionName);

                connection.setExceptionListener(exception -> {
                    System.err.println(
                            "ActiveMQ connection failed for " + clientId
                                    + ": " + exception.getMessage());

                    connectionFailed.set(true);
                });

                subscriber.setMessageListener(this::handleMessage);
                connection.start();

                connected = true;

                System.out.printf(
                        "[%s] Subscribed to topic %s%n",
                        clientId,
                        MqConfig.TOPIC);

                while (running && !connectionFailed.get()) {
                    Thread.sleep(250);
                }

            } catch (JMSException exception) {
                if (running) {
                    System.err.printf(
                            "[%s] Could not connect to ActiveMQ: %s%n",
                            clientId,
                            exception.getMessage());
                }

            } catch (InterruptedException exception) {
                // close() interrupts the worker during normal shutdown.
                Thread.currentThread().interrupt();
                running = false;

            } finally {
                connected = false;

                closeQuietly(subscriber);
                closeQuietly(session);
                closeQuietly(connection);

                if (activeConnection == connection) {
                    activeConnection = null;
                }
            }

            if (running) {
                pauseBeforeReconnect();
            }
        }
    }

    /**
     * Invalid messages are logged and ignored, so one poison message
     * cannot terminate the consumer loop.
     */
    private void handleMessage(Message message) {
        try {
            if (!(message instanceof TextMessage textMessage)) {
                System.err.println(
                        "Ignored non-text package-status message.");
                return;
            }

            PackageStatusEvent event =
                    PackageStatusEvent.fromJson(textMessage.getText());

            eventHandler.accept(event);

        } catch (IOException | IllegalArgumentException exception) {
            System.err.println(
                    "Ignored invalid package-status message: "
                            + exception.getMessage());

        } catch (RuntimeException exception) {
            // Protect message consumption from an unexpected handler error.
            System.err.println(
                    "Package-status handler failed: "
                            + exception.getMessage());
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }

    private void pauseBeforeReconnect() {
        try {
            Thread.sleep(MqConfig.RECONNECT_DELAY_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    @Override
    public synchronized void close() {
        running = false;

        // Close the connection as well as interrupting the background worker.
        Connection connection = activeConnection;

        if (connection != null) {
            closeQuietly(connection);
        }

        if (worker != null) {
            worker.interrupt();
        }
    }

    private static void closeQuietly(MessageConsumer consumer) {
        if (consumer != null) {
            try {
                consumer.close();
            } catch (JMSException ignored) {
                // Best-effort cleanup.
            }
        }
    }

    private static void closeQuietly(Session session) {
        if (session != null) {
            try {
                session.close();
            } catch (JMSException ignored) {
                // Best-effort cleanup.
            }
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (JMSException ignored) {
                // Best-effort cleanup.
            }
        }
    }
}
