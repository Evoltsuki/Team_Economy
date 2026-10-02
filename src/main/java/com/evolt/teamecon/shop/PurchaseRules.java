package com.evolt.teamecon.shop;

import com.evolt.teamecon.config.TeConfig;
import com.evolt.teamecon.economy.TeamEconomyManager;
import com.evolt.teamecon.util.ModLogger;
import com.google.gson.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** One server-side policy for item purchases, commands, books and every possible box reward. */
public final class PurchaseRules {
    public static final String FILE_NAME = "teamecon_progression.json";
    public record Access(boolean unlocked, String reason) {
        public static final Access OPEN = new Access(true, "");
        public static Access locked(String reason) { return new Access(false, reason); }
    }
    private record Rule(List<String> match, List<Pattern> patterns, String advancement, boolean sellOnly) {
        boolean matches(String id) { return patterns.stream().anyMatch(pattern -> pattern.matcher(id).matches()); }
    }

    private final MinecraftServer server;
    private final TeamEconomyManager manager;
    private List<Rule> items = new ArrayList<>(), enchantments = new ArrayList<>();
    private boolean invalidConfig;

    public PurchaseRules(MinecraftServer server) {
        this(server, TeamEconomyManager.get(server));
    }

    public PurchaseRules(MinecraftServer server, TeamEconomyManager manager) {
        this.server = server;
        this.manager = manager;
        defaults();
    }

    public Access item(ServerPlayer player, String id) {
        return com.evolt.teamecon.price.TradePolicy.canTrade(id) ? check(player, id, items) : Access.locked("mod_disabled");
    }
    public Access item(ServerPlayer player, String id, ShopCatalog catalog) {
        if (!catalog.valid()) return Access.locked("config");
        if (!catalog.allows(id)) return Access.locked("catalog");
        String stage = catalog.stage(id);
        if (!stage.isEmpty() && TeConfig.SHOP.useTeamStages.get()
                && !com.evolt.teamecon.team.TeamUtil.hasStage(server, player.getUUID(), stage))
            return Access.locked("stage:" + stage);
        return check(player, id, items, catalog.custom(id));
    }
    public Access enchantment(ServerPlayer player, String id) {
        return id != null && id.startsWith("minecraft:") ? check(player, id, enchantments) : Access.locked("mod_disabled");
    }

    private Access check(ServerPlayer player, String id, List<Rule> rules) {
        return check(player, id, rules, false);
    }
    private Access check(ServerPlayer player, String id, List<Rule> rules, boolean explicitOffer) {
        if (!TeConfig.SHOP.progressionEnabled.get()) return Access.OPEN;
        if (invalidConfig) return Access.locked("config");
        boolean matched = false;
        String missing = "";
        for (Rule rule : rules) {
            if (!rule.matches(id)) continue;
            matched = true;
            // A restrictive rule cannot be undone by a later broad allow rule.
            if (rule.sellOnly()) return Access.locked("sell_only");
            if (!rule.advancement().isEmpty() && !manager.bypassesProgression(player.getUUID())
                    && !hasAdvancement(player, rule.advancement()) && missing.isEmpty())
                missing = rule.advancement();
        }
        if (!missing.isEmpty()) return Access.locked("advancement:" + missing);
        if (!matched && !explicitOffer && !id.startsWith("minecraft:") && !TeConfig.SHOP.allowUnruledModdedPurchases.get())
            return Access.locked("modded");
        return Access.OPEN;
    }

    private boolean hasAdvancement(ServerPlayer player, String id) {
        var advancement = server.getAdvancements().get(ResourceLocation.parse(id));
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    /** Reason tokens contain no wire-format separators and localize on the receiving client. */
    public static Component describe(String reason) {
        if (reason.startsWith("casino_level:")) return Component.translatable("casino.teamecon.requires_level", reason.substring(13));
        if (reason.startsWith("advancement:")) {
            String id = reason.substring("advancement:".length());
            Component title = id.startsWith("minecraft:")
                    ? Component.translatable("advancements." + id.substring(10).replace('/', '.') + ".title")
                    : Component.literal(id);
            return Component.translatable("shop.teamecon.requires_advancement", title);
        }
        if (reason.startsWith("stage:")) return Component.translatable("shop.teamecon.requires_stage", reason.substring(6));
        return Component.translatable("shop.teamecon.lock." + reason);
    }

    public void load(Path configDir) {
        Path file = configDir.resolve(FILE_NAME);
        invalidConfig = false;
        if (!Files.exists(file)) {
            defaults();
            try {
                Files.createDirectories(configDir);
                JsonObject root = new JsonObject();
                root.addProperty("version", 1);
                root.add("items", write(items));
                root.add("enchantments", write(enchantments));
                Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n");
            } catch (IOException ex) { ModLogger.error("Could not write purchase progression defaults", ex); }
            return;
        }
        try (var reader = Files.newBufferedReader(file)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("version") || root.get("version").getAsInt() != 1)
                throw new IllegalArgumentException("Expected progression version 1");
            List<Rule> loadedItems = read(root.getAsJsonArray("items"));
            List<Rule> loadedEnchantments = read(root.getAsJsonArray("enchantments"));
            items = loadedItems;
            enchantments = loadedEnchantments;
        } catch (IOException | RuntimeException ex) {
            invalidConfig = true;
            ModLogger.error("Invalid purchase progression file; purchases locked until repaired and reloaded", ex);
        }
    }

    private static List<Rule> read(JsonArray array) {
        if (array == null || array.size() > 256) throw new IllegalArgumentException("Missing or excessive progression rules");
        List<Rule> out = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject obj = element.getAsJsonObject();
            JsonArray matches = obj.getAsJsonArray("match");
            if (matches == null || matches.isEmpty() || matches.size() > 128)
                throw new IllegalArgumentException("Each rule needs 1 to 128 item patterns");
            List<String> ids = new ArrayList<>();
            for (JsonElement match : matches) ids.add(match.getAsString());
            String advancement = obj.has("advancement") ? obj.get("advancement").getAsString() : "";
            boolean sellOnly = obj.has("sellOnly") && obj.get("sellOnly").getAsBoolean();
            out.add(rule(ids, advancement, sellOnly));
        }
        return out;
    }

    private static Rule rule(List<String> matches, String advancement, boolean sellOnly) {
        if (!advancement.isEmpty() && (advancement.length() > 256 || ResourceLocation.tryParse(advancement) == null))
            throw new IllegalArgumentException("Invalid advancement id: " + advancement);
        List<Pattern> patterns = new ArrayList<>();
        for (String match : matches) {
            if (match.length() > 256 || !match.matches("[a-z0-9_.-]+:[a-z0-9_./*\\-]+"))
                throw new IllegalArgumentException("Invalid item pattern: " + match);
            patterns.add(Pattern.compile(Pattern.quote(match).replace("*", "\\E.*\\Q")));
        }
        return new Rule(List.copyOf(matches), List.copyOf(patterns), advancement, sellOnly);
    }

    private static JsonArray write(List<Rule> rules) {
        JsonArray out = new JsonArray();
        for (Rule rule : rules) {
            JsonObject obj = new JsonObject();
            JsonArray matches = new JsonArray();
            rule.match().forEach(matches::add);
            obj.add("match", matches);
            if (rule.sellOnly()) obj.addProperty("sellOnly", true);
            else obj.addProperty("advancement", rule.advancement());
            out.add(obj);
        }
        return out;
    }

    private void gate(String advancement, String... paths) {
        items.add(rule(java.util.Arrays.stream(paths).map(p -> p.contains(":") ? p : "minecraft:" + p).toList(), advancement, false));
    }
    private void sellOnly(String... paths) {
        items.add(rule(java.util.Arrays.stream(paths).map(p -> "minecraft:" + p).toList(), "", true));
    }

    private void defaults() {
        items = new ArrayList<>(); enchantments = new ArrayList<>();
        sellOnly("nether_star", "beacon", "elytra", "dragon_egg", "dragon_head", "dragon_breath",
                "totem_of_undying", "heavy_core", "mace", "trial_key", "ominous_trial_key", "breeze_rod",
                "wither_skeleton_skull", "heart_of_the_sea", "conduit", "echo_shard", "recovery_compass",
                "enchanted_golden_apple", "trident", "*_smithing_template",
                "netherite_helmet", "netherite_chestplate", "netherite_leggings", "netherite_boots",
                "netherite_sword", "netherite_pickaxe", "netherite_axe", "netherite_shovel", "netherite_hoe");
        gate("minecraft:story/smelt_iron", "iron_*", "raw_iron*", "deepslate_iron_ore", "bucket", "*_bucket", "shield",
                "anvil", "chipped_anvil", "damaged_anvil", "hopper", "hopper_minecart", "blast_furnace", "minecart",
                "*_minecart", "rail", "*_rail", "compass", "shears", "cauldron", "chain", "lantern", "tripwire_hook");
        gate("minecraft:story/iron_tools", "gold_*", "golden_*", "raw_gold*", "deepslate_gold_ore", "redstone*",
                "deepslate_redstone_ore", "lapis_*", "deepslate_lapis_ore", "emerald*", "deepslate_emerald_ore",
                "repeater", "comparator", "piston", "sticky_piston", "observer", "dispenser", "dropper", "crafter", "teamecon:*");
        gate("minecraft:story/mine_diamond", "diamond", "diamond_*", "deepslate_diamond_ore", "enchanting_table", "jukebox");
        gate("minecraft:story/form_obsidian", "obsidian", "crying_obsidian");
        gate("minecraft:nether/obtain_ancient_debris", "ancient_debris", "netherite_*");
        gate("minecraft:story/enter_the_nether", "netherrack", "nether_*", "red_nether_*", "quartz*", "smooth_quartz*",
                "chiseled_quartz*", "blackstone*", "polished_blackstone*", "gilded_blackstone", "basalt", "smooth_basalt",
                "polished_basalt", "glowstone*", "shroomlight", "magma*", "soul_*", "crimson_*", "warped_*",
                "weeping_vines", "twisting_vines", "ghast_tear", "ender_pearl", "respawn_anchor", "lodestone");
        gate("minecraft:nether/obtain_blaze_rod", "blaze_*", "ender_eye", "ender_chest", "brewing_stand", "end_crystal");
        gate("minecraft:story/enter_the_end", "end_stone*", "end_rod", "purpur_*", "chorus_*", "popped_chorus_fruit");
        gate("minecraft:end/find_end_city", "shulker_shell", "shulker_box", "*_shulker_box");
        enchantments.add(rule(List.of("minecraft:*"), "minecraft:story/enchant_item", false));
        enchantments.add(rule(List.of("minecraft:mending"), "minecraft:adventure/trade", false));
        enchantments.add(rule(List.of("minecraft:soul_speed"), "minecraft:nether/loot_bastion", false));
        enchantments.add(rule(List.of("minecraft:swift_sneak", "minecraft:wind_burst"), "", true));
    }
}
