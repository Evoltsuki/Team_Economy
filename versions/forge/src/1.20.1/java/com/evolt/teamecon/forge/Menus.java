package com.evolt.teamecon.forge;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;
import java.util.function.Consumer;
public final class Menus {
    public static void open(Player player,MenuProvider provider,Consumer<FriendlyByteBuf> data){if(player instanceof ServerPlayer p)net.minecraftforge.network.NetworkHooks.openScreen(p,provider,data);}
    public static void open(Player player,MenuProvider provider,BlockPos pos){open(player,provider,b->b.writeBlockPos(pos));}
    public static void open(Player player,MenuProvider provider){player.openMenu(provider);}
}
