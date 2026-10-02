package com.evolt.teamecon.forge;
import com.evolt.teamecon.network.ModNetwork;
import com.evolt.teamecon.network.ShopNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Optional;
import java.util.function.BiConsumer;

public final class ForgeNetwork {
    private static final String PROTOCOL="5";
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("teamecon","main"),()->PROTOCOL,PROTOCOL::equals,PROTOCOL::equals);
    private static int nextId;
    public static void init(){var registrar=new PayloadRegistrar();ModNetwork.register(registrar);ShopNetwork.register(registrar);}
    static <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type,StreamCodec<FriendlyByteBuf,T> codec,BiConsumer<T,PayloadContext> handler,boolean serverbound){
        CHANNEL.registerMessage(nextId++,type.messageClass(),(p,b)->codec.encode(b,p),codec::decode,(p,supplier)->{
            var context=supplier.get();
            // Only serverbound handlers need a player. Client handlers never load a
            // Minecraft client class on a dedicated server, even during registration.
            handler.accept(p,new PayloadContext(context.getSender(),work->context.enqueueWork(work)));
            context.setPacketHandled(true);
        },Optional.of(serverbound?NetworkDirection.PLAY_TO_SERVER:NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void sendToPlayer(ServerPlayer player,CustomPacketPayload payload){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),payload);}
    public static void sendToAllPlayers(CustomPacketPayload payload){CHANNEL.send(PacketDistributor.ALL.noArg(),payload);}
    public static void sendToServer(CustomPacketPayload payload){CHANNEL.sendToServer(payload);}
}
