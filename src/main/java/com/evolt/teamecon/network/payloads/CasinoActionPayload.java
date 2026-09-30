package com.evolt.teamecon.network.payloads;

import com.evolt.teamecon.TeamEconomyMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bound to an open container and a monotonically increasing request number. */
public record CasinoActionPayload(int containerId, long requestId, Action action, String game, String choice, long amount)
        implements CustomPacketPayload {
    public enum Action { PLAY, CASH_OUT, DEPOSIT, BUY_CARD, OPEN_SHOP }
    public static final Type<CasinoActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TeamEconomyMod.MOD_ID, "casino_action"));
    public static final StreamCodec<FriendlyByteBuf, CasinoActionPayload> STREAM_CODEC =
            StreamCodec.of(CasinoActionPayload::write, CasinoActionPayload::new);
    public CasinoActionPayload(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readVarLong(), buf.readEnum(Action.class), buf.readUtf(32), buf.readUtf(64), buf.readVarLong());
    }
    public static void write(FriendlyByteBuf buf, CasinoActionPayload p) {
        buf.writeVarInt(p.containerId); buf.writeVarLong(p.requestId);
        buf.writeEnum(p.action); buf.writeUtf(p.game, 32); buf.writeUtf(p.choice, 64); buf.writeVarLong(p.amount);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
