package com.example.orders;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * The order API, answering API Gateway v2 HTTP requests. CloudFront forwards
 * /api/* here verbatim, so the paths handleRequest matches are the paths the
 * browser asks for.
 *
 * <p>ORDERS_TABLE is set by Wayfinder.yaml from the `db` component's output.
 * The image's CMD names {@code com.example.orders.Handler::handleRequest}.
 */
public final class Handler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    static final int DEFAULT_LIST_LIMIT = 50;
    static final int MAX_LIST_LIMIT = 200;

    static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final OrderStore store;
    private final String release;

    /** Called by the Lambda runtime, once per cold start. */
    public Handler() {
        String table = System.getenv("ORDERS_TABLE");
        if (table == null || table.isEmpty()) {
            throw new IllegalStateException("ORDERS_TABLE is not set; Wayfinder.yaml sets it from the db component");
        }
        DynamoDbClient client = DynamoDbClient.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
        this.store = new DynamoOrderStore(client, table);
        this.release = readRelease();
        log("starting", Map.of("release", release, "table", table));
    }

    Handler(OrderStore store, String release) {
        this.store = store;
        this.release = release;
    }

    /**
     * Routes one request. An unknown path is a 404 rather than a fallthrough to
     * the SPA, so a mistyped API call does not come back as HTML the browser
     * then fails to parse as JSON.
     */
    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent req, Context context) {
        String path = req.getRawPath();
        String method = req.getRequestContext() == null || req.getRequestContext().getHttp() == null
                ? ""
                : req.getRequestContext().getHttp().getMethod();
        switch (path == null ? "" : path) {
            case "/api/healthz":
                return json(200, Map.of("status", "ok", "release", release));
            case "/api/orders":
                if ("POST".equals(method)) {
                    return placeOrder(req);
                }
                if ("GET".equals(method)) {
                    return listOrders(req);
                }
                return json(405, error(method + " is not allowed on /api/orders"));
            default:
                return json(404, error("no such endpoint: " + path));
        }
    }

    private APIGatewayV2HTTPResponse placeOrder(APIGatewayV2HTTPEvent req) {
        Order submitted;
        try {
            submitted = JSON.readValue(req.getBody() == null ? "" : req.getBody(), Order.class);
        } catch (JsonProcessingException e) {
            return json(400, error("the request body is not valid JSON: " + e.getOriginalMessage()));
        }
        if (submitted == null) {
            return json(400, error("the request body is not valid JSON: it is empty"));
        }
        String problem = submitted.problem();
        if (problem != null) {
            return json(400, error(problem));
        }

        Order order = submitted.accepted(UUID.randomUUID().toString(), Instant.now().truncatedTo(ChronoUnit.MICROS));
        try {
            store.put(order);
        } catch (RuntimeException e) {
            log("writing the order", Map.of("order_id", order.orderId(), "error", String.valueOf(e.getMessage())));
            return json(500, error("the order could not be saved"));
        }
        return json(201, order);
    }

    private APIGatewayV2HTTPResponse listOrders(APIGatewayV2HTTPEvent req) {
        int limit = DEFAULT_LIST_LIMIT;
        String raw = req.getQueryStringParameters() == null ? null : req.getQueryStringParameters().get("limit");
        if (raw != null && !raw.isEmpty()) {
            int n;
            try {
                n = Integer.parseInt(raw);
            } catch (NumberFormatException e) {
                n = 0;
            }
            if (n < 1) {
                return json(400, error("limit must be a positive whole number, got \"" + raw + "\""));
            }
            limit = Math.min(n, MAX_LIST_LIMIT);
        }

        List<Order> found;
        try {
            found = store.list(limit);
        } catch (RuntimeException e) {
            log("listing orders", Map.of("error", String.valueOf(e.getMessage())));
            return json(500, error("the orders could not be read"));
        }
        return json(200, Map.of("orders", found));
    }

    private static Map<String, String> error(String message) {
        return Map.of("error", message);
    }

    private static APIGatewayV2HTTPResponse json(int status, Object body) {
        String encoded;
        try {
            encoded = JSON.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            status = 500;
            encoded = "{\"error\":\"the response could not be encoded\"}";
        }
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(status)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(encoded)
                .build();
    }

    /** One JSON line per event, which is what CloudWatch Logs Insights parses. */
    private static void log(String msg, Map<String, String> fields) {
        try {
            Map<String, Object> line = new java.util.LinkedHashMap<>();
            line.put("msg", msg);
            line.putAll(fields);
            System.out.println(JSON.writeValueAsString(line));
        } catch (JsonProcessingException e) {
            System.out.println(msg);
        }
    }

    private static String readRelease() {
        try (InputStream in = Handler.class.getResourceAsStream("/release.properties")) {
            if (in == null) {
                return "dev";
            }
            Properties p = new Properties();
            p.load(in);
            return p.getProperty("release", "dev");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
