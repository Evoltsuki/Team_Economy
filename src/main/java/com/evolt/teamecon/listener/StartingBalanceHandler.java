package com.evolt.teamecon.listener;

import com.evolt.teamecon.config.TeConfig;
import com.evolt.teamecon.economy.TeamEconomyManager;
import com.evolt.teamecon.team.TeamUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Grants the configured starting balance the first time a wallet is seen, so a fresh team
 * is not stuck with zero money. One grant per wallet: later members of the same party get
 * nothing extra, and relogging never repeats it.
 */
public final class StartingBalanceHandler {

    private StartingBalanceHandler() {
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        grantGuide(player);
        long starting = TeConfig.ECONOMY.startingBalance.get();
        if (starting <= 0) {
            return;
        }
        TeamEconomyManager manager = TeamEconomyManager.get(player.getServer());
        if (manager == null) {
            return;
        }
        java.util.UUID teamKey = TeamUtil.walletKey(player.getServer(), player.getUUID());
        if (manager.hasWallet(teamKey)) {
            manager.claim(player.getUUID(), "starting_balance");
            return;
        }
        if (!manager.claim(player.getUUID(), "starting_balance")) {
            manager.setBalance(teamKey, 0);
            return;
        }
        manager.setBalance(teamKey, starting);
        String teamName = TeamUtil.displayName(player.getServer(), player.getUUID());
        player.sendSystemMessage(Component.translatable(
                "message.teamecon.starting_balance", teamName, starting));
    }

    public static void grantGuide(ServerPlayer player) {
        TeamEconomyManager manager = TeamEconomyManager.get(player.getServer());
        if (manager != null && manager.claim(player.getUUID(), "welcome_guide_v1")) {
            var book = new net.minecraft.world.item.ItemStack(com.evolt.teamecon.init.ModRegistries.GUIDE_BOOK.get());
            if (!player.getInventory().add(book)) player.drop(book, false);
            player.inventoryMenu.broadcastChanges();
        }
    }
}
