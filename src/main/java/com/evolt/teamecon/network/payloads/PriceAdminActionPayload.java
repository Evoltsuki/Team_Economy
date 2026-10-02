package com.evolt.teamecon.network.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PriceAdminActionPayload(int containerId, long sequence, boolean save, String item, long buy, long sell, long revision) implements CustomPacketPayload {
    public static final Type<PriceAdminActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("teamecon", "price_admin_action"));
    public static final StreamCodec<FriendlyByteBuf, PriceAdminActionPayload> STREAM_CODEC = StreamCodec.of(PriceAdminActionPayload::write, PriceAdminActionPayload::new);
    public PriceAdminActionPayload(FriendlyByteBuf b) { this(b.readVarInt(), b.readVarLong(), b.readBoolean(), b.readUtf(256), b.readLong(), b.readLong(), b.readVarLong()); }
    public static void write(FriendlyByteBuf b, PriceAdminActionPayload p) {
        b.writeVarInt(p.containerId); b.writeVarLong(p.sequence); b.writeBoolean(p.save); b.writeUtf(p.item, 256);
        b.writeLong(p.buy); b.writeLong(p.sell); b.writeVarLong(p.revision);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
