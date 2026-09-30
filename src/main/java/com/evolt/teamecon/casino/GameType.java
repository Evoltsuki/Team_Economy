package com.evolt.teamecon.casino;

/**
 * The mini-games a machine can host. Each machine block is bound to one of these; the
 * wireless terminal offers all of them.
 */
public enum GameType {

    SLOTS("slots"),
    MULTIPLIER("multiplier"),
    SCRATCH("scratch"),
    HILO("hilo"),
    ROULETTE("roulette"),
    PENGUIN("penguin"),
    COLOR_WHEEL("color_wheel");

    private final String id;

    GameType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static GameType byId(String id) {
        for (GameType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }
}
