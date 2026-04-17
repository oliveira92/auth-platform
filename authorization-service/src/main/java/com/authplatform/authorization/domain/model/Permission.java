package com.authplatform.authorization.domain.model;

public record Permission(
    String id,
    String name,
    String description,
    String resource,
    String action
) {
    public String toScope() {
        return resource + ":" + action;
    }
}
