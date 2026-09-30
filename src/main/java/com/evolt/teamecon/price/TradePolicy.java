package com.evolt.teamecon.price;

import java.util.Set;

/** Trading is limited to vanilla goods and the mod's own dedicated equipment catalogue. */
public final class TradePolicy {
    private static final Set<String> RESTRICTED = Set.of(
            "air", "cave_air", "void_air", "bedrock", "barrier", "light", "debug_stick",
            "command_block", "chain_command_block", "repeating_command_block", "command_block_minecart",
            "structure_block", "structure_void", "jigsaw", "spawner", "trial_spawner", "vault",
            "end_portal_frame", "knowledge_book", "reinforced_deepslate", "player_head", "bundle");

    private TradePolicy() {}

    private static final Set<String> EQUIPMENT = Set.of(
            "teamecon:shop_machine", "teamecon:blind_box_machine", "teamecon:slot_machine",
            "teamecon:roulette_table", "teamecon:color_wheel_table", "teamecon:penguin_machine",
            "teamecon:multiplier_machine", "teamecon:hilo_table", "teamecon:terminal", "teamecon:guide_book");

    public static boolean canSell(String id) {
        return id != null && id.startsWith("minecraft:") && canTrade(id);
    }

    public static boolean canTrade(String id) {
        if (id == null || !id.contains(":")) return false;
        // Blank registry items must never bypass the paid, server-serialized ticket catalogue.
        if (id.startsWith("teamecon:scratch_card_") || id.equals("teamecon:scratch_table")) return false;
        if (!id.startsWith("minecraft:")) return EQUIPMENT.contains(id);
        String path = id.substring(10);
        return !RESTRICTED.contains(path) && !path.endsWith("_spawn_egg");
    }
}
