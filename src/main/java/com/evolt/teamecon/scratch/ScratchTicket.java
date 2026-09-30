package com.evolt.teamecon.scratch;

import net.minecraft.nbt.CompoundTag;
import java.util.UUID;

/** The item contains only its serial number. The server saves the paid prize separately. */
public record ScratchTicket(UUID id, ScratchKind kind, long price, int bucket, long payout, long seed) {
    public CompoundTag save() {
        CompoundTag t=new CompoundTag();t.putUUID("id",id);t.putString("kind",kind.id());t.putLong("price",price);
        t.putInt("bucket",bucket);t.putLong("payout",payout);t.putLong("seed",seed);return t;
    }
    public static ScratchTicket load(CompoundTag t) {
        ScratchKind kind=ScratchKind.byId(t.getString("kind"));
        long price=t.getLong("price"), payout=t.getLong("payout"); int bucket=t.getInt("bucket");
        if(kind==null || price<=0 || payout<0 || payout>com.evolt.teamecon.economy.MoneyMath.MAX_MONEY
                || bucket<0 || bucket>=kind.multipliers().length) throw new IllegalArgumentException("Invalid scratch ticket");
        return new ScratchTicket(t.getUUID("id"),kind,price,bucket,payout,t.getLong("seed"));
    }
}
