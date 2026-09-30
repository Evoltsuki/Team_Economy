package com.evolt.teamecon.client;

import com.evolt.teamecon.TeamEconomyMod;
import com.evolt.teamecon.price.PriceService;
import com.evolt.teamecon.casino.CasinoMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;

/**
 * Draws the auto-valued price under every item the player can see in a container screen, so
 * the economy is legible without hovering anything. Prices come from the synced cache; when
 * the table has not arrived yet nothing is drawn rather than a wrong number.
 */
@EventBusSubscriber(modid = TeamEconomyMod.MOD_ID, value = Dist.CLIENT)
public final class ItemPriceLabelHandler {

    private static final int LABEL_COLOR = 0xFFE9A93A;
    private static final int SHADOW_COLOR = 0xFF000000;

    private ItemPriceLabelHandler() {
    }

    @SubscribeEvent
    public static void onRenderForeground(ContainerScreenEvent.Render.Foreground event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (!(event.getContainerScreen() instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        ClientPriceCache.PriceTable table = ClientPriceCache.table();
        if (table == null || table.isEmpty()) {
            return;
        }
        AbstractContainerMenu menu = screen.getMenu();
        GuiGraphics graphics = event.getGuiGraphics();
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();

        for (Slot slot : menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            // The player's inventory and the casino deposit slot both get labels: the
            // deposit slot is where goods become points, so its worth belongs on screen.
            boolean playerOwned = slot.container instanceof Inventory;
            boolean casinoDeposit = menu instanceof CasinoMenu
                    && slot.index == CasinoMenu.DEPOSIT_SLOT;
            if (!playerOwned && !casinoDeposit) {
                continue;
            }
            String itemKey = PriceService.itemKeyStatic(stack.getItem());
            if (!com.evolt.teamecon.price.TradePolicy.canSell(itemKey)) continue;
            Long price = table.priceOf(itemKey);
            if (price == null || price <= 0L) {
                continue;
            }
            int x = screen.getGuiLeft() + slot.x;
            int y = screen.getGuiTop() + slot.y;
            // Skip the slot under the cursor so the label never covers the pickup highlight.
            if (mouseX >= x - 1 && mouseX <= x + 17 && mouseY >= y - 1 && mouseY <= y + 17) {
                continue;
            }
            String text = formatPrice(price);
            int textWidth = minecraft.font.width(text);
            // Foreground already has the menu origin applied. Screen coordinates here
            // translate twice and leave labels floating below the entire interface.
            // Keep the small price at the top of the icon, clear of quantity/durability.
            float scale = Math.min(.5F, 15F / Math.max(1, textWidth));
            graphics.pose().pushPose();
            graphics.pose().translate(slot.x + 8 - textWidth * scale / 2, slot.y + .5F, 200);
            graphics.pose().scale(scale, scale, 1);
            graphics.fill(-1, -1, textWidth + 1, 8, SHADOW_COLOR);
            graphics.drawString(minecraft.font, text, 0, 0, LABEL_COLOR, false);
            graphics.pose().popPose();
        }
    }

    /** Compact large prices the way the rest of the UI would. */
    private static String formatPrice(long price) {
        if (price >= 1_000_000L) {
            return (price / 1_000_000L) + "M";
        }
        if (price >= 1_000L) {
            return (price / 1_000L) + "k";
        }
        return String.valueOf(price);
    }
}
