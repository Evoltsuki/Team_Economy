package com.evolt.teamecon.network.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PriceAdminSyncPayload(int containerId, long sequence, String item, long buy, long sell,
        long effectiveBuy, long effectiveSell, long reference, long revision, boolean editable, String message) implements CustomPacketPayload {
    public static final Type<PriceAdminSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("teamecon", "price_admin_sync"));
    public static final StreamCodec<FriendlyByteBuf, PriceAdminSyncPayload> STREAM_CODEC = StreamCodec.of(PriceAdminSyncPayload::write, PriceAdminSyncPayload::new);
    public PriceAdminSyncPayload(FriendlyByteBuf b) {
        this(b.readVarInt(), b.readVarLong(), b.readUtf(256), b.readLong(), b.readLong(), b.readLong(), b.readLong(), b.readLong(), b.readVarLong(), b.readBoolean(), b.readUtf(128));
    }
    public static void write(FriendlyByteBuf b, PriceAdminSyncPayload p) {
        b.writeVarInt(p.containerId); b.writeVarLong(p.sequence); b.writeUtf(p.item, 256); b.writeLong(p.buy); b.writeLong(p.sell);
        b.writeLong(p.effectiveBuy); b.writeLong(p.effectiveSell); b.writeLong(p.reference); b.writeVarLong(p.revision); b.writeBoolean(p.editable); b.writeUtf(p.message, 128);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
