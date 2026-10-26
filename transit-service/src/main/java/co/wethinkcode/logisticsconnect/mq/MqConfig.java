package co.wethinkcode.logisticsconnect.mq;

import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.Connection;
import javax.jms.JMSException;

/**
 * Shared by every producer/consumer service that talks to the "package-status-topic"
 * ActiveMQ topic. Duplicated into each participating service's own source tree,
 * since these are independent Maven projects with no shared parent pom.
 */
public final class MqConfig {

    public static final String BROKER_URL = setting(
            "ACTIVEMQ_BROKER_URL",
            "tcp://localhost:61616");

    public static final String TOPIC = setting(
            "PACKAGE_STATUS_TOPIC",
            "package-status-topic");

    public static final long RECONNECT_DELAY_MILLIS =
            positiveLongSetting(
                    "ACTIVEMQ_RECONNECT_DELAY_MS",
                    5000L);

    private MqConfig() {
    }

    /**
     * Creates a broker connection using configured credentials when supplied.
     */
    public static Connection createConnection() throws JMSException {
        return createConnection(BROKER_URL);
    }

    /**
     * The overload also makes it possible to test against an embedded broker.
     */
    public static Connection createConnection(String brokerUrl)
            throws JMSException {

        ActiveMQConnectionFactory factory =
                new ActiveMQConnectionFactory(brokerUrl);

        String username = System.getenv("ACTIVEMQ_USERNAME");

        if (username != null && !username.isBlank()) {
            String password = System.getenv().getOrDefault(
                    "ACTIVEMQ_PASSWORD",
                    "");

            return factory.createConnection(username, password);
        }

        return factory.createConnection();
    }

    private static String setting(String name, String defaultValue) {
        String value = System.getenv(name);

        return value == null || value.isBlank()
                ? defaultValue
                : value.trim();
    }

    private static long positiveLongSetting(
            String name,
            long defaultValue) {

        String raw = System.getenv(name);

        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }

        try {
            long value = Long.parseLong(raw.trim());

            if (value <= 0) {
                throw new IllegalArgumentException(
                        name + " must be greater than zero.");
            }

            return value;

        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    name + " must be a valid positive integer.",
                    exception);
        }
    }
}
