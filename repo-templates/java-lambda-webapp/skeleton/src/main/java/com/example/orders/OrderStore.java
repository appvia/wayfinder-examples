package com.example.orders;

import java.util.List;

/** What the API needs of a database. DynamoOrderStore is the real one; the tests use their own. */
public interface OrderStore {
    /** Writes an order, replacing any order with the same id. */
    void put(Order order);

    /** Returns up to limit orders, most recently received first. */
    List<Order> list(int limit);
}
