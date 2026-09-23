package com.example.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.JsonNode;

class HandlerTest {

    /** Keeps orders in memory, and fails every call when broken is set. */
    static final class MemoryStore implements OrderStore {
        final List<Order> orders = new ArrayList<>();
        boolean broken;

        @Override
        public void put(Order order) {
            if (broken) {
                throw new IllegalStateException("table unavailable");
            }
            orders.add(order);
        }

        @Override
        public List<Order> list(int limit) {
            if (broken) {
                throw new IllegalStateException("table unavailable");
            }
            List<Order> sorted = new ArrayList<>(orders);
            sorted.sort(Comparator.comparing(Order::receivedAt).reversed());
            return sorted.subList(0, Math.min(limit, sorted.size()));
        }
    }

    private final MemoryStore store = new MemoryStore();
    private final Handler handler = new Handler(store, "test-release");

    private static APIGatewayV2HTTPEvent request(String method, String path, String body, Map<String, String> query) {
        return APIGatewayV2HTTPEvent.builder()
                .withRawPath(path)
                .withBody(body)
                .withQueryStringParameters(query)
                .withRequestContext(APIGatewayV2HTTPEvent.RequestContext.builder()
                        .withHttp(APIGatewayV2HTTPEvent.RequestContext.Http.builder().withMethod(method).build())
                        .build())
                .build();
    }

    private APIGatewayV2HTTPResponse call(String method, String path, String body, Map<String, String> query) {
        return handler.handleRequest(request(method, path, body, query), null);
    }

    private static JsonNode body(APIGatewayV2HTTPResponse res) throws Exception {
        return Handler.JSON.readTree(res.getBody());
    }

    @Test
    void healthzReportsTheRelease() throws Exception {
        APIGatewayV2HTTPResponse res = call("GET", "/api/healthz", null, null);
        assertEquals(200, res.getStatusCode());
        assertEquals("ok", body(res).get("status").asText());
        assertEquals("test-release", body(res).get("release").asText());
    }

    @Test
    void placeOrderSetsTheIdTotalAndTime() throws Exception {
        APIGatewayV2HTTPResponse res = call("POST", "/api/orders",
                "{\"order_id\":\"mine\",\"customer\":\"ada\",\"total\":1,\"items\":[{\"sku\":\"A\",\"qty\":2,\"price\":4.5}]}",
                null);
        assertEquals(201, res.getStatusCode(), res.getBody());
        JsonNode saved = body(res);
        assertNotEquals("mine", saved.get("order_id").asText());
        assertEquals(9.0, saved.get("total").asDouble());
        Instant.parse(saved.get("received_at").asText());
        assertEquals(1, store.orders.size());
    }

    @Test
    void placeOrderRefusesAnInvalidOrderWithTheReason() throws Exception {
        APIGatewayV2HTTPResponse res = call("POST", "/api/orders", "{\"customer\":\"ada\",\"items\":[]}", null);
        assertEquals(400, res.getStatusCode());
        assertEquals("an order needs at least one item", body(res).get("error").asText());
        assertTrue(store.orders.isEmpty());
    }

    @Test
    void placeOrderRefusesABodyThatIsNotJson() throws Exception {
        APIGatewayV2HTTPResponse res = call("POST", "/api/orders", "{not json", null);
        assertEquals(400, res.getStatusCode());
        assertTrue(body(res).get("error").asText().startsWith("the request body is not valid JSON"));
    }

    @Test
    void placeOrderAnswers500WhenTheTableRefusesTheWrite() throws Exception {
        store.broken = true;
        APIGatewayV2HTTPResponse res = call("POST", "/api/orders",
                "{\"customer\":\"ada\",\"items\":[{\"sku\":\"A\",\"qty\":1,\"price\":1}]}", null);
        assertEquals(500, res.getStatusCode());
        assertEquals("the order could not be saved", body(res).get("error").asText());
    }

    @Test
    void listOrdersReturnsTheNewestFirstWithinTheLimit() throws Exception {
        for (int i = 0; i < 3; i++) {
            store.orders.add(new Order("o" + i, "ada", List.of(new Item("A", 1, 1)), 1,
                    Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i)));
        }
        APIGatewayV2HTTPResponse res = call("GET", "/api/orders", null, Map.of("limit", "2"));
        assertEquals(200, res.getStatusCode());
        JsonNode orders = body(res).get("orders");
        assertEquals(2, orders.size());
        assertEquals("o2", orders.get(0).get("order_id").asText());
    }

    @Test
    void listOrdersAnswersAnEmptyListNotNull() throws Exception {
        JsonNode orders = body(call("GET", "/api/orders", null, null)).get("orders");
        assertTrue(orders.isArray());
        assertEquals(0, orders.size());
    }

    @Test
    void listOrdersRefusesALimitThatIsNotAPositiveNumber() throws Exception {
        for (String bad : List.of("0", "-3", "ten")) {
            APIGatewayV2HTTPResponse res = call("GET", "/api/orders", null, Map.of("limit", bad));
            assertEquals(400, res.getStatusCode(), bad);
            assertEquals("limit must be a positive whole number, got \"" + bad + "\"", body(res).get("error").asText());
        }
    }

    @Test
    void unknownPathsAndMethodsAreRefused() throws Exception {
        assertEquals(404, call("GET", "/api/nope", null, null).getStatusCode());
        APIGatewayV2HTTPResponse res = call("DELETE", "/api/orders", null, null);
        assertEquals(405, res.getStatusCode());
        assertEquals("DELETE is not allowed on /api/orders", body(res).get("error").asText());
    }
}
