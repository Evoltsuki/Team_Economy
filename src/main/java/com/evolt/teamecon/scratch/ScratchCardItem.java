package com.evolt.teamecon.scratch;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import java.util.List;
import java.util.UUID;

public final class ScratchCardItem extends Item {
    private final ScratchKind kind;
    public ScratchCardItem(Properties properties, ScratchKind kind) { super(properties.stacksTo(1));this.kind=kind; }
    public ScratchKind kind() { return kind; }
    public static UUID serial(ItemStack stack) {
        if (!(stack.getItem() instanceof ScratchCardItem)) return null;
        CompoundTag tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        return tag.hasUUID("ticket")?tag.getUUID("ticket"):null;
    }
    public static void stamp(ItemStack stack, UUID id) {
        CompoundTag tag=new CompoundTag();tag.putUUID("ticket",id);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("scratch.teamecon.open_hint"));
        lines.add(Component.translatable("scratch.teamecon.rule."+kind.id()));
        lines.add(Component.translatable("scratch.teamecon.price",kind.price()));
        if(serial(stack)==null)lines.add(Component.translatable("scratch.teamecon.blank"));
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if(player instanceof net.minecraft.server.level.ServerPlayer p && hand==InteractionHand.MAIN_HAND)
            com.evolt.teamecon.TeamEconomyMod.get().scratchCards().open(p);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
    }
}
