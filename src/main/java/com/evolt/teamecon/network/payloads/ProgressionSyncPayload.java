package com.evolt.teamecon.network.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ProgressionSyncPayload(int level, boolean valid, String rules, String statistics) implements CustomPacketPayload {
    public static final Type<ProgressionSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("teamecon", "casino_progression"));
    public static final StreamCodec<FriendlyByteBuf, ProgressionSyncPayload> STREAM_CODEC = StreamCodec.of(ProgressionSyncPayload::write, ProgressionSyncPayload::new);
    public ProgressionSyncPayload(FriendlyByteBuf b) { this(b.readVarInt(), b.readBoolean(), b.readUtf(16384), b.readUtf(16384)); }
    private static void write(FriendlyByteBuf b, ProgressionSyncPayload p) {
        b.writeVarInt(p.level); b.writeBoolean(p.valid); b.writeUtf(p.rules, 16384); b.writeUtf(p.statistics, 16384);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
