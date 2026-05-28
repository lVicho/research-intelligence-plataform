package com.researchintelligence.platform.shared.application;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceName, Long id) {
        super(resourceName + " not found: " + id);
    }
}
