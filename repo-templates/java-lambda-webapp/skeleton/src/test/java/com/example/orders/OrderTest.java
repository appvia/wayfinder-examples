package com.example.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class OrderTest {

    private static Order order(String customer, List<Item> items) {
        return new Order(null, customer, items, 0, null);
    }

    @Test
    void validOrderHasNoProblem() {
        assertNull(order("ada", List.of(new Item("SKU-1", 1, 0))).problem());
    }

    @Test
    void reportsTheFirstProblemInTheCallersWords() {
        assertEquals("customer is required", order("", List.of(new Item("SKU-1", 1, 1))).problem());
        assertEquals("an order needs at least one item", order("ada", List.of()).problem());
        assertEquals("items[0]: sku is required", order("ada", List.of(new Item("", 1, 1))).problem());
        assertEquals("items[1]: qty must be at least 1, got 0",
                order("ada", List.of(new Item("A", 1, 1), new Item("B", 0, 1))).problem());
        assertEquals("items[0]: price cannot be negative, got -1.0",
                order("ada", List.of(new Item("A", 1, -1))).problem());
    }

    // A client that states its own total must not have it believed.
    @Test
    void acceptedOverwritesASubmittedTotal() {
        Order submitted = new Order("theirs", "ada", List.of(new Item("A", 2, 1.5), new Item("B", 1, 4)), 999, null);
        Instant at = Instant.parse("2026-01-02T03:04:05Z");
        Order saved = submitted.accepted("ours", at);
        assertEquals(7.0, saved.total());
        assertEquals("ours", saved.orderId());
        assertEquals(at, saved.receivedAt());
    }
}
