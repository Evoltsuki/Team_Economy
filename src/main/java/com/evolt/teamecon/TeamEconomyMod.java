package com.evolt.teamecon;

import com.evolt.teamecon.command.ModCommands;
import com.evolt.teamecon.listener.StartingBalanceHandler;
import com.evolt.teamecon.config.TeConfig;
import com.evolt.teamecon.economy.EconomyService;
import com.evolt.teamecon.economy.TeamEconomyManager;
import com.evolt.teamecon.gambling.GamblingService;
import com.evolt.teamecon.init.ModRegistries;
import com.evolt.teamecon.network.ModNetwork;
import com.evolt.teamecon.price.PriceService;
import com.evolt.teamecon.price.RarityFallback;
import com.evolt.teamecon.price.RecipePricer;
import com.evolt.teamecon.shop.ShopMenu;
import com.evolt.teamecon.shop.ShopService;
import com.evolt.teamecon.util.ModLogger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(TeamEconomyMod.MOD_ID)
public class TeamEconomyMod {

    public static final String MOD_ID = "teamecon";
    public static final Logger LOGGER = LoggerFactory.getLogger("TeamEconomy");

    private static TeamEconomyMod instance;

    private PriceService prices;
    private EconomyService economy;
    private GamblingService gambling;
    private ShopService shop;
    private com.evolt.teamecon.scratch.ScratchCardService scratchCards;
    private com.evolt.teamecon.gambling.CasinoProgression casinoProgression;

    public TeamEconomyMod(IEventBus modEventBus, ModContainer modContainer) {
        instance = this;

        prices = new PriceService();
        RecipePricer pricer = new RecipePricer(prices);
        prices.setPricer(pricer);

        modContainer.registerConfig(ModConfig.Type.SERVER, TeConfig.SPEC);
        modEventBus.addListener(ModConfigEvent.Loading.class, this::onConfigLoad);
        modEventBus.addListener(ModConfigEvent.Reloading.class, this::onConfigReload);

        ModRegistries.register(modEventBus);
        modEventBus.addListener(ModNetwork::register);
        modEventBus.addListener(com.evolt.teamecon.network.ShopNetwork::register);

        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(ModCommands::register);
        NeoForge.EVENT_BUS.addListener(StartingBalanceHandler::onLogin);
        NeoForge.EVENT_BUS.addListener(TeamEconomyMod::onContainerOpened);
        NeoForge.EVENT_BUS.addListener(this::onDatapackSync);

        ModLogger.info("Team Economy initialized");
    }

    private void onConfigLoad(ModConfigEvent.Loading event) {
        ModLogger.info("Team Economy config loaded: {}", event.getConfig().getFileName());
    }

    private void onConfigReload(ModConfigEvent.Reloading event) {
        ModLogger.info("Team Economy config reloaded");
    }

    private void onServerStarting(ServerStartingEvent event) {
        reloadServices(event.getServer());
    }

    public void reloadServices(MinecraftServer server) {
        TeamEconomyManager manager = TeamEconomyManager.get(server);
        // Base prices are also written to the config folder for easy editing.
        prices.basePrices().load(FMLPaths.CONFIGDIR.get().resolve("teamecon_base_prices.json"));
        prices.shopPrices().load(FMLPaths.CONFIGDIR.get());
        prices.setProvider(server.registryAccess());
        // Loot and drops have no recipe, so the rarity floor is what gives them value.
        prices.setRarityFallback(RarityFallback.fromConfig());
        prices.setCraftValue(TeConfig.PRICING.craftValuePerStep.get(), TeConfig.PRICING.maxCraftMarkup.get());
        prices.pricer().indexRecipes(server.getRecipeManager(), server.registryAccess());
        prices.pricer().computeAll();
        prices.collectFallbacks();
        logExamplePrices(prices);
        var purchaseRules = new com.evolt.teamecon.shop.PurchaseRules(server, manager);
        casinoProgression = new com.evolt.teamecon.gambling.CasinoProgression();
        casinoProgression.load(FMLPaths.CONFIGDIR.get());
        economy = new EconomyService(server, manager, prices, purchaseRules, casinoProgression);

        gambling = new GamblingService(server, manager, casinoProgression);
        gambling.loadConfigs(FMLPaths.CONFIGDIR.get());

        shop = new ShopService(server, manager, prices, purchaseRules, casinoProgression);
        shop.loadConfigs(FMLPaths.CONFIGDIR.get());
        scratchCards = shop.cards();

        ModLogger.info("Team Economy services ready (teams loaded: {})",
                com.evolt.teamecon.team.TeamUtil.isTeamsLoaded());
        com.evolt.teamecon.network.PriceSyncHandler.broadcastNow(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            com.evolt.teamecon.network.ProgressionNetwork.send(player, true);
            if (player.containerMenu instanceof ShopMenu menu) {
                menu.invalidateCatalog();
                com.evolt.teamecon.network.ShopNetwork.sendSync(player);
            } else if (player.containerMenu instanceof com.evolt.teamecon.casino.CasinoMenu) {
                ModNetwork.sendOpenSync(player);
            }
        }
    }

    private void onDatapackSync(net.neoforged.neoforge.event.OnDatapackSyncEvent event) {
        if (event.getPlayer() == null && economy != null) reloadServices(event.getPlayerList().getServer());
    }

    /** A few well-known items, logged so the price table can be eyeballed at startup. */
    private void logExamplePrices(PriceService prices) {
        ModLogger.info("Example prices: apple={}, golden_apple={}, enchanted_golden_apple={}, nether_star={}, diamond={}, emerald={}",
                prices.resolve("minecraft:apple").unitPrice(),
                prices.resolve("minecraft:golden_apple").unitPrice(),
                prices.resolve("minecraft:enchanted_golden_apple").unitPrice(),
                prices.resolve("minecraft:nether_star").unitPrice(),
                prices.resolve("minecraft:diamond").unitPrice(),
                prices.resolve("minecraft:emerald").unitPrice());
    }
    /** Sends the shop catalog to a player the moment they open the vending machine. */
    private static void onContainerOpened(net.neoforged.neoforge.event.entity.player.PlayerContainerEvent.Open event) {
        if (event.getContainer() instanceof ShopMenu && event.getEntity() instanceof ServerPlayer serverPlayer) {
            com.evolt.teamecon.network.ShopNetwork.sendSync(serverPlayer);
        }
    }
    private void onServerStopping(ServerStoppingEvent event) {
        economy = null;
        gambling = null;
        shop = null;
        scratchCards = null;
        casinoProgression = null;
        prices.setProvider(null);
        ModLogger.info("Team Economy services released");
    }

    public EconomyService economy() {
        return economy;
    }

    public GamblingService gambling() {
        return gambling;
    }

    public ShopService shop() {
        return shop;
    }

    public com.evolt.teamecon.scratch.ScratchCardService scratchCards() { return scratchCards; }
    public com.evolt.teamecon.gambling.CasinoProgression casinoProgression() { return casinoProgression; }

    public PriceService prices() {
        return prices;
    }


    public static TeamEconomyMod get() {
        return instance;
    }
}
