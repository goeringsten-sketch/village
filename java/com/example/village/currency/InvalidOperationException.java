package com.example.village.currency;

/**
 * Exception für ungültige Operationen
 */
public class InvalidOperationException extends Exception {
    public InvalidOperationException(String message) {
        super(message);
    }
    
    public InvalidOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
