package com.aibridge.exception;

/**
 * Raised when no bridge configuration exists for the given tenant and feature.
 */
public class ConfigNotFoundException extends RuntimeException {

    public ConfigNotFoundException(String tenantId, String feature) {
        super("No configuration found for tenant='" + tenantId + "' and feature='" + feature + "'");
    }

    public ConfigNotFoundException(String tenantId, String feature, Throwable cause) {
        super("No configuration found for tenant='" + tenantId + "' and feature='" + feature + "'", cause);
    }
}
