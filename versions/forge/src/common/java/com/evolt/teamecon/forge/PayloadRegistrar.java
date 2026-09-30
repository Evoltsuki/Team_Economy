package com.evolt.teamecon.forge;
import net.minecraft.network.FriendlyByteBuf;
import java.util.function.BiConsumer;
public final class PayloadRegistrar {
    public PayloadRegistrar registrar(String version) { return this; }
    public <T extends CustomPacketPayload> PayloadRegistrar playToServer(CustomPacketPayload.Type<T> type, StreamCodec<FriendlyByteBuf,T> codec, BiConsumer<T,PayloadContext> handler) {
        ForgeNetwork.register(type,codec,handler,true);return this;
    }
    public <T extends CustomPacketPayload> PayloadRegistrar playToClient(CustomPacketPayload.Type<T> type, StreamCodec<FriendlyByteBuf,T> codec, BiConsumer<T,PayloadContext> handler) {
        ForgeNetwork.register(type,codec,handler,false);return this;
    }
}
