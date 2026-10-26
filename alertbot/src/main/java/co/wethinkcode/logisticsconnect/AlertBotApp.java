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

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7054);

        app.get("/health", ctx -> ctx.result("OK"));

        // TODO (Posts proactive delay notifications to public transit social media pages (simulated).)
        // Mechanism: Outbound webhook, simulated social post
    }
}

// MQ TODO (stretch goal): subscribes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.logisticsconnect.mq.MqConfig)
