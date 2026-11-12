package com.businessbuddy.exception;

/**
 * A record does not exist <em>or belongs to another business</em>. Both cases give the
 * same 404 answer so identifiers of other businesses reveal nothing (4.6.2).
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String what) {
        super(what + " not found");
    }
}
