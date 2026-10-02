package com.evolt.teamecon.client;

import com.evolt.teamecon.TeamEconomyMod;
import com.evolt.teamecon.price.PriceService;
import com.evolt.teamecon.config.TeConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/**
 * Adds the auto-valued sell price under every item. The price comes from the server-synced
 * table, so this event also covers third-party recipe viewers (including JEI) because they
 * render the same vanilla tooltip.
 */
@EventBusSubscriber(modid = TeamEconomyMod.MOD_ID, value = Dist.CLIENT)
public final class TooltipPriceHandler {

    private TooltipPriceHandler() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!TeConfig.PRICING.showPriceTooltips.get()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        // The price table keys on the registry id ("minecraft:iron_ingot"). Deriving it from the
        // description id used to keep the type prefix and drop the namespace, so every lookup
        // missed and the tooltip never showed a price.
        String registryKey = PriceService.itemKeyStatic(stack.getItem());
        if (!PriceService.plainModStack(stack)) return;
        long price = ClientPriceCache.priceOf(registryKey);
        if (price <= 0) {
            return;
        }
        List<Component> tooltip = event.getToolTip();
        tooltip.add(Component.translatable("tooltip.teamecon.sell_value", price)
                .withStyle(ChatFormatting.GOLD));
    }
}
