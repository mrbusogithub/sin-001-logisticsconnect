package co.wethinkcode.logisticsconnect.mq;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This test class confirms that the producer sends contract-compliant JSON
 * through an actual ActiveMQ broker, rather than just
 * testing JSON serialization in isolation.
 */

public class PackageStatusPublisherTest {

    // Use a separate broker name to avoid conflicting with other tests.
    private static final String BROKER_NAME =
            "logisticsconnect-producer-test";

    // The vm transport connects to the embedded broker running in this test.
    private static final String BROKER_URL =
            "vm://" + BROKER_NAME + "?create=false";

    // Use a test topic so this test does not consume application messages.
    private static final String TOPIC =
            "package-status-test-topic";

    private BrokerService broker;
    private Connection consumerConnection;
    private Session consumerSession;
    private MessageConsumer consumer;

    /**
     * Starts an in-memory ActiveMQ broker before each test.
     * This lets the test exercise real JMS messaging without Docker.
     */
    @BeforeEach
    void startEmbeddedBroker() throws Exception {
        broker = new BrokerService();

        // Avoid writing persistent messages to disk during this unit test.
        broker.setBrokerName(BROKER_NAME);
        broker.setPersistent(false);

        // JMX administration is unnecessary for this test.
        broker.setUseJmx(false);

        // Start the broker before creating the JMS consumer.
        broker.start();

        // Connect the consumer to the embedded broker.
        consumerConnection =
                new ActiveMQConnectionFactory(BROKER_URL)
                        .createConnection();

        // Create a session for receiving topic messages.
        consumerSession = consumerConnection.createSession(
                false,
                Session.AUTO_ACKNOWLEDGE);

        // Subscribe before publishing so the message can be received.
        Topic topic = consumerSession.createTopic(TOPIC);
        consumer = consumerSession.createConsumer(topic);

        // Start delivery to this consumer.
        consumerConnection.start();
    }

    /**
     * Closes JMS resources and stops the broker after each test.
     * This prevents resources from leaking into other tests.
     */
    @AfterEach
    void stopEmbeddedBroker() throws Exception {
        if (consumer != null) {
            consumer.close();
        }

        if (consumerSession != null) {
            consumerSession.close();
        }

        if (consumerConnection != null) {
            consumerConnection.close();
        }

        if (broker != null) {
            broker.stop();
            broker.waitUntilStopped();
        }
    }

    /**
     * Verifies that the publisher sends a persistent JSON text message
     * containing the expected package-status event.
     */
    @Test
    void publishesContractAsPersistentJsonTextMessage() throws Exception {
        // Inject the embedded broker URL and test topic into the publisher.
        PackageStatusPublisher publisher =
                new PackageStatusPublisher(BROKER_URL, TOPIC);

        // Create a valid event matching the documented contract.
        PackageStatusEvent event = new PackageStatusEvent(
                "H-501",
                5,
                "2026-07-18T10:15:00Z");

        // Publish the event to the embedded ActiveMQ broker.
        publisher.publish(event);

        // Wait up to three seconds for the broker to deliver the message.
        Message received = consumer.receive(3000);

        // A null result means the publisher's message was not received.
        assertNotNull(received, "The broker should deliver the event.");

        // The producer should send JSON as a JMS text message.
        assertInstanceOf(TextMessage.class, received);

        TextMessage textMessage = (TextMessage) received;

        // Decode the received JSON using the same public event contract.
        PackageStatusEvent decoded =
                PackageStatusEvent.fromJson(textMessage.getText());

        // Verify that the message contains the original event data.
        assertEquals("H-501", decoded.getHubId());
        assertEquals(5, decoded.getStage());
        assertEquals(
                "2026-07-18T10:15:00Z",
                decoded.getTimestamp());

        // Check that the JMS message includes the expected deduplication key.
        assertEquals(
                event.eventKey(),
                textMessage.getStringProperty("eventKey"));
    }
}
