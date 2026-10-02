package com.evolt.teamecon.gametest;

import com.evolt.teamecon.economy.*;
import com.evolt.teamecon.price.PriceService;
import com.evolt.teamecon.shop.*;
import com.evolt.teamecon.team.TeamUtil;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.nio.file.*;
import java.util.UUID;

@GameTestHolder("teamecon")
public final class ServerConfigurationTest {
    @GameTest(template="empty")
    public static void customCatalogueControlsMachineAndCommandWithoutDisablingRecycling(GameTestHelper h) throws Exception {
        Path dir = Files.createTempDirectory("teamecon-catalog-test-");
        try {
            var p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "catalog-test"));
            p.getInventory().clearContent();
            var manager = new TeamEconomyManager(); var wallet = TeamUtil.walletKey(p.getServer(), p.getUUID());
            manager.setBalance(wallet, 10000);
            var prices = new PriceService(); prices.basePrices().load(dir.resolve("base.json"));
            var rules = new PurchaseRules(p.getServer(), manager);
            var shop = new ShopService(p.getServer(), manager, prices, rules);
            var economy = new EconomyService(p.getServer(), manager, prices, rules);
            Files.writeString(dir.resolve(ShopCatalog.FILE_NAME), """
                    {"version":1,"includeDefaultItems":false,"disabledItems":["minecraft:diamond"],"items":[
                    {"item":"minecraft:bread","price":50},{"item":"minecraft:diamond","price":1000}]}
                    """);
            shop.loadConfigs(dir);
            h.assertTrue(shop.itemCatalog().equals(java.util.List.of("minecraft:bread")), "Custom-only catalogue leaked items");
            h.assertTrue(shop.buyItem(p, Items.BREAD, 2).outcome() == ShopService.Outcome.OK, "Machine purchase failed");
            h.assertTrue(economy.buy(p, Items.BREAD, 3).outcome() == EconomyService.Outcome.OK, "Command purchase failed");
            h.assertTrue(manager.getBalance(wallet) == 9750 && p.getInventory().countItem(Items.BREAD) == 5, "Price/delivery mismatch");
            h.assertTrue(shop.buyItem(p, Items.DIAMOND, 1).outcome() != ShopService.Outcome.OK, "Machine ignored exclusion");
            h.assertTrue(economy.buy(p, Items.DIAMOND, 1).outcome() != EconomyService.Outcome.OK, "Command ignored exclusion");
            h.assertTrue(economy.sell(p, new ItemStack(Items.DIAMOND)).outcome() == EconomyService.Outcome.OK, "Purchase exclusion disabled recycling");
            long before = manager.getBalance(wallet);
            Files.writeString(dir.resolve(ShopCatalog.FILE_NAME), "{broken"); shop.loadConfigs(dir);
            h.assertTrue(shop.itemCatalog().isEmpty(), "Invalid catalogue left old offers visible");
            h.assertTrue(economy.buy(p, Items.BREAD, 1).outcome() != EconomyService.Outcome.OK && manager.getBalance(wallet) == before, "Invalid catalogue allowed command purchase");
            Files.writeString(dir.resolve(ShopCatalog.FILE_NAME), "{\"version\":1,\"items\":[]}"); shop.loadConfigs(dir);
            h.assertTrue(economy.buy(p, Items.BREAD, 1).outcome() == EconomyService.Outcome.OK, "Repaired catalogue did not reload");
        } finally { cleanup(dir); }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void configurableBoxRewardsReloadAtomicallyAndRespectStageAndValueSwitches(GameTestHelper h) throws Exception {
        Path dir = Files.createTempDirectory("teamecon-custom-box-test-");
        try {
            var p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "box-config"));
            p.getInventory().clearContent();
            var manager = new TeamEconomyManager(); var wallet = TeamUtil.walletKey(p.getServer(), p.getUUID());
            manager.setBalance(wallet, 10000);
            var prices = new PriceService(); prices.basePrices().load(dir.resolve("base.json"));
            var shop = new ShopService(p.getServer(), manager, prices);
            Path file = dir.resolve("teamecon_blindbox.json");
            Files.writeString(file, """
                    {"version":1,"pools":[
                    {"id":"gift","price":10,"enforceValueCap":false,"entries":[{"item":"minecraft:diamond","count":2,"weight":1}]},
                    {"id":"capped","price":10,"entries":[{"item":"minecraft:diamond","count":2,"weight":1}]},
                    {"id":"stage","stage":"custom_stage","price":10,"entries":[{"item":"minecraft:bread","weight":1}]},
                    {"id":"missing","price":10,"allowModdedItems":true,"entries":[{"item":"missingmod:gem","weight":1}]}]}
                    """);
            shop.loadConfigs(dir);
            h.assertTrue(shop.blindBoxes().byId("capped") == null && shop.blindBoxes().byId("missing") == null, "Invalid rewards remained purchasable");
            h.assertTrue(shop.buyBlindBox(p, "gift", 3).outcome() == ShopService.Outcome.OK, "Explicit reward override failed");
            h.assertTrue(manager.getBalance(wallet) == 9970 && p.getInventory().countItem(Items.DIAMOND) == 6, "Batch reward/price mismatch");
            h.assertTrue(shop.buyBlindBox(p, "stage").outcome() == ShopService.Outcome.PROGRESSION_LOCKED, "Custom stage ignored");
            Files.writeString(file, "[{\"id\":\"gift\",\"price\":10,\"entries\":[{\"item\":\"minecraft:diamond\",\"weight\":0}]}]");
            shop.loadConfigs(dir);
            h.assertTrue(shop.buyBlindBox(p, "gift").outcome() == ShopService.Outcome.NO_POOL && manager.getBalance(wallet) == 9970, "Invalid reload charged for stale prize");
        } finally { cleanup(dir); }
        h.succeed();
    }

    private static void cleanup(Path dir) throws Exception {
        try (var files = Files.list(dir)) { for (Path path : files.toList()) Files.delete(path); }
        Files.delete(dir);
    }
}
