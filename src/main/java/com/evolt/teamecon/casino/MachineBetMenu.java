package com.evolt.teamecon.casino;

import com.evolt.teamecon.TeamEconomyMod;
import com.evolt.teamecon.init.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** The multiplier is applied to a server-held base stake; opening this menu never bets. */
public class MachineBetMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final long base, limit;
    private final int factor;
    private boolean applied;
    public MachineBetMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos(), buf.readVarLong(), buf.readVarLong(), buf.readVarInt());
    }
    public MachineBetMenu(int id, Inventory inventory, BlockPos pos, long base, long limit) {
        this(id, inventory, pos, base, limit, 1);
    }
    public MachineBetMenu(int id, Inventory inventory, BlockPos pos, long base, long limit, int factor) {
        super(ModRegistries.MACHINE_BET_MENU.get(), id); this.pos=pos; this.base=base; this.limit=limit; this.factor=factor;
    }
    public long base() { return base; }
    public long limit() { return limit; }
    public int factor() { return factor; }
    public static long multiplied(long base, int factor, long limit) {
        return base>0 && factor>=1 && factor<=1_000_000 && base<=limit/factor ? base*factor : 0;
    }
    public boolean apply(ServerPlayer player, int factor) {
        if(applied || !stillValid(player))return false;
        var machine=(CasinoMachineBlockEntity)player.level().getBlockEntity(pos);
        var mod=TeamEconomyMod.get();
        String dimension=player.level().dimension().location().toString();
        if(machine.betBase()!=base || machine.bet()!=multiplied(base,this.factor,limit) || mod.economy().manager().machineBusy(dimension,pos)
                || mod.economy().manager().activeBets().values().stream().anyMatch(s->s.atMachine(dimension,pos)))return false;
        long amount=multiplied(base,factor,Math.min(limit,mod.gambling().maxBet(machine.machineId(),com.evolt.teamecon.team.TeamUtil.walletKey(player.getServer(),player.getUUID()))));
        if(amount<mod.gambling().minBet())return false;
        applied=true;machine.selectFactor(player,amount,factor);return true;
    }
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
    @Override public boolean stillValid(Player player) {
        return player.isAlive() && !player.isSpectator() && player.distanceToSqr(pos.getCenter())<=64
                && player.level().getBlockEntity(pos) instanceof CasinoMachineBlockEntity machine
                && machine.getBlockState().getValue(CasinoMachineBlock.ASSEMBLED);
    }
}
