package com.evolt.teamecon.forge;
import net.minecraft.resources.ResourceLocation;
public interface CustomPacketPayload {
    record Type<T extends CustomPacketPayload>(ResourceLocation id, Class<T> messageClass) {}
    Type<? extends CustomPacketPayload> type();
}
