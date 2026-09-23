package com.example.messages.store;

import java.security.SecureRandom;

/** Makes message references such as {@code MSG-7K2M9QX4}. */
public final class References {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private References() {}

    public static String next() {
        StringBuilder reference = new StringBuilder("MSG-");
        for (int i = 0; i < 8; i++) {
            reference.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return reference.toString();
    }
}
