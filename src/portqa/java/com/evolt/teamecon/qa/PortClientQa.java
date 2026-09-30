package com.evolt.teamecon.qa;

import com.evolt.teamecon.TeamEconomyMod;
import com.evolt.teamecon.casino.*;
import com.evolt.teamecon.client.*;
import com.evolt.teamecon.shop.*;
import com.evolt.teamecon.init.ModRegistries;
import com.evolt.teamecon.network.payloads.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

/** Runs only with -PportQa. Real client/server packets and unedited framebuffer captures. */
@EventBusSubscriber(modid="teamecon",value=Dist.CLIENT)
public final class PortClientQa {
    private record Step(String name,BooleanSupplier ready){}
    private static final Deque<Step> steps=new ArrayDeque<>();
    private static final List<String> passed=new ArrayList<>(),screenshots=new ArrayList<>();
    private static boolean created,planned,finished;
    private static int wait;
    private static String capture;
    private static volatile boolean serverReady,parallelStarted;
    private static final long started=System.nanoTime();
    private static Minecraft mc(){return Minecraft.getInstance();}
    private static Path output(){return mc().gameDirectory.toPath().resolve("port-qa");}
    private static void require(boolean ok,String reason){if(!ok)throw new IllegalStateException(reason);}
    private static void action(String name,Runnable run){steps.add(new Step(name,()->{run.run();return true;}));}
    private static void until(String name,BooleanSupplier ready){steps.add(new Step(name,ready));}
    private static void delay(int ticks){until("wait "+ticks,()->wait>=ticks);}
    private static void shot(String name){delay(5);action("capture "+name,()->capture=name);until("saved "+name,()->screenshots.contains(name));}
    private static void server(Consumer<ServerPlayer> action){
        var server=Objects.requireNonNull(mc().getSingleplayerServer());var id=mc().player.getUUID();
        server.execute(()->{try{action.accept(Objects.requireNonNull(server.getPlayerList().getPlayer(id)));}catch(Throwable e){mc().execute(()->finish(e));}});
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("teamecon.portQa")||finished)return;
        try{
            require((System.nanoTime()-started)/1_000_000_000<240,"Client QA timeout");
            if(!created){
                if(!(mc().screen instanceof TitleScreen)||mc().getOverlay()!=null)return;
                created=true;mc().options.guiScale().set(2);mc().options.pauseOnLostFocus=false;
                mc().getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc().resizeDisplay();
                var settings=new LevelSettings("Port QA",net.minecraft.world.level.GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
                mc().createWorldOpenFlows().createFreshLevel("port-qa-"+System.currentTimeMillis(),settings,new WorldOptions(12345,false,false),
                        registry->registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), new TitleScreen());
                return;
            }
            if(!planned){
                if(mc().player==null||mc().screen!=null||mc().getSingleplayerServer()==null)return;
                planned=true;plan();
            }
            if(steps.isEmpty()){finish(null);return;}
            require(++wait<600,"Timed out: "+steps.peekFirst().name());
            if(steps.peekFirst().ready().getAsBoolean()){passed.add(steps.removeFirst().name());wait=0;}
        }catch(Throwable e){finish(e);}
    }
    private static void plan(){
        action("prepare player and six machines",()->server(p->{
            var mod=TeamEconomyMod.get();var wallet=com.evolt.teamecon.team.TeamUtil.walletKey(p.getServer(),p.getUUID());
            mod.economy().manager().setBalance(wallet,1_000_000);mod.economy().manager().setCasinoLevel(wallet,5);
            var advancement=p.getServer().getAdvancements().get(ResourceLocation.parse("minecraft:end/kill_dragon"));
            for(String criterion:advancement.value().criteria().keySet())p.getAdvancements().award(advancement,criterion);
            p.getInventory().add(new ItemStack(ModRegistries.TERMINAL.get()));
            var world=p.serverLevel();world.setDayTime(6000);world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,p.getServer());
            for(int x=-3;x<36;x++)for(int z=-3;z<14;z++)world.setBlock(new BlockPos(x,63,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
            var blocks=List.of(ModRegistries.SLOT_MACHINE.get(),ModRegistries.ROULETTE_TABLE.get(),ModRegistries.COLOR_WHEEL_TABLE.get(),
                    ModRegistries.PENGUIN_MACHINE.get(),ModRegistries.MULTIPLIER_MACHINE.get(),ModRegistries.HILO_TABLE.get());
            for(int i=0;i<blocks.size();i++){
                var block=blocks.get(i);var pos=new BlockPos(i*5,64,0);var state=block.defaultBlockState().setValue(CasinoMachineBlock.FACING,Direction.SOUTH).setValue(CasinoMachineBlock.ASSEMBLED,true);
                world.setBlock(pos,state,3);block.setPlacedBy(world,pos,state,p,new ItemStack(block));
            }
            var box=ModRegistries.BLIND_BOX_MACHINE.get();var pos=new BlockPos(32,64,0);var state=box.defaultBlockState().setValue(ShopBlock.FACING,Direction.SOUTH);
            world.setBlock(pos,state,3);box.setPlacedBy(world,pos,state,p,new ItemStack(box));
            p.teleportTo(world,13,66,12,180,10);p.getAbilities().flying=true;p.onUpdateAbilities();serverReady=true;
        }));
        until("world ready",()->serverReady);delay(25);shot("machines");
        action("open shop",()->server(p->ShopMenu.open(p,null,true,"items")));
        until("shop catalog arrived",()->mc().screen instanceof ShopScreen&&!ClientShopCache.items().isEmpty());shot("shop");
        action("automatic GUI scale",()->{mc().options.guiScale().set(0);mc().resizeDisplay();});shot("shop-auto");
        action("close shop",()->mc().screen.onClose());delay(5);
        action("open blind box",()->server(p->{p.teleportTo(p.serverLevel(),32.5,64,3,180,10);ShopMenu.open(p,new BlockPos(32,64,0),false,"boxes");}));
        until("both box catalogs arrived",()->mc().screen instanceof BlindBoxScreen&&ClientShopCache.boxes().size()==2);shot("boxes");
        action("buy rare box through client packet",()->ClientPayloadSender.sendToServer(new ShopActionPayload(mc().player.containerMenu.containerId,1000,ShopActionPayload.Kind.BUY_BOX,"rare",1)));
        until("box reward arrived",()->!ClientShopCache.rewards().isEmpty());shot("box-reward");
        action("close boxes",()->mc().screen.onClose());delay(5);
        action("open terminal",()->server(TerminalItem::open));
        until("terminal synchronized",()->mc().screen instanceof CasinoScreen&&CasinoScreenData.last()!=null);shot("terminal");
        action("play terminal through client packet",()->ClientPayloadSender.sendToServer(new CasinoActionPayload(mc().player.containerMenu.containerId,2000,CasinoActionPayload.Action.PLAY,"hilo","high",20)));
        until("terminal result arrived",()->CasinoScreenData.last()!=null&&CasinoScreenData.last().requestId()==2000);shot("terminal-result");
        action("close terminal",()->mc().screen.onClose());delay(5);
        action("start three cabinets",()->server(p->{
            p.teleportTo(p.serverLevel(),6,66,11,180,12);var mod=TeamEconomyMod.get();
            for(int i=0;i<3;i++){
                var pos=new BlockPos(i*5,65,0);var e=(CasinoMachineBlockEntity)p.serverLevel().getBlockEntity(pos);
                var game=mod.gambling().beginMachine(p,pos,e.machineId(),i==1?"red":"",100,false,100);
                require(game!=null,"Parallel cabinet "+i+" failed");e.startExternal(game,100);
            }
            require(mod.economy().manager().pendingMachines().size()==3,"Parallel state missing");parallelStarted=true;
        }));
        until("three cabinets running",()->parallelStarted);shot("parallel-machines");delay(110);
        action("all cabinet payments complete",()->server(p->{require(TeamEconomyMod.get().economy().manager().pendingMachines().isEmpty(),"Pending payment stuck");}));delay(5);
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        if(capture==null||finished)return;
        try{Files.createDirectories(output());try(var image=Screenshot.takeScreenshot(mc().getMainRenderTarget())){image.writeToFile(output().resolve(capture+".png"));}
            screenshots.add(capture);capture=null;
        }catch(Throwable e){finish(e);}
    }
    private static void finish(Throwable error){
        if(finished)return;finished=true;
        try{Files.createDirectories(output());Files.writeString(output().resolve("result.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(
                Map.of("status",error==null?"passed":"failed","detail",error==null?"":error.toString(),"steps",passed,"screenshots",screenshots)));}
        catch(Exception ex){TeamEconomyMod.LOGGER.error("Cannot save QA report",ex);}
        if(error!=null)TeamEconomyMod.LOGGER.error("PORT_QA_FAILED",error);else TeamEconomyMod.LOGGER.info("PORT_QA_PASSED: {} screenshots",screenshots.size());
        mc().stop();
    }
}
