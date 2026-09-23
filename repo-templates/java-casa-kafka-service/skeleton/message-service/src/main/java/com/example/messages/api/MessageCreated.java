package com.example.messages.api;

import com.example.messages.store.MessageStatus;

public record MessageCreated(String reference, MessageStatus status) {}
