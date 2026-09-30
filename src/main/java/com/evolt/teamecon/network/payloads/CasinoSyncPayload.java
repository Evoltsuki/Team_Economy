package com.evolt.teamecon.network.payloads;

import com.evolt.teamecon.TeamEconomyMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CasinoSyncPayload(int containerId, long requestId, long balance, String walletName,
        long minBet, long maxBet, double multiplier, long stake, int rounds, String sessionGame,
        long cashOut, double multiplierLimit, String game, String messageKey, String messageArgs,
        String extra, String tierOdds, String slotsOdds, String scratchOdds, double hiloPayout, double expectedCap)
        implements CustomPacketPayload {
    public static final Type<CasinoSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TeamEconomyMod.MOD_ID, "casino_sync"));
    public static final StreamCodec<FriendlyByteBuf, CasinoSyncPayload> STREAM_CODEC =
            StreamCodec.of(CasinoSyncPayload::write, CasinoSyncPayload::new);
    public CasinoSyncPayload(FriendlyByteBuf b) {
        this(b.readVarInt(), b.readVarLong(), b.readVarLong(), b.readUtf(128),
                b.readVarLong(), b.readVarLong(), b.readDouble(), b.readVarLong(), b.readVarInt(), b.readUtf(32),
                b.readVarLong(), b.readDouble(), b.readUtf(32), b.readUtf(128), b.readUtf(256), b.readUtf(256),
                b.readUtf(4096), b.readUtf(4096), b.readUtf(4096), b.readDouble(), b.readDouble());
    }
    public static void write(FriendlyByteBuf b, CasinoSyncPayload p) {
        b.writeVarInt(p.containerId); b.writeVarLong(p.requestId); b.writeVarLong(p.balance); b.writeUtf(p.walletName, 128);
        b.writeVarLong(p.minBet); b.writeVarLong(p.maxBet); b.writeDouble(p.multiplier); b.writeVarLong(p.stake);
        b.writeVarInt(p.rounds); b.writeUtf(p.sessionGame, 32); b.writeVarLong(p.cashOut); b.writeDouble(p.multiplierLimit);
        b.writeUtf(p.game, 32); b.writeUtf(p.messageKey, 128); b.writeUtf(p.messageArgs, 256); b.writeUtf(p.extra, 256);
        b.writeUtf(p.tierOdds, 4096); b.writeUtf(p.slotsOdds, 4096); b.writeUtf(p.scratchOdds, 4096);
        b.writeDouble(p.hiloPayout); b.writeDouble(p.expectedCap);
    }
    public CasinoSyncPayload withBalance(long balance, String name) {
        return new CasinoSyncPayload(containerId, requestId, balance, name, minBet, maxBet, multiplier, stake, rounds,
                sessionGame, cashOut, multiplierLimit, game, messageKey, messageArgs, extra, tierOdds, slotsOdds,
                scratchOdds, hiloPayout, expectedCap);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
