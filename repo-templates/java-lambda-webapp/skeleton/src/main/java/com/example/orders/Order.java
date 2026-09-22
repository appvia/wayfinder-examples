package com.example.orders;

import java.time.Instant;
import java.util.List;

/**
 * One customer order, and the rules it must satisfy. Nothing here talks to AWS,
 * which is what lets OrderTest run without a client.
 */
public record Order(String orderId, String customer, List<Item> items, double total, Instant receivedAt) {

    /**
     * Returns the first thing wrong with the order, in the words the caller sees
     * in the 400 body, or null when there is nothing wrong.
     */
    public String problem() {
        if (customer == null || customer.isEmpty()) {
            return "customer is required";
        }
        if (items == null || items.isEmpty()) {
            return "an order needs at least one item";
        }
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (it == null || it.sku() == null || it.sku().isEmpty()) {
                return "items[" + i + "]: sku is required";
            }
            if (it.qty() < 1) {
                return "items[" + i + "]: qty must be at least 1, got " + it.qty();
            }
            if (it.price() < 0) {
                return "items[" + i + "]: price cannot be negative, got " + it.price();
            }
        }
        return null;
    }

    /**
     * The order as it is saved: the id, the timestamp and the total are the
     * API's to set, so a client that sends its own gets them replaced.
     */
    public Order accepted(String id, Instant at) {
        double sum = 0;
        for (Item it : items) {
            sum += it.price() * it.qty();
        }
        return new Order(id, customer, items, sum, at);
    }
}
