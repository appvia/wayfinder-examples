package com.example.orders;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

/**
 * Keeps orders in the table the stack's `db` component creates. It holds no
 * credentials: the function's own workload identity carries the read-write
 * grant Wayfinder.yaml states, and the SDK picks it up from the execution role.
 */
public final class DynamoOrderStore implements OrderStore {

    private final DynamoDbClient client;
    private final String table;

    public DynamoOrderStore(DynamoDbClient client, String table) {
        this.client = client;
        this.table = table;
    }

    @Override
    public void put(Order order) {
        client.putItem(r -> r.tableName(table).item(toItem(order)));
    }

    /**
     * Scans the table and sorts in the process. The table is keyed by order_id
     * alone, so there is no index to read "most recent first" from, and a scan
     * is honest at demo volumes. Past a few thousand orders, add a global
     * secondary index keyed on a coarse time bucket and query that instead.
     */
    @Override
    public List<Order> list(int limit) {
        List<Order> found = new ArrayList<>();
        Map<String, AttributeValue> startKey = null;
        do {
            ScanRequest.Builder request = ScanRequest.builder().tableName(table);
            if (startKey != null) {
                request.exclusiveStartKey(startKey);
            }
            ScanResponse page = client.scan(request.build());
            for (Map<String, AttributeValue> item : page.items()) {
                found.add(fromItem(item));
            }
            startKey = page.hasLastEvaluatedKey() ? page.lastEvaluatedKey() : null;
        } while (startKey != null);

        found.sort(Comparator.comparing(Order::receivedAt).reversed());
        return found.size() > limit ? found.subList(0, limit) : found;
    }

    /** The attribute names are the JSON ones; order_id is the table's hash key. */
    static Map<String, AttributeValue> toItem(Order o) {
        List<AttributeValue> items = new ArrayList<>();
        for (Item it : o.items()) {
            items.add(AttributeValue.fromM(Map.of(
                    "sku", AttributeValue.fromS(it.sku()),
                    "qty", AttributeValue.fromN(Integer.toString(it.qty())),
                    "price", AttributeValue.fromN(Double.toString(it.price())))));
        }
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("order_id", AttributeValue.fromS(o.orderId()));
        item.put("customer", AttributeValue.fromS(o.customer()));
        item.put("items", AttributeValue.fromL(items));
        item.put("total", AttributeValue.fromN(Double.toString(o.total())));
        item.put("received_at", AttributeValue.fromS(o.receivedAt().toString()));
        return item;
    }

    static Order fromItem(Map<String, AttributeValue> item) {
        List<Item> items = new ArrayList<>();
        for (AttributeValue v : item.get("items").l()) {
            Map<String, AttributeValue> m = v.m();
            items.add(new Item(
                    m.get("sku").s(),
                    Integer.parseInt(m.get("qty").n()),
                    Double.parseDouble(m.get("price").n())));
        }
        return new Order(
                item.get("order_id").s(),
                item.get("customer").s(),
                items,
                Double.parseDouble(item.get("total").n()),
                Instant.parse(item.get("received_at").s()));
    }
}
