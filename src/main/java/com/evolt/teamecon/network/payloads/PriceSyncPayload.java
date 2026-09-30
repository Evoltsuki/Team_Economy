package com.evolt.teamecon.network.payloads;

import com.evolt.teamecon.TeamEconomyMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * Server-to-client snapshot of every known item price, so the client can show the auto-valued
 * sell price in item tooltips without re-deriving recipe values itself. Only base or
 * recipe-derived prices are sent; demand decay is applied at sale time and depends on the
 * quantity, so the tooltip always shows the stable per-unit value.
 */
public record PriceSyncPayload(Map<String, Long> prices) implements CustomPacketPayload {

    public static final Type<PriceSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TeamEconomyMod.MOD_ID, "price_sync"));

    public static final StreamCodec<FriendlyByteBuf, PriceSyncPayload> STREAM_CODEC =
            StreamCodec.of(PriceSyncPayload::write, PriceSyncPayload::new);

    public PriceSyncPayload(FriendlyByteBuf buf) {
        this(buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readVarLong));
    }

    public static void write(FriendlyByteBuf buf, PriceSyncPayload payload) {
        buf.writeMap(payload.prices, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeVarLong);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}