package com.evolt.teamecon.gametest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
final class PortPlayers {
    static ServerPlayer create(GameTestHelper h,String name){
        var profile=new GameProfile(UUID.randomUUID(),name);var server=h.getLevel().getServer();
        var player=new ServerPlayer(server,h.getLevel(),profile,ClientInformation.createDefault());
        // Menu packets use the connection directly, so the inert fixture must model a PLAY connection.
        var connection=new Connection(PacketFlow.SERVERBOUND){
            @Override public net.minecraft.network.ConnectionProtocol getProtocol(){return net.minecraft.network.ConnectionProtocol.PLAY;}
            @Override public void send(Packet<?> packet){}
        };
        player.connection=new ServerGamePacketListenerImpl(server,connection,player,CommonListenerCookie.createInitial(profile,false)){
            @Override public void send(Packet<?> packet){}
        };
        return player;
    }
}
