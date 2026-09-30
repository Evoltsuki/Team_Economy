package com.evolt.teamecon.gametest;

import com.evolt.teamecon.TeamEconomyMod;
import com.evolt.teamecon.economy.TeamEconomyManager;
import com.evolt.teamecon.gambling.CasinoProgression;
import com.evolt.teamecon.shop.PurchaseRules;
import com.evolt.teamecon.team.TeamUtil;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder("teamecon")
public final class AdminUnlockTest {
    private static int earnedCriteria(ServerPlayer player) {
        int count = 0;
        for (var advancement : player.getServer().getAdvancements().getAllAdvancements())
            for (String ignored : player.getAdvancements().getOrStartProgress(advancement).getCompletedCriteria()) count++;
        return count;
    }

    @GameTest(template="empty")
    public static void unlockAndLegacyAliasNeverAwardOrRevokeVanillaCriteria(GameTestHelper h) throws Exception {
        var p = ProgressionTest.player(h,"quiet-unlock");
        var manager = TeamEconomyMod.get().economy().manager();
        var wallet = TeamUtil.walletKey(p.getServer(),p.getUUID());
        var dispatcher = p.getServer().getCommands().getDispatcher();
        var source = p.createCommandSourceStack().withPermission(2);
        // Include existing progress: unlocking must neither grant nor revoke it.
        var root = p.getServer().getAdvancements().get(ResourceLocation.parse("minecraft:story/root"));
        var progress = p.getAdvancements().getOrStartProgress(root);
        progress.grantProgress(progress.getRemainingCriteria().iterator().next());
        int before = earnedCriteria(p);
        manager.setBalance(wallet,1234);
        h.assertTrue(dispatcher.execute("teamecon admin unlock",source)==1,"Unlock command failed");
        h.assertTrue(manager.casinoLevel(wallet)==5 && manager.bypassesProgression(p.getUUID()),"Missing mod-only permissions");
        h.assertTrue(earnedCriteria(p)==before,"Unlock changed vanilla advancement criteria");
        h.assertTrue(TeamEconomyMod.get().casinoProgression().terminalAccess(p,manager).unlocked(),"Terminal still locked");
        dispatcher.execute("teamecon admin level 2",source);
        h.assertTrue(manager.bypassesProgression(p.getUUID()),"Level command unexpectedly reset personal exemption");
        dispatcher.execute("teamecon admin bypass false",source);
        h.assertTrue(!manager.bypassesProgression(p.getUUID()),"Exemption cannot be revoked");
        dispatcher.execute("teamecon admin advancements",source);
        h.assertTrue(manager.casinoLevel(wallet)==2 && manager.bypassesProgression(p.getUUID()),"Legacy alias changed level");
        h.assertTrue(earnedCriteria(p)==before && manager.getBalance(wallet)==1234,"Admin commands changed advancements or money");
        h.assertTrue(dispatcher.execute("teamecon admin status",source)==1,"Status failed");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void personalExemptionSurvivesReloadAndKeepsTradeBans(GameTestHelper h) {
        var p = ProgressionTest.player(h,"bypass-owner");
        var other = ProgressionTest.player(h,"bypass-other");
        var wallet = TeamUtil.walletKey(p.getServer(),p.getUUID());
        var manager = new TeamEconomyManager();
        h.assertTrue(!manager.bypassesProgression(p.getUUID()),"Fresh players bypass progression");
        manager.setCasinoLevel(wallet,5);
        manager.setProgressionBypass(p.getUUID(),true);
        manager = TeamEconomyManager.load(manager.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        var rules = new PurchaseRules(p.getServer(),manager);
        var casino = new CasinoProgression();
        h.assertTrue(rules.item(p,"minecraft:diamond").unlocked(),"Saved bypass missed item gate");
        h.assertTrue(rules.enchantment(p,"minecraft:mending").unlocked(),"Saved bypass missed book gates");
        h.assertTrue(casino.terminalAccess(p,manager).unlocked(),"Saved bypass missed terminal gate");
        h.assertTrue(!rules.item(other,"minecraft:diamond").unlocked(),"Personal bypass leaked to another player");
        h.assertTrue(!rules.item(p,"minecraft:elytra").unlocked() && !rules.item(p,"example:diamond").unlocked(),"Bypass disabled trade bans");
        manager.setProgressionBypass(p.getUUID(),false);
        h.assertTrue(!rules.item(p,"minecraft:diamond").unlocked() && !casino.terminalAccess(p,manager).unlocked(),"Revoke failed to restore gates");
        manager = TeamEconomyManager.load(manager.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(!manager.bypassesProgression(p.getUUID()) && manager.casinoLevel(wallet)==5,"Revocation not saved or changed wallet level");
        h.assertTrue(!TeamEconomyManager.load(new CompoundTag(),h.getLevel().registryAccess()).bypassesProgression(p.getUUID()),"Old saves default to bypass");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void nonOperatorsCannotChangeModProgression(GameTestHelper h) throws Exception {
        var p = ProgressionTest.player(h,"bypass-denied");
        var manager = TeamEconomyMod.get().economy().manager();
        var wallet = TeamUtil.walletKey(p.getServer(),p.getUUID());
        var source = p.createCommandSourceStack().withPermission(0);
        var dispatcher = p.getServer().getCommands().getDispatcher();
        for (String command : new String[]{"unlock","bypass true","bypass false","advancements"}) {
            boolean denied=false;
            try { dispatcher.execute("teamecon admin " + command,source); }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { denied=true; }
            h.assertTrue(denied,"Non-OP executed " + command);
        }
        h.assertTrue(manager.casinoLevel(wallet)==1 && !manager.bypassesProgression(p.getUUID()),"Denied command changed permissions");
        h.assertTrue(earnedCriteria(p)==0,"Denied command awarded progress");
        h.succeed();
    }
}
