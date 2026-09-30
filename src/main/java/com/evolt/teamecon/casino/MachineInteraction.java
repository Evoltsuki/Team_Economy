package com.evolt.teamecon.casino;

import com.evolt.teamecon.TeamEconomyMod;
import com.evolt.teamecon.gambling.*;
import com.evolt.teamecon.team.TeamUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.List;

/** Native block-hit controls: no container or casino GUI is opened for a full-size machine. */
@EventBusSubscriber(modid="teamecon")
public final class MachineInteraction {
    public enum Control { NONE, MINUS_100, MINUS_10, PLUS_10, PLUS_100, BET_MULTIPLIER, RED, BLACK, GREEN, HIGH, LOW, START, CASH_OUT, WHEEL }
    private MachineInteraction(){}
    public static int duration(String game) {
        return switch(game){case "slots"->48;case "roulette","color_wheel"->70;case "penguin"->14;default->24;};
    }
    public static double[] coordinates(CasinoMachineBlockEntity machine,BlockHitResult hit) {
        BlockPos base=machine.getBlockPos().below();Direction front=machine.getBlockState().getValue(CasinoMachineBlock.FACING);
        Direction right=front.getCounterClockWise(),back=front.getOpposite();
        double dx=hit.getLocation().x-base.getX()-.5,dz=hit.getLocation().z-base.getZ()-.5;
        return new double[]{.5+dx*right.getStepX()+dz*right.getStepZ(),hit.getLocation().y-base.getY(),.5+dx*back.getStepX()+dz*back.getStepZ()};
    }
    public static Control controlAt(CasinoMachineBlockEntity machine,BlockHitResult hit) {
        double[] p=coordinates(machine,hit);var block=(CasinoMachineBlock)machine.getBlockState().getBlock();
        double width=MachineLayout.of(block.game()).width();
        if(block.game()==GameType.ROULETTE && hit.getDirection()==Direction.UP && p[1]>.76 && p[2]>.45)return Control.WHEEL;
        p[1]-=MachineLayout.controlsOffset(block.game());
        if(p[2]>.45||p[0]<.14||p[0]>width-.14)return Control.NONE;
        if(p[1]>=.54&&p[1]<=.78){int index=(int)((p[0]-.14)/(width-.28)*5);return Control.values()[1+Math.clamp(index,0,4)];}
        if(p[1]>=.81&&p[1]<=1.02){
            if(block.game()==GameType.ROULETTE){int index=Math.clamp((int)((p[0]-.14)/(width-.28)*3),0,2);return new Control[]{Control.RED,Control.BLACK,Control.GREEN}[index];}
            if(block.game()==GameType.HILO)return p[0]<width/2?Control.HIGH:Control.LOW;
        }
        boolean run=block.game()==GameType.MULTIPLIER||block.game()==GameType.PENGUIN;
        double startY=block.game()==GameType.ROULETTE?1.02:1.27;
        if(p[1]>=startY&&p[1]<=startY+.24)return !run||p[0]<width*.64?Control.START:Control.CASH_OUT;
        return Control.NONE;
    }
    public static List<String> choices(String game) {
        var mod=TeamEconomyMod.get();
        return switch(game){
            case "roulette" -> java.util.stream.Stream.concat(java.util.stream.Stream.of("red","black","odd","even","low","high"),
                    java.util.stream.IntStream.rangeClosed(0,36).mapToObj(i->"number:"+i)).toList();
            case "penguin" -> List.of("jump");
            case "hilo" -> List.of("high","low");
            default -> List.of("");
        };
    }
    public static void use(Level level,BlockPos master,Player player,BlockHitResult hit) {
        if(level.isClientSide||!(player instanceof ServerPlayer p)||p.isSpectator()||!p.isAlive()
                ||!(level.getBlockEntity(master) instanceof CasinoMachineBlockEntity machine))return;
        // Vanilla validates the hit block; wide cabinets additionally bound the complete control surface.
        if(player.distanceToSqr(hit.getLocation())>64)return;
        var mod=TeamEconomyMod.get();var games=mod.gambling();
        Control control=controlAt(machine,hit);
        if(games==null||(control!=Control.CASH_OUT&&!machine.controlAllowed()))return;
        var manager=mod.economy().manager();
        if(manager.machineBusy(level.dimension().location().toString(),master)){
            p.displayClientMessage(Component.translatable("machine.teamecon.running"),true);return;
        }
        String dimension=level.dimension().location().toString();
        for(var entry:manager.activeBets().entrySet())if(!entry.getKey().player().equals(p.getUUID())&&entry.getValue().atMachine(dimension,master)){
            p.displayClientMessage(Component.translatable("machine.teamecon.occupied",machine.operatorName()),true);return;
        }
        long cap=Math.max(games.minBet(),games.maxBet(machine.machineId(),com.evolt.teamecon.team.TeamUtil.walletKey(p.getServer(),p.getUUID())));
        long bet=Math.clamp(machine.bet(),games.minBet(),cap);
        List<String> options=choices(machine.machineId());
        String choice=options.contains(machine.choice())?machine.choice():options.isEmpty()?"":options.getFirst();
        var key=SessionKey.machine(p.getUUID(),dimension,master);
        var active=games.sessionOf(key);
        if(active!=null&&active.atMachine(dimension,master)&&control!=Control.START&&control!=Control.CASH_OUT)return;
        switch(control){
            case MINUS_100,MINUS_10,PLUS_10,PLUS_100 -> {
                long delta=switch(control){case MINUS_100->-100;case MINUS_10->-10;case PLUS_10->10;default->100;};
                if(p.isShiftKeyDown())delta*=10;
                bet=Math.clamp(bet+delta,games.minBet(),cap);
                machine.select(p,bet,choice);p.displayClientMessage(Component.translatable("machine.teamecon.bet",bet),true);
            }
            case BET_MULTIPLIER -> {
                machine.select(p,bet,choice);long base=machine.betBase();int factor=machine.betFactor();
                p.openMenu(new net.minecraft.world.SimpleMenuProvider((id,inv,owner)->new MachineBetMenu(id,inv,master,base,cap,factor),
                        Component.translatable("machine.teamecon.bet_input.title")),buf->{buf.writeBlockPos(master);buf.writeVarLong(base);buf.writeVarLong(cap);buf.writeVarInt(factor);});
            }
            case RED,BLACK,GREEN,HIGH,LOW -> machine.select(p,bet,switch(control){
                case RED->"red";case BLACK->"black";case GREEN->"number:0";case HIGH->"high";default->"low";
            });
            case WHEEL -> {
                double[] xyz=coordinates(machine,hit);double angle=Math.atan2(1.5-xyz[2],xyz[0]-1.5);
                int pocket=Math.floorMod((int)Math.floor(angle/(Math.PI*2)*37),37);
                machine.select(p,bet,"number:"+RouletteGame.WHEEL[pocket]);
            }
            case START,CASH_OUT -> {
                if(control==Control.CASH_OUT){
                    if(active==null||!active.atMachine(dimension,master)){
                        p.displayClientMessage(Component.translatable("message.teamecon.round_no_session"),true);return;
                    }
                    var paid=games.cashOut(key,TeamUtil.walletKey(p.getServer(),p.getUUID()),p.getName().getString(),machine.machineId());
                    if(paid.accepted()){
                        machine.settled(paid);machine.refreshPlayer(p);
                        p.displayClientMessage(Component.translatable(paid.status()==GamblingService.Status.BUSTED?"machine.teamecon.lost":"machine.teamecon.paid",
                                paid.status()==GamblingService.Status.BUSTED?active.stake():paid.payout()),true);
                    }
                    return;
                }
                if(control==Control.START){
                    var access=mod.casinoProgression().gameAccess(manager,TeamUtil.walletKey(p.getServer(),p.getUUID()),machine.machineId());
                    if(!access.unlocked()){
                        p.displayClientMessage(com.evolt.teamecon.shop.PurchaseRules.describe(access.reason()),true);return;
                    }
                    if(!games.enabled(machine.machineId())){
                        p.displayClientMessage(Component.translatable("message.teamecon.game_disabled"),true);return;
                    }
                }
                machine.select(p,bet,choice);
                if(machine.machineId().equals("multiplier")){
                    var result=games.startCrashMachine(p,master,bet);
                    if(result.accepted()){
                        machine.startLive();if(result.status()==GamblingService.Status.BUSTED)machine.settled(result);machine.refreshPlayer(p);
                        p.displayClientMessage(Component.translatable(result.status()==GamblingService.Status.BUSTED?"machine.teamecon.lost":"machine.teamecon.crash_live",bet),true);
                    }else p.displayClientMessage(Component.translatable("machine.teamecon.cannot_start"),true);
                    return;
                }
                int duration=duration(machine.machineId());
                PendingMachineGame pending=games.beginMachine(p,master,machine.machineId(),choice,bet,false,duration);
                if(pending==null){
                    var run=games.sessionOf(key);
                    String messageKey=run!=null&&!run.wallet().equals(TeamUtil.walletKey(p.getServer(),p.getUUID()))?"message.teamecon.team_changed"
                            :manager.pendingMachine(key)!=null?"message.teamecon.busy"
                            :control==Control.CASH_OUT?"message.teamecon.round_no_session":"machine.teamecon.cannot_start";
                    p.displayClientMessage(Component.translatable(messageKey),true);return;
                }
                machine.startExternal(pending,duration);machine.refreshPlayer(p);
                p.displayClientMessage(Component.translatable("machine.teamecon.running"),true);
            }
            default -> {
                machine.select(p,bet,choice);p.displayClientMessage(Component.translatable("machine.teamecon.hint"),true);
            }
        }
    }
    /** Completes even if the owner disconnected or the entire cabinet was broken/unloaded. */
    @SubscribeEvent public static void settle(ServerTickEvent.Post event) {
        var mod=TeamEconomyMod.get();if(mod==null||mod.economy()==null||mod.gambling()==null)return;
        long now=event.getServer().overworld().getGameTime();
        for(var entry:mod.economy().manager().activeBets().entrySet()){
            var run=entry.getValue();if(!run.liveCrash())continue;
            var player=event.getServer().getPlayerList().getPlayer(entry.getKey().player());
            var result=mod.gambling().tickCrash(entry.getKey(),player==null?"":player.getName().getString(),now);
            boolean ended=result.status()==GamblingService.Status.BUSTED||result.status()==GamblingService.Status.CASHED_OUT;
            if(run.worldBound()){
                var world=event.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(run.machineDimension())));
                if(world!=null&&world.hasChunkAt(run.machinePos())&&world.getBlockEntity(run.machinePos()) instanceof CasinoMachineBlockEntity machine){
                    if(ended)machine.settled(result);
                    if(player!=null&&(ended||now%2==0))machine.refreshPlayer(player);
                }
            }
            if(player!=null){
                if(!run.worldBound()&&(ended||now%2==0)&&player.containerMenu instanceof CasinoMenu menu)
                    com.evolt.teamecon.network.ModNetwork.sendSync(player,menu.lastRequest(),"multiplier",
                            ended?result.status()==GamblingService.Status.BUSTED?"message.teamecon.round_busted":"message.teamecon.cashed_out":"",
                            ended?String.valueOf(result.status()==GamblingService.Status.BUSTED?run.stake():result.payout()):"",ended?"crash_end":"");
                if(ended)player.displayClientMessage(Component.translatable(result.status()==GamblingService.Status.BUSTED?"machine.teamecon.lost":"machine.teamecon.paid",
                        result.status()==GamblingService.Status.BUSTED?run.stake():result.payout()),true);
            }
        }
        for(PendingMachineGame pending:mod.economy().manager().pendingMachines()){
            if(now<pending.dueTick())continue;
            var result=mod.gambling().settleMachine(SessionKey.of(pending),now);
            var level=event.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(pending.dimension())));
            CasinoMachineBlockEntity machine=level!=null&&level.hasChunkAt(pending.pos())
                    &&level.getBlockEntity(pending.pos()) instanceof CasinoMachineBlockEntity found&&found.machineId().equals(pending.game())?found:null;
            if(machine!=null)machine.settled(result);
            ServerPlayer player=event.getServer().getPlayerList().getPlayer(pending.player());
            if(player!=null){
                Component text=result.status()==GamblingService.Status.ROUND_WON?Component.translatable("machine.teamecon.round",String.format(java.util.Locale.ROOT,"%.2f",result.multiplier()))
                        :result.status()==GamblingService.Status.BUSTED?Component.translatable("machine.teamecon.lost",pending.stake())
                        :Component.translatable("machine.teamecon.paid",result.payout());
                player.displayClientMessage(text,true);
            }
            if(pending.kind()==PendingMachineGame.Kind.ROUND && (player==null||machine==null||player.level()!=level||player.distanceToSqr(pending.pos().getCenter())>256)){
                var paid=mod.gambling().cashOut(SessionKey.of(pending),pending.wallet(),pending.playerName(),pending.game());
                if(machine!=null&&paid.accepted())machine.settled(paid);
                if(player!=null&&paid.accepted())player.displayClientMessage(Component.translatable("machine.teamecon.auto_paid",paid.payout()),true);
            }
            if(machine!=null&&player!=null)machine.refreshPlayer(player);
        }
        if(event.getServer().getTickCount()%20!=0)return;
        // Bound runs recover even when no block entity is ticking (unload, break, dimension change or logout).
        for(var entry:mod.economy().manager().activeBets().entrySet()){
            var run=entry.getValue();if(run.liveCrash()||mod.economy().manager().pendingMachine(entry.getKey())!=null)continue;
            if(!run.worldBound()){
                var owner=event.getServer().getPlayerList().getPlayer(entry.getKey().player());
                var wallet=owner==null?run.wallet():TeamUtil.walletKey(event.getServer(),entry.getKey().player());
                if(owner==null||!wallet.equals(run.wallet())||!mod.casinoProgression().gameAccess(mod.economy().manager(),wallet,run.game()).unlocked()){
                    var paid=mod.gambling().cashOut(entry.getKey(),wallet,owner==null?"":owner.getName().getString(),run.game());
                    if(owner!=null&&paid.accepted())owner.displayClientMessage(Component.translatable("machine.teamecon.auto_paid",paid.payout()),true);
                }
                continue;
            }
            var level=event.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(run.machineDimension())));
            var player=event.getServer().getPlayerList().getPlayer(entry.getKey().player());
            boolean present=level!=null&&level.hasChunkAt(run.machinePos())&&level.getBlockEntity(run.machinePos()) instanceof CasinoMachineBlockEntity;
            if(!present||player==null||player.level()!=level||player.distanceToSqr(run.machinePos().getCenter())>256){
                var paid=mod.gambling().cashOut(entry.getKey(),run.wallet(),player==null?"":player.getName().getString(),run.game());
                if(player!=null)player.displayClientMessage(Component.translatable("machine.teamecon.auto_paid",paid.payout()),true);
            }
        }
    }
    public static void tick(CasinoMachineBlockEntity machine) {
        if(!machine.getBlockState().getValue(CasinoMachineBlock.ASSEMBLED)||machine.getLevel()==null)return;
        var level=machine.getLevel();var mod=TeamEconomyMod.get();if(mod.economy()==null)return;
        if(machine.spinTicks()>0&&machine.spinTicks()%10==0)level.playSound(null,machine.getBlockPos(),SoundEvents.UI_BUTTON_CLICK.value(),SoundSource.BLOCKS,.15F,.7F+machine.progress(0)*.6F);
        if(machine.machineId().equals("penguin")&&!machine.won()&&machine.spinTicks()==Math.round(duration("penguin")*.28F))
            level.playSound(null,machine.getBlockPos(),SoundEvents.GLASS_BREAK,SoundSource.BLOCKS,.75F,1.3F);
        if(level.getGameTime()%20!=0||machine.operator()==null)return;
        var player=level.getServer().getPlayerList().getPlayer(machine.operator());
        if(player!=null)machine.refreshPlayer(player);
    }
}
