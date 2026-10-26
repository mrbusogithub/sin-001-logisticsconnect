package co.wethinkcode.logisticsconnect;

/**
 * Makes the alert decision independently of JMS and HTTP.
 *
 * That separation makes threshold boundaries and duplicate handling easy
 * to test without starting the web server or a real message broker.
 */

public class AlertEvaluator {
}
