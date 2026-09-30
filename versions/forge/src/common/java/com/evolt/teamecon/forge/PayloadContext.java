package com.evolt.teamecon.forge;
import net.minecraft.world.entity.player.Player;
import java.util.function.Consumer;
public record PayloadContext(Player player, Consumer<Runnable> executor) {
    public void enqueueWork(Runnable work) { executor.accept(work); }
}
