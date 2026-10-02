package com.evolt.teamecon.forge;
import com.evolt.teamecon.network.ModNetwork;
import com.evolt.teamecon.network.ShopNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;
import java.util.function.BiConsumer;

public final class ForgeNetwork {
    private static final SimpleChannel CHANNEL=ChannelBuilder.named("teamecon:main").networkProtocolVersion(3).simpleChannel();
    private static int nextId;
    public static void init(){var registrar=new PayloadRegistrar();ModNetwork.register(registrar);ShopNetwork.register(registrar);CHANNEL.build();}
    static <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type,StreamCodec<FriendlyByteBuf,T> codec,BiConsumer<T,PayloadContext> handler,boolean serverbound){
        CHANNEL.messageBuilder(type.messageClass(),nextId++,serverbound?NetworkDirection.PLAY_TO_SERVER:NetworkDirection.PLAY_TO_CLIENT)
            .encoder((p,b)->codec.encode(b,p)).decoder(codec::decode).consumerNetworkThread((p,context)->{
                handler.accept(p,new PayloadContext(context.getSender(),work->context.enqueueWork(work)));
                context.setPacketHandled(true);
            }).add();
    }
    public static void sendToPlayer(ServerPlayer player,CustomPacketPayload payload){CHANNEL.send(payload,PacketDistributor.PLAYER.with(player));}
    public static void sendToAllPlayers(CustomPacketPayload payload){CHANNEL.send(payload,PacketDistributor.ALL.noArg());}
    public static void sendToServer(CustomPacketPayload payload){CHANNEL.send(payload,PacketDistributor.SERVER.noArg());}
}
