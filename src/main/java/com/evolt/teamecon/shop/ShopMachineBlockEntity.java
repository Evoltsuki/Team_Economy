package com.evolt.teamecon.shop;

import com.evolt.teamecon.init.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ShopMachineBlockEntity extends BlockEntity {
    private long dispensedAt = -1000;
    private String item = "minecraft:emerald";
    public ShopMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.SHOP_MACHINE_ENTITY.get(), pos, state);
    }
    public void dispense(String item) {
        if (level == null || level.isClientSide) return;
        this.item = item;
        dispensedAt = level.getGameTime();
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        level.playSound(null, worldPosition, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, .5F, 1.2F);
    }
    public String item() { return item; }
    public float progress(float partial) {
        return level == null ? 1 : Math.clamp((level.getGameTime() - dispensedAt + partial) / 30F, 0F, 1F);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putLong("dispensedAt", dispensedAt);
        tag.putString("item", item);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        dispensedAt = tag.contains("dispensedAt") ? tag.getLong("dispensedAt") : -1000;
        item = tag.getString("item");
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, provider);
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
