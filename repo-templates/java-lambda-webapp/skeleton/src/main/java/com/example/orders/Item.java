package com.example.orders;

/** One line of an order. */
public record Item(String sku, int qty, double price) {}
