package com.example.messages.api;

import java.util.Map;
import java.util.TreeMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns a rejected request into a 400 that names each field that is wrong. */
@RestControllerAdvice
public class ApiErrors {

    public record ValidationFailure(String message, Map<String, String> errors) {}

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ValidationFailure invalid(MethodArgumentNotValidException e) {
        Map<String, String> errors = new TreeMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return new ValidationFailure("The message has errors", errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ValidationFailure unreadable(HttpMessageNotReadableException e) {
        return new ValidationFailure("The request body is not JSON of the form {\"message\": \"...\"}", Map.of());
    }
}
