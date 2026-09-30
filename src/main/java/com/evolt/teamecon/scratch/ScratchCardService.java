package com.evolt.teamecon.scratch;

import com.evolt.teamecon.economy.*;
import com.evolt.teamecon.init.ModRegistries;
import com.evolt.teamecon.team.TeamUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import java.util.Random;
import java.util.UUID;

public final class ScratchCardService {
    private final MinecraftServer server;
    private final TeamEconomyManager manager;
    private final com.evolt.teamecon.gambling.CasinoProgression progression;
    private final Random random=new Random();
    public ScratchCardService(MinecraftServer server,TeamEconomyManager manager){this(server,manager,new com.evolt.teamecon.gambling.CasinoProgression());}
    public ScratchCardService(MinecraftServer server,TeamEconomyManager manager,com.evolt.teamecon.gambling.CasinoProgression progression){
        this.server=server;this.manager=manager;this.progression=progression;
    }
    public com.evolt.teamecon.shop.PurchaseRules.Access access(ServerPlayer player,ScratchKind kind){
        return progression.levelAccess(manager,TeamUtil.walletKey(server,player.getUUID()),progression.cardLevel(kind));
    }
    public boolean enabled(ScratchKind kind){return kind.expectedFactor()<=com.evolt.teamecon.config.TeConfig.GAMBLING.expectedValueCap.get()+1e-9;}
    /** Checks capacity before drawing, so changing inventory cannot reroll prizes for free. */
    public String buy(ServerPlayer player,ScratchKind kind) { return buy(player,kind,1); }
    public String buy(ServerPlayer player,ScratchKind kind,int factor) {
        if(kind==null||!enabled(kind))return "message.teamecon.game_disabled";
        if(!access(player,kind).unlocked())return "casino.teamecon.card_locked";
        UUID wallet=TeamUtil.walletKey(server,player.getUUID());
        if(factor<1||factor>10000)return "scratch.teamecon.stake_invalid";
        long price=MoneyMath.payout(kind.price(),factor);
        if(price>com.evolt.teamecon.gambling.CasinoProgression.cardStakeLimit(manager.casinoLevel(wallet)))return "scratch.teamecon.stake_invalid";
        if(!manager.canAfford(wallet,price))return "command.teamecon.shop_no_funds";
        ItemStack item=new ItemStack(ModRegistries.SCRATCH_CARDS.get(kind).get());
        UUID serial=UUID.randomUUID();ScratchCardItem.stamp(item,serial);
        if(!ItemDelivery.canFit(player.getInventory(),item))return "command.teamecon.shop_no_space";
        int bucket=kind.draw(random);
        ScratchTicket ticket=new ScratchTicket(serial,kind,price,bucket,MoneyMath.payout(price,kind.multiplier(bucket)),random.nextLong());
        if(!manager.debit(wallet,price))return "command.teamecon.shop_no_funds";
        manager.putTicket(ticket);
        manager.appendTransaction(new Transaction(manager.nextTxId(),wallet,player.getUUID(),player.getName().getString(),
                TxType.GAMBLE_LOSS,"scratch:"+kind.id(),1,price,-price,System.currentTimeMillis()));
        ItemDelivery.give(player.getInventory(),item);player.containerMenu.broadcastChanges();
        return "scratch.teamecon.purchased";
    }
    public void open(ServerPlayer player) {
        if(!player.isAlive()||player.isSpectator()||player.containerMenu!=player.inventoryMenu)return;
        ItemStack stack=player.getMainHandItem();UUID id=ScratchCardItem.serial(stack);
        ScratchTicket ticket=id==null?null:manager.ticket(id);
        if(ticket==null||!(stack.getItem() instanceof ScratchCardItem card)||card.kind()!=ticket.kind()) {
            player.displayClientMessage(Component.translatable("scratch.teamecon.invalid"),true);return;
        }
        player.openMenu(new SimpleMenuProvider((container,inventory,p)->new ScratchCardMenu(container,inventory,ticket),
                Component.translatable("item.teamecon.scratch_card_"+ticket.kind().id())),b->ScratchCardMenu.write(b,ticket));
    }
    /** Auto-called by revealing the card. The serialized ticket is removed with the single credit. */
    public long reveal(ServerPlayer player,UUID serial) {
        if(!(player.containerMenu instanceof ScratchCardMenu menu)||!menu.ticket().id().equals(serial))return -1;
        if(menu.settledAmount()>=0)return menu.settledAmount();
        ScratchTicket ticket=manager.ticket(serial);
        if(ticket==null)return -1;
        ItemStack physical=ItemStack.EMPTY;
        for(ItemStack stack:player.getInventory().items)if(serial.equals(ScratchCardItem.serial(stack))){physical=stack;break;}
        if(physical.isEmpty()||!(physical.getItem() instanceof ScratchCardItem card)||card.kind()!=ticket.kind())return -1;
        UUID wallet=TeamUtil.walletKey(server,player.getUUID());
        // The physical ticket can be passed to another player, like any other purchased item.
        manager.removeTicket(serial);physical.shrink(1);
        long amount=manager.credit(wallet,ticket.payout());
        menu.settled(amount);
        manager.appendTransaction(new Transaction(manager.nextTxId(),wallet,player.getUUID(),player.getName().getString(),
                TxType.GAMBLE_WIN,"scratch:"+ticket.kind().id(),1,amount,amount,System.currentTimeMillis()));
        player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();
        return amount;
    }
}
