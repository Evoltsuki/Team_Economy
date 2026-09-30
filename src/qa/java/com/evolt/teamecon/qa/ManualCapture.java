package com.evolt.teamecon.qa;

import com.evolt.teamecon.TeamEconomyMod;
import com.evolt.teamecon.init.ModRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Prepares the dedicated local gallery once, then gives control to the photographer. */
@EventBusSubscriber(modid = "teamecon", value = Dist.CLIENT)
public final class ManualCapture {
    private static boolean prepared;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("teamecon.manualCapture") || prepared) return;
        var client = Minecraft.getInstance();
        var server = client.getSingleplayerServer();
        if (client.player == null || client.level == null || server == null || client.getOverlay() != null) return;
        prepared = true;
        client.options.hideGui = false;
        client.options.guiScale().set(2);
        client.options.fov().set(60);
        client.options.bobView().set(false);
        client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        client.resizeDisplay();
        client.options.save();
        var playerId = client.player.getUUID();
        server.execute(() -> {
            try {
                var player = server.getPlayerList().getPlayer(playerId);
                if (player == null) throw new IllegalStateException("Gallery player disconnected during setup");
                player.setGameMode(GameType.CREATIVE);
                var level = player.serverLevel();
                level.setDayTime(6000);
                level.setWeatherParameters(100000, 0, false, false);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
                var source = player.createCommandSourceStack().withPermission(4).withSuppressedOutput();
                server.getCommands().performPrefixedCommand(source, "teamecon admin unlock");
                server.getCommands().performPrefixedCommand(source, "teamecon admin balance 1000000");
                player.getInventory().clearContent();
                player.getInventory().items.set(0, new ItemStack(ModRegistries.GUIDE_BOOK.get()));
                player.getInventory().items.set(1, new ItemStack(ModRegistries.TERMINAL.get()));
                player.getInventory().items.set(9, new ItemStack(Items.IRON_INGOT, 32));
                player.getInventory().items.set(10, new ItemStack(Items.COPPER_INGOT, 32));
                player.getInventory().items.set(11, new ItemStack(Items.GOLD_INGOT, 8));
                player.getInventory().items.set(12, new ItemStack(Items.COAL, 64));
                player.getInventory().items.set(13, new ItemStack(Items.OAK_LOG, 64));
                player.getInventory().items.set(14, new ItemStack(Items.WHEAT, 64));
                player.getInventory().items.set(15, new ItemStack(Items.COBBLESTONE, 64));
                player.getInventory().selected = 2;
                player.inventoryMenu.sendAllDataToRemote();
                player.connection.send(new ClientboundSetCarriedItemPacket(2));
                player.setNoGravity(false);
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
                player.teleportTo(level, 6.5, 64, 7.4, 180, 1);
                client.execute(() -> client.getToasts().clear());
                TeamEconomyMod.LOGGER.info("MANUAL_CAPTURE_READY: white gallery; creative; level 5; guide in slot 1; terminal in slot 2; user controls camera and F2 screenshots");
            } catch (Throwable error) {
                TeamEconomyMod.LOGGER.error("MANUAL_CAPTURE_FAILED", error);
            }
        });
    }
}
