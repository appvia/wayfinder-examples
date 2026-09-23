package com.example.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

class DynamoOrderStoreTest {

    private static final Order ORDER = new Order(
            "order-1", "ada", List.of(new Item("SKU-1", 2, 1.5)), 3, Instant.parse("2026-01-02T03:04:05.123456Z"));

    // The table's hash key is order_id. An item written under any other name is
    // refused by DynamoDB, and nothing else in the suite would notice.
    @Test
    void writesTheAttributeNamesTheTableExpects() {
        Map<String, AttributeValue> item = DynamoOrderStore.toItem(ORDER);
        for (String want : List.of("order_id", "customer", "items", "total", "received_at")) {
            assertTrue(item.containsKey(want), "no " + want + " attribute; got " + item.keySet());
        }
    }

    @Test
    void readsBackWhatItWrote() {
        assertEquals(ORDER, DynamoOrderStore.fromItem(DynamoOrderStore.toItem(ORDER)));
    }
}
