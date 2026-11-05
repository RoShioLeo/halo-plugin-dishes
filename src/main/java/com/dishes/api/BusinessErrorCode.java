package com.dishes.api;

public enum BusinessErrorCode {
    NOT_FOUND("DISHES_NOT_FOUND"),
    CATEGORY_DELETE_CONFLICT("DISHES_CATEGORY_DELETE_CONFLICT"),
    ACCESS_DENIED("DISHES_ACCESS_DENIED"),
    PASSWORD_INVALID("DISHES_PASSWORD_INVALID"),
    BAD_REQUEST("DISHES_BAD_REQUEST"),
    INVALID_REQUEST("DISHES_INVALID_REQUEST"),
    CONFLICT("DISHES_CONFLICT"),
    CHEF_REQUIRED("DISHES_CHEF_REQUIRED");

    private final String value;

    BusinessErrorCode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
