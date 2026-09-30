package com.evolt.teamecon.network.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BalanceSyncPayload(int containerId, long balance, String walletName, long saleQuote) implements CustomPacketPayload {
    public static final Type<BalanceSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("teamecon", "balance_sync"));
    public static final StreamCodec<FriendlyByteBuf, BalanceSyncPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeVarInt(p.containerId); b.writeVarLong(p.balance); b.writeUtf(p.walletName, 128); b.writeVarLong(p.saleQuote); },
            b -> new BalanceSyncPayload(b.readVarInt(), b.readVarLong(), b.readUtf(128), b.readVarLong()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
