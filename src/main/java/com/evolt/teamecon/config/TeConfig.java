package com.evolt.teamecon.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * All economy parameters are configurable here. Default values are balanced so that
 * gambling is a low-stakes side activity and normal progression stays the main income.
 */
public class TeConfig {

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final Economy ECONOMY;
    public static final Market MARKET;
    public static final Pricing PRICING;
    public static final Shop SHOP;
    public static final Gambling GAMBLING;
    public static final Quests QUESTS;

    static {
        BUILDER.push("economy");
        ECONOMY = new Economy();
        BUILDER.pop();

        BUILDER.push("market");
        MARKET = new Market();
        BUILDER.pop();

        BUILDER.push("pricing");
        PRICING = new Pricing();
        BUILDER.pop();

        BUILDER.push("shop");
        SHOP = new Shop();
        BUILDER.pop();

        BUILDER.push("gambling");
        GAMBLING = new Gambling();
        BUILDER.pop();


        BUILDER.push("quests");
        QUESTS = new Quests();
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private TeConfig() {
    }

    /** Core economy behaviour. */
    public static final class Economy {
        public final ModConfigSpec.ConfigValue<Long> startingBalance;
        public final ModConfigSpec.ConfigValue<Long> maxBalance;
        public final ModConfigSpec.ConfigValue<Boolean> demandScopeIsTeam;
        public final ModConfigSpec.ConfigValue<Integer> transactionLogLimit;

        Economy() {
            startingBalance = BUILDER.comment("Balance given to a team the first time it is seen. 0 disables it.")
                    .defineInRange("startingBalance", 200L, 0L, 1_000_000_000L);
            maxBalance = BUILDER.comment("Hard cap on team balance. 0 = no cap.")
                    .defineInRange("maxBalance", 0L, 0L, com.evolt.teamecon.economy.MoneyMath.MAX_MONEY);
            demandScopeIsTeam = BUILDER.comment("true = market demand/dip is tracked per team, false = shared across the whole server.")
                    .define("demandScopeIsTeam", true);
            transactionLogLimit = BUILDER.comment("How many recent transactions are kept per team (0 = keep all).")
                    .defineInRange("transactionLogLimit", 200, 0, 100000);
        }
    }

    /** Demand decay and recovery for sold items. */
    public static final class Market {
        public final ModConfigSpec.ConfigValue<Double> decayPerUnit;
        public final ModConfigSpec.ConfigValue<Double> minPriceFactor;
        public final ModConfigSpec.ConfigValue<Integer> recoverySecondsPerUnit;
        public final ModConfigSpec.ConfigValue<Integer> maxDipUnits;

        Market() {
            decayPerUnit = BUILDER.comment("Price multiplier drop per cumulative unit sold, e.g. 0.01 = -1% per unit.")
                    .defineInRange("decayPerUnit", 0.01D, 0D, 1D);
            minPriceFactor = BUILDER.comment("Floor for the sell-price multiplier (0.05 = 5% of base value).")
                    .defineInRange("minPriceFactor", 0.05D, 0D, 1D);
            recoverySecondsPerUnit = BUILDER.comment("Real seconds needed for one unit of demand dip to recover.")
                    .defineInRange("recoverySecondsPerUnit", 30, 1, 86400);
            maxDipUnits = BUILDER.comment("Cap on the tracked demand dip, in units (0 = no cap).")
                    .defineInRange("maxDipUnits", 5000, 0, 1_000_000);
        }
    }

    /** Automatic valuation behaviour. */
    public static final class Pricing {
        public final ModConfigSpec.ConfigValue<Double> craftValuePerStep;
        public final ModConfigSpec.ConfigValue<Double> maxCraftMarkup;
        public final ModConfigSpec.ConfigValue<Boolean> logUnknownItems;
        public final ModConfigSpec.ConfigValue<Boolean> showPriceTooltips;
        public final ModConfigSpec.ConfigValue<Boolean> rarityFallbackEnabled;
        public final ModConfigSpec.ConfigValue<Long> rarityFallbackCommon;
        public final ModConfigSpec.ConfigValue<Long> rarityFallbackUncommon;
        public final ModConfigSpec.ConfigValue<Long> rarityFallbackRare;
        public final ModConfigSpec.ConfigValue<Long> rarityFallbackEpic;

        Pricing() {
            craftValuePerStep = BUILDER.comment("Value added per standard crafting step, as a fraction of ingredient value (0.1 = +10%).")
                    .defineInRange("craftValuePerStep", 0.1D, 0D, 10D);
            maxCraftMarkup = BUILDER.comment("Maximum total markup from crafting steps over ingredient value.")
                    .defineInRange("maxCraftMarkup", 2D, 0D, 100D);
            logUnknownItems = BUILDER.comment("Report items with no price and no recipe to the pending list.")
                    .define("logUnknownItems", true);
            showPriceTooltips = BUILDER.comment("Show the auto-valued sell price under every item in its tooltip (client needs the synced price table).")
                    .define("showPriceTooltips", true);
            rarityFallbackEnabled = BUILDER.comment("Give items that cannot be crafted (mob drops, loot, finds) a value based on their vanilla rarity, so they can still be sold.")
                    .define("rarityFallbackEnabled", true);
            rarityFallbackCommon = BUILDER.comment("Sell value of an uncraftable COMMON item (an apple, a piece of dirt).")
                    .defineInRange("rarityFallbackCommon", 1L, 0L, 1_000_000L);
            rarityFallbackUncommon = BUILDER.comment("Sell value of an uncraftable UNCOMMON item.")
                    .defineInRange("rarityFallbackUncommon", 8L, 0L, 1_000_000L);
            rarityFallbackRare = BUILDER.comment("Fallback for RARE items without a base price. Boss loot has explicit base prices.")
                    .defineInRange("rarityFallbackRare", 128L, 0L, 1_000_000L);
            rarityFallbackEpic = BUILDER.comment("Sell value of an uncraftable EPIC item (an enchanted golden apple).")
                    .defineInRange("rarityFallbackEpic", 1024L, 0L, 100_000_000L);
        }
    }

    /** Shop, services and mystery boxes. */
    public static final class Shop {
        public final ModConfigSpec.ConfigValue<Double> buyMarkup;
        public final ModConfigSpec.ConfigValue<Boolean> useTeamStages;
        public final ModConfigSpec.ConfigValue<Boolean> progressionEnabled;
        public final ModConfigSpec.ConfigValue<Boolean> allowUnruledModdedPurchases;

        Shop() {
            buyMarkup = BUILDER.comment("Players pay sell-value x this multiplier when buying from the shop.")
                    .defineInRange("buyMarkup", 2.0D, 2D, 100D);
            useTeamStages = BUILDER.comment("Gate shop offers and services behind FTB Teams stages (progression unlocks).")
                    .define("useTeamStages", true);
            progressionEnabled = BUILDER.comment("Enforce teamecon_progression.json for shops, buy commands and books. Blind boxes use independent prize pools without advancement locks.",
                            "Advancement unlocks are personal; sharing a wallet does not grant another player's progress.")
                    .define("progressionEnabled", true);
            allowUnruledModdedPurchases = BUILDER.comment("Legacy option, ignored since 0.4.1. All third-party mod item purchases and sales are disabled.",
                            "Team Economy's dedicated machine and ticket catalogues remain available.")
                    .define("allowUnruledModdedPurchases", false);
        }
    }

    /** Scratch cards and multiplier betting. */
    public static final class Gambling {
        public final ModConfigSpec.ConfigValue<Long> minBet;
        public final ModConfigSpec.ConfigValue<Long> maxBet;
        public final ModConfigSpec.ConfigValue<Double> expectedValueCap;
        public final ModConfigSpec.ConfigValue<Double> hiLoPayout;
        public final ModConfigSpec.ConfigValue<Double> maxMultiplier;

        Gambling() {
            minBet = BUILDER.comment("Smallest allowed bet, in balance units.")
                    .defineInRange("minBet", 1L, 1L, 1_000_000_000L);
            maxBet = BUILDER.comment("Largest allowed bet per single action, in balance units (caps streaks).")
                    .defineInRange("maxBet", 1000000L, 1L, 1_000_000_000L);
            expectedValueCap = BUILDER.comment("Highest allowed expected value multiplier for any gamble (<=1.0 means no money printer).")
                    .defineInRange("expectedValueCap", 1.0D, 0D, 1D);
            hiLoPayout = BUILDER.comment("Legacy setting. Since 0.3, high/low pays 2 * games.hilo.payoutScale in teamecon_casino_levels.json.")
                    .defineInRange("hiLoPayout", 1.9D, 1D, 2D);
            maxMultiplier = BUILDER.comment("Cash-out ceiling for multiplier and penguin runs; reaching it disables further rounds.")
                    .defineInRange("maxMultiplier", 1000D, 1D, 1_000_000D);
        }
    }


    /** Quests, achievements and repeat-claim protection. */
    public static final class Quests {
        public final ModConfigSpec.ConfigValue<Boolean> rewardEveryMember;
        public final ModConfigSpec.ConfigValue<Long> totalRewardCapPerTeam;

        Quests() {
            rewardEveryMember = BUILDER.comment("Give one reward copy to every team member instead of a single team reward.")
                    .define("rewardEveryMember", true);
            totalRewardCapPerTeam = BUILDER.comment("Maximum total quest reward value a team can earn (0 = no cap).")
                    .defineInRange("totalRewardCapPerTeam", 0L, 0L, 1_000_000_000L);
        }
    }
}
