package com.evolt.teamecon.network;
import com.evolt.teamecon.TeamEconomyMod;
import com.evolt.teamecon.team.TeamUtil;
import com.evolt.teamecon.network.payloads.TeamBoardPayload;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
@EventBusSubscriber(modid="teamecon")
public final class TeamBoardNetwork {
    public static String snapshot(ServerPlayer p){
        var manager=TeamEconomyMod.get().economy().manager();var wallet=TeamUtil.walletKey(p.getServer(),p.getUUID());var stats=manager.stats(wallet);
        JsonObject root=new JsonObject();root.addProperty("wallet",wallet.toString());root.addProperty("team",ModNetwork.walletName(p));root.addProperty("balance",manager.getBalance(wallet));
        JsonArray rows=new JsonArray();
        TeamUtil.members(p).stream().sorted(java.util.Comparator.<java.util.UUID>comparingLong(id->stats.earningsByPlayer().getOrDefault(id,0L)).reversed().thenComparing(Object::toString)).limit(512).forEach(id->{
            JsonObject row=new JsonObject();var online=p.getServer().getPlayerList().getPlayer(id);
            var cache=p.getServer().getProfileCache();
            String name=online!=null?online.getGameProfile().getName():id.equals(p.getUUID())?p.getGameProfile().getName()
                    :cache==null?stats.playerName(id):cache.get(id).map(com.mojang.authlib.GameProfile::getName).orElse(stats.playerName(id));
            row.addProperty("name",name);
            row.addProperty("earned",stats.earningsByPlayer().getOrDefault(id,0L));row.addProperty("online",online!=null);rows.add(row);
        });
        root.add("members",rows);return root.toString();
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(event.getServer().getTickCount()%20!=0||TeamEconomyMod.get().economy()==null)return;
        for(ServerPlayer p:event.getServer().getPlayerList().getPlayers())PacketDistributor.sendToPlayer(p,new TeamBoardPayload(snapshot(p)));
    }
}
