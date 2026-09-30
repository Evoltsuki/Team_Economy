package com.evolt.teamecon.network.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ShopActionPayload(int containerId, long requestId, Kind kind, String target, int amount) implements CustomPacketPayload {
    public enum Kind { BUY_ITEM, BUY_BOX, ENCHANT, SELL_STACK, BUY_TICKET, UPGRADE_LEVEL, OPEN_TERMINAL }
    public static final Type<ShopActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("teamecon", "shop_action"));
    public static final StreamCodec<FriendlyByteBuf, ShopActionPayload> STREAM_CODEC = StreamCodec.of(ShopActionPayload::write, ShopActionPayload::new);
    public ShopActionPayload(FriendlyByteBuf b) {
        this(b.readVarInt(), b.readVarLong(), b.readEnum(Kind.class), b.readUtf(256), b.readVarInt());
    }
    public static void write(FriendlyByteBuf b, ShopActionPayload p) {
        b.writeVarInt(p.containerId); b.writeVarLong(p.requestId); b.writeEnum(p.kind);
        b.writeUtf(p.target, 256); b.writeVarInt(p.amount);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
