package com.evolt.teamecon.gambling;

import com.evolt.teamecon.economy.MoneyMath;
import com.evolt.teamecon.economy.TeamEconomyManager;
import com.evolt.teamecon.scratch.ScratchKind;
import com.evolt.teamecon.shop.PurchaseRules;
import com.evolt.teamecon.team.TeamUtil;
import com.evolt.teamecon.util.ModLogger;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Server-owned progression and machine limits. Wallet levels live in SavedData, not in items. */
public final class CasinoProgression {
    public static final String FILE_NAME = "teamecon_casino_levels.json";
    public static final int MAX_LEVEL = 5;
    public static final List<String> GAMES = List.of("hilo", "penguin", "color_wheel", "roulette", "slots", "multiplier");
    public record Profile(int requiredLevel, long maxBet, double maxRunMultiplier, long buyPrice,
                          double chanceScale, double payoutScale) {}
    public record Terminal(int requiredLevel, long price, String advancement) {}
    private long[] levelCosts = {0, 1200, 10000, 50000, 200000};
    private final Map<String, Profile> games = new LinkedHashMap<>();
    private final Map<ScratchKind, Integer> cards = new EnumMap<>(ScratchKind.class);
    private Terminal terminal = new Terminal(5, 100000, "minecraft:end/kill_dragon");
    private boolean valid = true;

    public CasinoProgression() {
        games.put("hilo", new Profile(1, 2000, 2, 200, 1, .90));
        games.put("penguin", new Profile(2, 10000, 40, 1500, 1, 1));
        games.put("color_wheel", new Profile(3, 50000, 10, 7500, 1, 1));
        games.put("roulette", new Profile(4, 100000, 32, 30000, 1, 1));
        games.put("slots", new Profile(5, 200000, 275, 75000, 1, 1));
        games.put("multiplier", new Profile(5, 200000, 1000, 100000, .83, 1));
        cards.put(ScratchKind.MATCH, 1); cards.put(ScratchKind.DICE, 1);
        cards.put(ScratchKind.FRUIT, 2); cards.put(ScratchKind.SEVENS, 2); cards.put(ScratchKind.GEMS, 3);
        cards.put(ScratchKind.BINGO, 4); cards.put(ScratchKind.VAULT, 4); cards.put(ScratchKind.CROWN, 5);
    }

    public boolean valid() { return valid; }
    public Profile profile(String game) { return games.get(game); }
    public Terminal terminal() { return terminal; }
    public int cardLevel(ScratchKind kind) { return cards.getOrDefault(kind, MAX_LEVEL); }
    /** Higher wallet levels keep earlier machines useful without improving their expected return. */
    public long scaledMaxBet(String game, int level) {
        Profile p = profile(game); if (p == null) return 0;
        int factor = new int[]{1,2,5,10,25}[Math.clamp(level-p.requiredLevel(),0,4)];
        return MoneyMath.payout(p.maxBet(), factor);
    }
    public static long cardStakeLimit(int level) { return new long[]{100,1000,10000,100000,250000}[Math.clamp(level,1,5)-1]; }
    public long levelCost(int nextLevel) { return nextLevel >= 2 && nextLevel <= MAX_LEVEL ? levelCosts[nextLevel - 1] : 0; }
    public static String machineItem(String game) {
        return "teamecon:" + switch (game) {
            case "hilo" -> "hilo_table";
            case "penguin" -> "penguin_machine";
            case "color_wheel" -> "color_wheel_table";
            case "roulette" -> "roulette_table";
            case "slots" -> "slot_machine";
            case "multiplier" -> "multiplier_machine";
            default -> "";
        };
    }
    public static String gameForItem(String item) {
        return GAMES.stream().filter(game -> machineItem(game).equals(item)).findFirst().orElse("");
    }
    /** A negative value means the ordinary recipe/rarity price applies. */
    public long itemPrice(String item) {
        if (item.equals("teamecon:terminal")) return terminal.price();
        Profile p = profile(gameForItem(item));
        return p == null ? -1 : p.buyPrice();
    }
    public PurchaseRules.Access levelAccess(TeamEconomyManager manager, UUID wallet, int required) {
        if (!valid) return PurchaseRules.Access.locked("casino_config");
        return manager.casinoLevel(wallet) >= required ? PurchaseRules.Access.OPEN
                : PurchaseRules.Access.locked("casino_level:" + required);
    }
    public PurchaseRules.Access gameAccess(TeamEconomyManager manager, UUID wallet, String game) {
        Profile p = profile(game);
        return p == null ? PurchaseRules.Access.locked("casino_config") : levelAccess(manager, wallet, p.requiredLevel());
    }
    public PurchaseRules.Access terminalAccess(ServerPlayer player, TeamEconomyManager manager) {
        var access = levelAccess(manager, TeamUtil.walletKey(player.getServer(), player.getUUID()), terminal.requiredLevel());
        if (!access.unlocked()) return access;
        if (!terminal.advancement().isEmpty() && !manager.bypassesProgression(player.getUUID())) {
            var advancement = player.getServer().getAdvancements().get(ResourceLocation.parse(terminal.advancement()));
            if (advancement == null || !player.getAdvancements().getOrStartProgress(advancement).isDone())
                return PurchaseRules.Access.locked("advancement:" + terminal.advancement());
        }
        return PurchaseRules.Access.OPEN;
    }
    public PurchaseRules.Access itemAccess(ServerPlayer player, TeamEconomyManager manager, String item) {
        if (item.equals("teamecon:terminal")) return terminalAccess(player, manager);
        String game = gameForItem(item);
        return game.isEmpty() ? PurchaseRules.Access.OPEN
                : gameAccess(manager, TeamUtil.walletKey(player.getServer(), player.getUUID()), game);
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject(); root.addProperty("version", 4);
        JsonArray costs = new JsonArray(); for (long cost : levelCosts) costs.add(cost); root.add("levelCosts", costs);
        JsonObject profiles = new JsonObject();
        games.forEach((id, p) -> {
            JsonObject value = new JsonObject();
            value.addProperty("requiredLevel", p.requiredLevel()); value.addProperty("maxBet", p.maxBet());
            value.addProperty("maxRunMultiplier", p.maxRunMultiplier()); value.addProperty("buyPrice", p.buyPrice());
            value.addProperty("chanceScale", p.chanceScale()); value.addProperty("payoutScale", p.payoutScale());
            profiles.add(id, value);
        });
        root.add("games", profiles);
        JsonObject t = new JsonObject(); t.addProperty("requiredLevel", terminal.requiredLevel());
        t.addProperty("price", terminal.price()); t.addProperty("advancement", terminal.advancement()); root.add("terminal", t);
        JsonObject c = new JsonObject(); cards.forEach((kind, level) -> c.addProperty(kind.id(), level)); root.add("cards", c);
        return root;
    }
    public static CasinoProgression fromJson(JsonObject root) {
        int version = root.get("version").getAsInt();
        if (version < 1 || version > 4) throw new IllegalArgumentException("Expected casino levels version 1 through 4");
        CasinoProgression out = new CasinoProgression();
        JsonArray costs = root.getAsJsonArray("levelCosts");
        if (costs.size() != MAX_LEVEL || costs.get(0).getAsLong() != 0) throw new IllegalArgumentException("Expected five level costs, starting with zero");
        for (int i = 1; i < MAX_LEVEL; i++) out.levelCosts[i] = money(costs.get(i));
        if (version < 3 && Arrays.equals(out.levelCosts, new long[]{0,2000,10000,50000,200000})) out.levelCosts[1] = 1200;
        JsonObject profiles = root.getAsJsonObject("games");
        for (String id : GAMES) {
            JsonObject p = profiles.getAsJsonObject(id);
            Profile defaults = out.games.get(id);
            Profile loaded = new Profile(level(p.get("requiredLevel")), money(p.get("maxBet")),
                    range(p.get("maxRunMultiplier"), 1, 1000000), money(p.get("buyPrice")),
                    range(p.get("chanceScale"), 0, 1), range(p.get("payoutScale"), 0, 1));
            long oldCap = switch (id) { case "hilo" -> 20; case "penguin" -> 100; case "color_wheel" -> 500; case "roulette" -> 1000; default -> 2000; };
            double oldMax = id.equals("penguin") ? 5 : id.equals("roulette") ? 36 : defaults.maxRunMultiplier();
            Profile old = new Profile(defaults.requiredLevel(), oldCap, oldMax, defaults.buyPrice(), defaults.chanceScale(), id.equals("roulette") ? .9 : id.equals("hilo") ? .98 : defaults.payoutScale());
            boolean migrate = version == 1 && loaded.equals(old)
                    || version == 2 && id.equals("roulette") && loaded.equals(new Profile(4,100000,36,30000,1,.9));
            if (version < 4 && id.equals("hilo") && loaded.payoutScale() == .98)
                loaded = new Profile(loaded.requiredLevel(), loaded.maxBet(), loaded.maxRunMultiplier(),
                        loaded.buyPrice(), loaded.chanceScale(), .90);
            out.games.put(id, migrate ? defaults : loaded);
        }
        JsonObject t = root.getAsJsonObject("terminal");
        String advancement = t.get("advancement").getAsString();
        if (!advancement.isEmpty() && (advancement.length() > 256 || ResourceLocation.tryParse(advancement) == null))
            throw new IllegalArgumentException("Invalid terminal advancement");
        out.terminal = new Terminal(level(t.get("requiredLevel")), money(t.get("price")), advancement);
        JsonObject c = root.getAsJsonObject("cards");
        for (ScratchKind kind : ScratchKind.values()) out.cards.put(kind, level(c.get(kind.id())));
        return out;
    }
    private static int level(JsonElement value) { return (int) range(value, 1, MAX_LEVEL); }
    private static long money(JsonElement value) { return (long) range(value, 1, MoneyMath.MAX_MONEY); }
    private static double range(JsonElement value, double low, double high) {
        double n = value.getAsDouble();
        if (!Double.isFinite(n) || n < low || n > high || (low == 1 && n != Math.floor(n)))
            throw new IllegalArgumentException("Casino value out of range: " + value);
        return n;
    }
    public void load(Path directory) {
        Path file = directory.resolve(FILE_NAME);
        if (!Files.exists(file)) {
            try {
                Files.createDirectories(directory);
                Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(toJson()) + "\n");
            } catch (IOException e) { ModLogger.error("Could not write casino level defaults", e); }
            return;
        }
        try (var reader = Files.newBufferedReader(file)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            CasinoProgression loaded = fromJson(json);
            levelCosts = loaded.levelCosts; terminal = loaded.terminal;
            games.clear(); games.putAll(loaded.games); cards.clear(); cards.putAll(loaded.cards); valid = true;
            if (json.get("version").getAsInt() < 4) {
                Path backup = directory.resolve(FILE_NAME + ".v" + json.get("version").getAsInt() + ".bak");
                if (!Files.exists(backup)) Files.copy(file, backup);
                Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(toJson()) + "\n");
                ModLogger.info("Migrated default progression to version 4; custom profiles preserved");
            }
        } catch (IOException | RuntimeException e) {
            valid = false;
            ModLogger.error("Invalid casino level config; new games and progression purchases locked until reload", e);
        }
    }
}
