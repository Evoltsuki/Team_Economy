package com.evolt.teamecon.network;
import com.evolt.teamecon.TeamEconomyMod;
import com.evolt.teamecon.network.payloads.*;
import com.evolt.teamecon.shop.*;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.io.IOException;
import java.util.ConcurrentModificationException;

public final class BoxAdminNetwork {
    private BoxAdminNetwork(){}
    public static void register(RegisterPayloadHandlersEvent event){
        event.registrar("3").playToServer(BoxAdminActionPayload.TYPE,BoxAdminActionPayload.STREAM_CODEC,(p,c)->c.enqueueWork(()->{
            if(c.player() instanceof ServerPlayer player)handle(player,p);
        })).playToClient(BoxAdminSyncPayload.TYPE,BoxAdminSyncPayload.STREAM_CODEC,(p,c)->c.enqueueWork(()->com.evolt.teamecon.client.BoxAdminScreen.receive(p)));
    }
    public static void handle(ServerPlayer player,BoxAdminActionPayload p){
        if(!BoxAdminMenu.authorized(player,p.containerId())||p.action()<0||p.action()>3)return;
        var shop=TeamEconomyMod.get().shop();if(shop==null)return;
        var config=shop.boxAdmin();String message="",id=p.id();
        if(!((BoxAdminMenu)player.containerMenu).accept(p.sequence(),player.level().getGameTime(),p.action()!=0))message="busy";
        else if(p.action()==3){
            shop.reloadBlindBoxes(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get());
            syncShops(player);
        }
        else if(p.action()!=0){
            try{
                JsonObject draft=p.action()==2?null:JsonParser.parseString(p.json()).getAsJsonObject();
                config.save(id,draft,p.action()==2,p.revision(),shop.prices());
                id=p.action()==2?"":draft.get("id").getAsString();
                shop.reloadBlindBoxes(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get());
                syncShops(player);
                message=p.action()==2?"deleted":"saved";
            }catch(ConcurrentModificationException ex){message="conflict";}
            catch(IOException ex){message="io_error";TeamEconomyMod.LOGGER.warn("Could not save blind boxes",ex);}
            catch(RuntimeException ex){message="invalid";}
        }
        JsonArray headers=config.headers();
        if(id.isEmpty()&&!headers.isEmpty())id=headers.get(0).getAsJsonObject().get("id").getAsString();
        JsonObject pool=config.pool(id);String json=pool==null?"":pool.toString();
        boolean editable=config.ready()&&json.length()<=24000;
        if(!editable){json="";message="unavailable";}
        PacketDistributor.sendToPlayer(player,new BoxAdminSyncPayload(p.containerId(),p.sequence(),config.revision(),editable,headers.toString(),json,message));
    }
    private static void syncShops(ServerPlayer player){
        for(ServerPlayer member:player.getServer().getPlayerList().getPlayers())if(member.containerMenu instanceof ShopMenu menu){menu.invalidateCatalog();ShopNetwork.sendSync(member);}
    }
}
