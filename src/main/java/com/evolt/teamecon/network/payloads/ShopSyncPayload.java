package com.evolt.teamecon.network.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bounded catalog chunks avoid exceeding Minecraft's custom-payload limit in large modpacks. */
public record ShopSyncPayload(int containerId, long balance, String walletName, long saleQuote,
        int chunkIndex, int chunkCount, String items, String boxes, String enchants, String messageKey, String messageArgs, String rewards, String pending)
        implements CustomPacketPayload {
    public static final Type<ShopSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("teamecon", "shop_sync"));
    public static final StreamCodec<FriendlyByteBuf, ShopSyncPayload> STREAM_CODEC = StreamCodec.of(ShopSyncPayload::write, ShopSyncPayload::new);
    public ShopSyncPayload(int id, long balance, String name, long quote, int part, int parts, String items,
            String boxes, String enchants, String message, String args, String rewards) {
        this(id,balance,name,quote,part,parts,items,boxes,enchants,message,args,rewards,"");
    }
    public ShopSyncPayload(FriendlyByteBuf b) {
        this(b.readVarInt(), b.readVarLong(), b.readUtf(128), b.readVarLong(), b.readVarInt(), b.readVarInt(),
                b.readUtf(16000), b.readUtf(32767), b.readUtf(32767), b.readUtf(128), b.readUtf(512), b.readUtf(32767), b.readUtf(32767));
    }
    public static void write(FriendlyByteBuf b, ShopSyncPayload p) {
        b.writeVarInt(p.containerId); b.writeVarLong(p.balance); b.writeUtf(p.walletName, 128); b.writeVarLong(p.saleQuote);
        b.writeVarInt(p.chunkIndex); b.writeVarInt(p.chunkCount); b.writeUtf(p.items, 16000);
        b.writeUtf(p.boxes, 32767); b.writeUtf(p.enchants, 32767);
        b.writeUtf(p.messageKey, 128); b.writeUtf(p.messageArgs, 512); b.writeUtf(p.rewards, 32767); b.writeUtf(p.pending, 32767);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
