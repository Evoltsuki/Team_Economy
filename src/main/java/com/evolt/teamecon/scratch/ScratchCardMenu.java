package com.evolt.teamecon.scratch;

import com.evolt.teamecon.init.ModRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;

/** A locked serial-number view; closing before reveal keeps the physical card intact. */
public final class ScratchCardMenu extends AbstractContainerMenu {
    private final ScratchTicket ticket;
    private final long openedAt;
    private long settledAmount=-1;
    public ScratchCardMenu(int id, Inventory inventory, RegistryFriendlyByteBuf b) {
        this(id,inventory,new ScratchTicket(b.readUUID(),ScratchKind.byId(b.readUtf(16)),b.readVarLong(),b.readVarInt(),b.readVarLong(),b.readLong()));
    }
    public ScratchCardMenu(int id, Inventory inventory, ScratchTicket ticket) {
        super(ModRegistries.SCRATCH_MENU.get(),id);this.ticket=ticket;this.openedAt=inventory.player.level().getGameTime();
    }
    public ScratchTicket ticket(){return ticket;}
    public long openedAt(){return openedAt;}
    public long settledAmount(){return settledAmount;}
    public void settled(long amount){settledAmount=amount;}
    public static void write(RegistryFriendlyByteBuf b, ScratchTicket t) {
        b.writeUUID(t.id());b.writeUtf(t.kind().id(),16);b.writeVarLong(t.price());b.writeVarInt(t.bucket());b.writeVarLong(t.payout());b.writeLong(t.seed());
    }
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    @Override public boolean stillValid(Player player){return player.isAlive();}
}
