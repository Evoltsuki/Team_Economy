package com.evolt.teamecon.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Strict scalar reads: a typo must not silently change a price or prize probability. */
public final class ConfigJson {
    private ConfigJson() {}

    public static long integer(JsonObject object, String key, long fallback, long min, long max) {
        if (!object.has(key)) return fallback;
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException(key + " must be an integer");
        long number = value.getAsBigDecimal().longValueExact();
        if (number < min || number > max) throw new IllegalArgumentException(key + " is out of range");
        return number;
    }

    public static boolean bool(JsonObject object, String key, boolean fallback) {
        if (!object.has(key)) return fallback;
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException(key + " must be true or false");
        return value.getAsBoolean();
    }

    public static String text(JsonObject object, String key, String fallback) {
        if (!object.has(key)) return fallback;
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException(key + " must be a string");
        return value.getAsString();
    }
}
