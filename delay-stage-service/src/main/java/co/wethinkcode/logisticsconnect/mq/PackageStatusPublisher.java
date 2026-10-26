package co.wethinkcode.logisticsconnect.mq;

import com.fasterxml.jackson.core.JsonProcessingException;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

/**
 * Publishes package-status changes without exposing JMS implementation
 * details to the REST route.
 */

public class PackageStatusPublisher {

    private final String brokerUrl;
    private final String topicName;

    public PackageStatusPublisher() {
        this(MqConfig.BROKER_URL, MqConfig.TOPIC);
    }

    // Injectable settings allow the publisher to be tested independently.
    public PackageStatusPublisher(String brokerUrl, String topicName) {
        this.brokerUrl = brokerUrl;
        this.topicName = topicName;
    }

    public void publish(PackageStatusEvent event) {
        Connection connection = null;
        Session session = null;
        MessageProducer producer = null;

        try {
            connection = MqConfig.createConnection(brokerUrl);
            session = connection.createSession(
                    false,
                    Session.AUTO_ACKNOWLEDGE);

            Topic topic = session.createTopic(topicName);
            producer = session.createProducer(topic);

            // Persistent delivery supports offline durable subscribers.
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);

            TextMessage message =
                    session.createTextMessage(event.toJson());

            // This property is useful when inspecting messages in ActiveMQ.
            message.setStringProperty("eventKey", event.eventKey());

            connection.start();
            producer.send(message);

        } catch (JMSException | JsonProcessingException exception) {
            // The REST route turns this into a controlled HTTP 503 response.
            throw new PublishException(
                    "Could not publish the package-status event.",
                    exception);

        } finally {
            closeQuietly(producer);
            closeQuietly(session);
            closeQuietly(connection);
        }
    }

    private static void closeQuietly(MessageProducer producer) {
        if (producer != null) {
            try {
                producer.close();
            } catch (JMSException ignored) {
                // A close failure must not hide the original send result.
            }
        }
    }

    private static void closeQuietly(Session session) {
        if (session != null) {
            try {
                session.close();
            } catch (JMSException ignored) {
                // Cleanup is best-effort.
            }
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (JMSException ignored) {
                // Cleanup is best-effort.
            }
        }
    }

    public static final class PublishException extends RuntimeException {

        public PublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
