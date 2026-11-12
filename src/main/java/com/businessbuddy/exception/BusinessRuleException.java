package com.businessbuddy.exception;

/**
 * A request broke a business rule (for example "Not enough flour in stock").
 * The message is written for the user and is shown on the page.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
