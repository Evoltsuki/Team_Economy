package com.evolt.teamecon.gametest;

import com.evolt.teamecon.api.QuestRewards;
import com.evolt.teamecon.economy.*;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.UUID;

@GameTestHolder("teamecon")
public final class QuestRewardsTest {
    @GameTest(template="empty")
    public static void rewardsAddOncePerWalletAndSurviveReload(GameTestHelper h) {
        var manager = new TeamEconomyManager(); var wallet = UUID.randomUUID();
        manager.setBalance(wallet, 40);
        h.assertTrue(QuestRewards.grantToWallet(manager, wallet, "example:first", 100).credited() == 100, "Reward not credited");
        h.assertTrue(manager.getBalance(wallet) == 140, "Reward replaced existing balance");
        h.assertTrue(QuestRewards.grantToWallet(manager, wallet, "example:first", 500).status() == QuestRewards.Status.ALREADY_CLAIMED, "Repeated ID credited twice");
        var restored = TeamEconomyManager.load(manager.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(QuestRewards.grantToWallet(restored, wallet, "example:first", 100).credited() == 0 && restored.getBalance(wallet) == 140, "Reload lost claim");
        h.assertTrue(QuestRewards.grantToWallet(restored, UUID.randomUUID(), "example:first", 100).credited() == 100, "Different team cannot claim");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void legacyBridgeClaimsAndFullWalletsRemainSafe(GameTestHelper h) {
        var manager = new TeamEconomyManager(); var wallet = UUID.randomUUID();
        manager.claim(wallet, "ftbquest-v3/7445429B27FE4CD5");
        h.assertTrue(QuestRewards.grantToWallet(manager, wallet, "7445429b27fe4cd5", 25).credited() == 0, "Bridge claim ignored");
        manager.setBalance(wallet, MoneyMath.MAX_MONEY);
        h.assertTrue(QuestRewards.grantToWallet(manager, wallet, "example:full", 100).status() == QuestRewards.Status.WALLET_FULL, "Full wallet accepted reward");
        h.assertTrue(!manager.hasClaimed(wallet, QuestRewards.claimKey("example:full")), "Failed reward consumed claim");
        manager.setBalance(wallet, 0);
        h.assertTrue(QuestRewards.grantToWallet(manager, wallet, "example:full", 100).credited() == 100, "Retry failed");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void rewardCommandsRequirePermissionAndKeepLegacySyntax(GameTestHelper h) {
        var player = ProgressionTest.player(h, "quest-api-test");
        var commands = player.getServer().getCommands();
        var source = player.createCommandSourceStack();
        String reward = "qatest:" + UUID.randomUUID();
        var manager = com.evolt.teamecon.TeamEconomyMod.get().economy().manager();
        var wallet = com.evolt.teamecon.team.TeamUtil.walletKey(player.getServer(), player.getUUID());
        long before = manager.getBalance(wallet);
        commands.performPrefixedCommand(source.withPermission(0), "teamecon reward " + reward + " 25");
        h.assertTrue(manager.getBalance(wallet) == before, "Unprivileged reward succeeded");
        commands.performPrefixedCommand(source.withPermission(2), "teamecon reward " + reward + " 25");
        h.assertTrue(manager.getBalance(wallet) == before + 25, "Namespaced command failed");
        commands.performPrefixedCommand(source.withPermission(2), "teamecon_quest_reward 7445429B27FE4CD5 25");
        commands.performPrefixedCommand(source.withPermission(2), "teamecon_quest_reward 7445429B27FE4CD5 25");
        h.assertTrue(manager.getBalance(wallet) == before + 50, "Legacy command missing or not idempotent");
        h.succeed();
    }
}
