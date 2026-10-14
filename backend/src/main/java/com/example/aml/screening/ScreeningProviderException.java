package com.example.aml.screening;

import java.util.UUID;

public class ScreeningProviderException extends RuntimeException {
    private final UUID requestId;

    public ScreeningProviderException(String message) { this(null, message, null); }
    public ScreeningProviderException(String message, Throwable cause) { this(null, message, cause); }
    public ScreeningProviderException(UUID requestId, String message, Throwable cause) {
        super(message, cause);
        this.requestId = requestId;
    }
    public UUID requestId() { return requestId; }
}
