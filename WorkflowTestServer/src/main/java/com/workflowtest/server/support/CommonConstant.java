package com.workflowtest.server.support;

/**
 * 与业务无关的通用字面量（数值、空值、算法名等）。
 * 业务字段名、作用域前缀等请放在对应业务类中维护。
 */
public final class CommonConstant {
    private CommonConstant() {}

    public static final int ZERO = 0;
    public static final int ONE = 1;

    public static final String EMPTY = "";
    public static final String JSON_EMPTY_OBJECT = "{}";
    public static final String JSON_EMPTY_ARRAY = "[]";
    public static final String JSON_NULL = "null";

    public static final String UTF_8 = "UTF-8";
    public static final String SHA_256 = "SHA-256";
}
