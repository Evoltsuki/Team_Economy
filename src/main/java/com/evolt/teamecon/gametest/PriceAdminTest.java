package com.evolt.teamecon.gametest;

import com.evolt.teamecon.price.*;
import com.evolt.teamecon.economy.*;
import com.evolt.teamecon.shop.*;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ClickType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.nio.file.*;

@GameTestHolder("teamecon")
public final class PriceAdminTest {
    @GameTest(template="empty")
    public static void overridesAffectQuotesPurchasesAndSnapshotsWithoutChangingRecipes(GameTestHelper h) throws Exception {
        Path dir = Files.createTempDirectory("teamecon-prices-test-");
        var prices = new PriceService(); prices.basePrices().put("minecraft:gold_ingot", 64); prices.overrides().load(dir);
        try {
            prices.overrides().save("minecraft:gold_ingot", new PriceOverrides.Entry(5, 100), prices.overrides().revision());
            h.assertTrue(prices.resolve("minecraft:gold_ingot").unitPrice() == 100, "Override missing from settlement price");
            h.assertTrue(prices.saleSnapshot().get("minecraft:gold_ingot") == 100, "Client snapshot differs");
            h.assertTrue(prices.resolve("minecraft:gold_block").unitPrice() == 576, "Item override changed recipe anchors");
            h.assertTrue(prices.purchasePrice("minecraft:gold_ingot", 2) == 200, "Purchase can undercut recycling");
            var player = ProgressionTest.player(h, "price-admin-sale");
            var economy = new EconomyService(player.getServer(), new TeamEconomyManager(), prices);
            ItemStack stack = new ItemStack(Items.GOLD_INGOT, 3);
            long quote = economy.saleQuote(player, stack);
            h.assertTrue(economy.sell(player, stack).total() == quote && stack.isEmpty(), "Quote and payment differ");
            prices.overrides().save("minecraft:gold_ingot", new PriceOverrides.Entry(-1, 0), prices.overrides().revision());
            h.assertTrue(prices.purchasable("minecraft:gold_ingot"), "Disabling sale disabled purchase");
            h.assertTrue(!prices.saleSnapshot().containsKey("minecraft:gold_ingot"), "Disabled sale is still advertised");
            stack = new ItemStack(Items.GOLD_INGOT);
            h.assertTrue(economy.sell(player, stack).outcome() == EconomyService.Outcome.NOT_PRICED && !stack.isEmpty(), "Disabled item consumed");
            prices.overrides().save("minecraft:gold_ingot", PriceOverrides.DEFAULT, prices.overrides().revision());
            h.assertTrue(prices.resolve("minecraft:gold_ingot").unitPrice() == 64, "Reset failed");
            h.succeed();
        } finally { try (var files = Files.list(dir)) { for (Path file : files.toList()) Files.deleteIfExists(file); } Files.delete(dir); }
    }
    @GameTest(template="empty")
    public static void pricingMenuHasNoMovableSlotsAndRequiresPermission(GameTestHelper h) {
        var player = ProgressionTest.player(h, "price-no-items");
        var menu = new PriceAdminMenu(94, player.getInventory(), "");
        h.assertTrue(menu.slots.isEmpty(), "Editor contains inventory slots");
        menu.clicked(0, 0, ClickType.CLONE, player);
        menu.clicked(-999, 0, ClickType.THROW, player);
        h.assertTrue(menu.quickMoveStack(player, 0).isEmpty() && menu.getCarried().isEmpty(), "Editor spawned an item");
        h.assertTrue(!PriceAdminMenu.authorized(player, 94), "Accepted request without an open editor");
        h.assertTrue(!menu.stillValid(player), "Non-OP can edit prices");
        player.containerMenu = menu;
        var prices = com.evolt.teamecon.TeamEconomyMod.get().prices();
        long revision = prices.overrides().revision();
        com.evolt.teamecon.network.PriceAdminNetwork.handle(player, new com.evolt.teamecon.network.payloads.PriceAdminActionPayload(
                94, 1, true, "minecraft:gold_ingot", 1, 1, revision));
        h.assertTrue(prices.overrides().revision() == revision, "Forged save bypassed OP check");
        player.containerMenu = player.inventoryMenu;
        h.succeed();
    }
}
