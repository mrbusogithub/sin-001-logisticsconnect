package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/**
 * Calculates an estimated arrival time for a hub.
 *
 * For each request, Transit gets hub details from the Hub Service and the
 * current delay stage from the Delay Stage Service.
 */

public class TransitServiceApp {

    private static final ObjectMapper JSON = new ObjectMapper();

    // Default addresses of the services Transit depends on.
    private static final String DEFAULT_HUB_URL =
            "http://localhost:7051";
    private static final String DEFAULT_DELAY_URL =
            "http://localhost:7052";

    // Reuse this client for outgoing HTTP requests.
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7053);

        app.get("/health", ctx -> ctx.result("OK"));

        // TODO (Calculates estimated arrival windows based on hub and delay stage.)
        // Add domain endpoints for transit-service here.
    }

    public static int calculateEtaMinutes(int i, int i1, int i2) {
        return 0;
    }

    public static String normalizeHubId(String s) {
        return null;
    }
}

// MQ TODO: subscribes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.logisticsconnect.mq.MqConfig)
