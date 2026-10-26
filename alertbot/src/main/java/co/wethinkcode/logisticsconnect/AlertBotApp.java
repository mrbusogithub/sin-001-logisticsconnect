package co.wethinkcode.logisticsconnect;

import co.wethinkcode.logisticsconnect.mq.MqConfig;
import co.wethinkcode.logisticsconnect.mq.PackageStatusConsumer;
import co.wethinkcode.logisticsconnect.mq.PackageStatusEvent;
import io.javalin.Javalin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Consumes package-status events and generates simulated critical-delay alerts.
 *
 * A real external notification API is intentionally not required for this demo.
 */

public class AlertBotApp {

    private static final int DEFAULT_PORT = 7054;
    private static final int MAX_RETAINED_ALERTS = 100;

    public static void main(String[] args) {
        int port = readSetting(
                "ALERTBOT_SERVICE_PORT",
                DEFAULT_PORT,
                1,
                65535);

        // AlertBot triggers an alert when stage >= this value.
        int threshold = readSetting(
                "ALERT_STAGE_THRESHOLD",
                5,
                0,
                8);

        AlertEvaluator evaluator = new AlertEvaluator(threshold);

        // A thread-safe list lets the REST endpoint expose recently generated
        // alerts while the JMS listener adds new alerts in the background.
        CopyOnWriteArrayList<Map<String, Object>> alerts =
                new CopyOnWriteArrayList<>();

        PackageStatusConsumer consumer = new PackageStatusConsumer(
                event -> handleEvent(event, evaluator, alerts),
                System.getenv().getOrDefault(
                        "ALERTBOT_JMS_CLIENT_ID",
                        "logisticsconnect-alertbot"),
                System.getenv().getOrDefault(
                        "ALERTBOT_JMS_SUBSCRIPTION",
                        "alertbot-package-status"));

        // Keep the application HTTP-responsive if ActiveMQ is temporarily down.
        consumer.start();

        Runtime.getRuntime().addShutdownHook(
                new Thread(consumer::close));

        Javalin app = Javalin.create().start(port);

        app.get("/health", ctx -> ctx.json(Map.of(
                "status", "UP",
                "service", "alertbot",
                "consumerConnected", consumer.isConnected(),
                "topic", MqConfig.TOPIC,
                "threshold", threshold,
                "thresholdRule", "stage >= threshold",
                "notificationMode", "simulated",
                "alertCount", alerts.size()
        )));

        // TODO (Posts proactive delay notifications to public transit social media pages (simulated).)
        // Mechanism: Outbound webhook, simulated social post

        // This endpoint makes simulated notifications easy to demonstrate.
        app.get("/alerts", ctx -> ctx.json(List.copyOf(alerts)));
    }

    /**
     * Evaluates one validated event and records a simulated notification
     * only when the threshold rule is met.
     */
    private static void handleEvent(
            PackageStatusEvent event,
            AlertEvaluator evaluator,
            CopyOnWriteArrayList<Map<String, Object>> alerts) {

        if (!evaluator.shouldAlert(event)) {
            System.out.printf(
                    "[AlertBot] No alert for hub %s at stage %d "
                            + "(threshold %d).%n",
                    event.getHubId(),
                    event.getStage(),
                    evaluator.getThreshold());
            return;
        }

        Map<String, Object> alert = Map.of(
                "hubId", event.getHubId(),
                "stage", event.getStage(),
                "timestamp", event.getTimestamp(),
                "threshold", evaluator.getThreshold(),
                "simulated", true,
                "message", "Critical delay detected for hub "
                        + event.getHubId()
                        + " at stage "
                        + event.getStage()
        );

        // Keep a small, bounded alert history for the demo endpoint.
        synchronized (alerts) {
            if (alerts.size() >= MAX_RETAINED_ALERTS) {
                alerts.remove(0);
            }

            alerts.add(alert);
        }

        // A visible console alert provides simple evidence for the demo.
        System.out.printf(
                "[SIMULATED ALERT] hub=%s stage=%d threshold=%d timestamp=%s%n",
                event.getHubId(),
                event.getStage(),
                evaluator.getThreshold(),
                event.getTimestamp());
    }

    /**
     * Reads a numeric environment setting and validates its supported range.
     */
    private static int readSetting(
            String name,
            int defaultValue,
            int minimum,
            int maximum) {

        String raw = System.getenv(name);

        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }

        try {
            int value = Integer.parseInt(raw.trim());

            if (value < minimum || value > maximum) {
                throw new IllegalArgumentException(
                        name + " must be between "
                                + minimum + " and " + maximum + ".");
            }

            return value;

        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    name + " must be a valid integer.",
                    exception);
        }
    }
}

// MQ TODO (stretch goal): subscribes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.logisticsconnect.mq.MqConfig)
