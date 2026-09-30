package com.evolt.teamecon.casino;

import com.evolt.teamecon.TeamEconomyMod;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/** Optional integration is isolated so dedicated servers also run without Patchouli. */
public final class GuideBookItem extends Item {
    public static final ResourceLocation BOOK=ResourceLocation.fromNamespaceAndPath("teamecon","casino_guide");
    public GuideBookItem(Properties properties){super(properties);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        if(player instanceof ServerPlayer serverPlayer){
            if(!ModList.get().isLoaded("patchouli"))player.displayClientMessage(Component.translatable("book.teamecon.missing"),true);
            else try{Bridge.open(serverPlayer);}catch(RuntimeException e){
                TeamEconomyMod.LOGGER.error("Could not open Team Economy handbook",e);
                player.displayClientMessage(Component.translatable("book.teamecon.failed"),true);
            }
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
    }
    private static final class Bridge {
        static void open(ServerPlayer player){vazkii.patchouli.api.PatchouliAPI.get().openBookGUI(player,BOOK);}
    }
}
