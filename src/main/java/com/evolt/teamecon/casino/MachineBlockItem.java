package com.evolt.teamecon.casino;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

/** The block validates both spaces and places its upper half in setPlacedBy. */
public class MachineBlockItem extends BlockItem {
    public MachineBlockItem(Block block, Properties properties) { super(block, properties); }
    @Override public void appendHoverText(net.minecraft.world.item.ItemStack stack,TooltipContext context,
            java.util.List<net.minecraft.network.chat.Component> lines,net.minecraft.world.item.TooltipFlag flag){
        if(getBlock() instanceof CasinoMachineBlock machine){
            var size=MachineLayout.of(machine.game());
            lines.add(net.minecraft.network.chat.Component.translatable("machine.teamecon.footprint",size.width(),size.depth(),size.height()));
            lines.add(net.minecraft.network.chat.Component.translatable("machine.teamecon.item_hint"));
        }
    }
}
