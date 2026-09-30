package com.evolt.teamecon.casino;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Wireless terminal: opens the casino interface anywhere, so scratch cards and the other
 * mini-games can be played without returning to a machine. Outcomes are still decided
 * server-side; the item never stores balance.
 */
public class TerminalItem extends Item {

    private static final Component TITLE = Component.translatable("container.teamecon.terminal");

    public TerminalItem(Properties properties) {
        super(properties);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,java.util.List<Component> lines,
                                         net.minecraft.world.item.TooltipFlag flag){
        lines.add(Component.translatable("item.teamecon.terminal.tooltip"));
    }

    public static boolean authorized(ServerPlayer player) {
        var mod=com.evolt.teamecon.TeamEconomyMod.get();
        return player.isAlive()&&player.getInventory().contains(new ItemStack(com.evolt.teamecon.init.ModRegistries.TERMINAL.get()))
                &&mod!=null&&mod.economy()!=null&&mod.casinoProgression()!=null
                &&mod.casinoProgression().terminalAccess(player,mod.economy().manager()).unlocked();
    }
    public static void open(ServerPlayer player) {
        if(!authorized(player))return;
        player.openMenu(new net.minecraft.world.SimpleMenuProvider((id,inv,p)->new CasinoMenu(id,inv,"scratch",player.blockPosition(),true),TITLE),b->{
            b.writeUtf("scratch",32);b.writeBoolean(true);b.writeBlockPos(player.blockPosition());
        });
        com.evolt.teamecon.network.ModNetwork.sendOpenSync(player);
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        ItemStack stack=player.getItemInHand(hand);
        if(player instanceof ServerPlayer p){
            if(!authorized(p)){
                var mod=com.evolt.teamecon.TeamEconomyMod.get();
                if(mod!=null&&mod.economy()!=null)p.displayClientMessage(com.evolt.teamecon.shop.PurchaseRules.describe(mod.casinoProgression().terminalAccess(p,mod.economy().manager()).reason()),true);
                return InteractionResultHolder.fail(stack);
            }
            open(p);
        }
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
}
