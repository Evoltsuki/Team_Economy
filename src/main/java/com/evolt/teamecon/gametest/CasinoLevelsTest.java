package com.evolt.teamecon.gametest;

import com.evolt.teamecon.TeamEconomyMod;
import com.evolt.teamecon.casino.CasinoMenu;
import com.evolt.teamecon.economy.*;
import com.evolt.teamecon.gambling.*;
import com.evolt.teamecon.init.ModRegistries;
import com.evolt.teamecon.price.PriceService;
import com.evolt.teamecon.scratch.*;
import com.evolt.teamecon.shop.ShopService;
import com.evolt.teamecon.team.TeamUtil;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.nio.file.Files;
import java.util.UUID;

@GameTestHolder("teamecon")
public final class CasinoLevelsTest {
    private CasinoLevelsTest() {}
    /** Fixture only: buy the real levels, then restore funds expected by older economy tests. */
    public static void unlock(TeamEconomyManager manager, UUID wallet, int level) {
        long before = manager.getBalance(wallet); manager.setBalance(wallet, 1_000_000);
        var rules = new CasinoProgression();
        for (int next = manager.casinoLevel(wallet) + 1; next <= level; next++)
            if (!manager.upgradeCasinoLevel(wallet, next, rules.levelCost(next))) throw new IllegalStateException("Fixture upgrade failed");
        manager.setBalance(wallet, before);
    }
    private static ServerPlayer player(GameTestHelper h) { return ProgressionTest.player(h, "casino-levels"); }
    private static UUID wallet(ServerPlayer p) { return TeamUtil.walletKey(p.getServer(), p.getUUID()); }

    @GameTest(template="empty")
    public static void upgradesChargeOnceCannotSkipAndPersist(GameTestHelper h) {
        var p = player(h); var m = new TeamEconomyManager(); var wallet = wallet(p);
        var shop = new ShopService(p.getServer(), m, TeamEconomyMod.get().prices());
        m.setBalance(wallet, 1199);
        h.assertTrue(shop.upgradeLevel(p, 2).outcome() == ShopService.Outcome.NO_FUNDS && m.casinoLevel(wallet) == 1 && m.getBalance(wallet) == 1199, "Unfunded level purchase mutated state");
        m.setBalance(wallet, 300000);
        h.assertTrue(shop.upgradeLevel(p, 3).outcome() != ShopService.Outcome.OK && m.getBalance(wallet) == 300000, "Skipped an unpaid level");
        h.assertTrue(shop.upgradeLevel(p, 2).outcome() == ShopService.Outcome.OK && m.getBalance(wallet) == 298800, "Wrong level price");
        h.assertTrue(shop.upgradeLevel(p, 2).outcome() != ShopService.Outcome.OK && m.getBalance(wallet) == 298800 && m.ledger(wallet).size() == 1, "Duplicate upgrade charged again");
        var loaded = TeamEconomyManager.load(m.save(new CompoundTag(), h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertTrue(loaded.casinoLevel(wallet) == 2 && loaded.getBalance(wallet) == 298800, "Level lost on reload");
        h.assertTrue(loaded.casinoLevel(UUID.randomUUID()) == 1, "New wallets inherit an unrelated level");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void borrowingAdvancedMachinesAndCardsCannotBypassLevel(GameTestHelper h) {
        var p = player(h); var m = new TeamEconomyManager(); var wallet = wallet(p); m.setBalance(wallet, 1000000);
        var games = new GamblingService(p.getServer(), m);
        h.assertTrue(games.beginMachine(p, p.blockPosition(), "color_wheel", "", 100, false, 140) == null, "Borrowed advanced machine was playable");
        h.assertTrue(games.playInstant(p.getUUID(), wallet, "test", 100, "roulette", "red").status() == GamblingService.Status.LEVEL_LOCKED, "GUI bypassed machine level");
        h.assertTrue(games.startBet(p.getUUID(), wallet, "test", 100, "penguin").status() == GamblingService.Status.LEVEL_LOCKED, "Run start bypassed level");
        var cards = new ScratchCardService(p.getServer(), m);
        h.assertTrue(cards.buy(p, ScratchKind.CROWN).equals("casino.teamecon.card_locked"), "Late ticket sold to a starter");
        h.assertTrue(m.getBalance(wallet) == 1000000 && m.ledger(wallet).isEmpty(), "Rejected games deducted funds");
        h.assertTrue(cards.buy(p, ScratchKind.MATCH).equals("scratch.teamecon.purchased"), "Starter ticket requires a late terminal");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void earlyStakeAndStreakCapsAreEnforcedAndOldFundsCanBeReleased(GameTestHelper h) {
        var p = player(h); var m = new TeamEconomyManager(); var wallet = wallet(p); m.setBalance(wallet, 10000);
        var games = new GamblingService(p.getServer(), m);
        h.assertTrue(games.playInstant(p.getUUID(), wallet, "test", games.maxBet("hilo")+1, "hilo", "high").status() == GamblingService.Status.TOO_BIG && m.getBalance(wallet) == 10000, "Starter stake cap was ignored");
        unlock(m, wallet, 2);
        h.assertTrue(games.startBet(p.getUUID(), wallet, "test", 100, "penguin").accepted(), "Unlocked hop failed to start");
        var run = games.sessionOf(p.getUUID());
        for (int i=0; i<10; i++) run.applySuccess(PenguinGame.next(run.rounds(),run.multiplier(),run.maxMultiplier(),1));
        h.assertTrue(run.maxMultiplier() == 40 && run.multiplier() == 40 && run.atLimit(), "Hop run exceeded its payout ceiling");
        h.assertTrue(games.cashOut(p.getUUID(), wallet, "test", "penguin").payout() == 4000, "Capped run paid the wrong amount");
        UUID oldWallet = UUID.randomUUID(); m.setBalance(oldWallet, 0);
        m.putBet(p.getUUID(), BetSession.restore(100, oldWallet, "multiplier", 1000, 2, 1));
        h.assertTrue(games.cashOut(p.getUUID(), wallet, "test", "multiplier").payout() == 200 && m.getBalance(oldWallet) == 200,
                "Level gate blocked return of a pre-existing stake");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void terminalRequiresLevelAndAdvancementAndUsesOnePriceAcrossPurchasePaths(GameTestHelper h) {
        var p = player(h); var m = TeamEconomyMod.get().economy().manager(); var wallet = wallet(p); m.setBalance(wallet, 1000000);
        p.getInventory().items.set(0, new ItemStack(ModRegistries.TERMINAL.get())); p.getInventory().selected = 0;
        var terminal = ModRegistries.TERMINAL.get();
        var outcome = terminal.use(p.level(), p, InteractionHand.MAIN_HAND);
        h.assertTrue(outcome.getResult() == net.minecraft.world.InteractionResult.FAIL, "An old terminal bypassed the level gate");
        unlock(m, wallet, 5);
        var menu = new CasinoMenu(17, p.getInventory(), "scratch", p.blockPosition(), true);
        h.assertTrue(!menu.stillValid(p), "Terminal level bypassed missing endgame advancement");
        ProgressionTest.grant(p, "minecraft:end/kill_dragon"); ProgressionTest.grant(p, "minecraft:story/iron_tools");
        h.assertTrue(menu.stillValid(p), "Qualified terminal holder was rejected");
        var prices = new PriceService(); prices.basePrices().put("teamecon:terminal", 1);
        var shop = new ShopService(p.getServer(), m, prices); var economy = new EconomyService(p.getServer(), m, prices);
        h.assertTrue(shop.unitBuyPrice("teamecon:terminal") == 100000 && economy.unitBuyPrice("teamecon:terminal") == 100000, "Terminal fell back to a cheap recipe price");
        p.getInventory().clearContent();
        h.assertTrue(shop.buyItem(p, terminal, 1).outcome() == ShopService.Outcome.OK && economy.buy(p, terminal, 1).outcome() == EconomyService.Outcome.OK, "Qualified terminal exchange failed");
        h.assertTrue(m.getBalance(wallet) == 800000 && p.getInventory().countItem(terminal) == 2, "Terminal price differs between UI and commands");
        m.putBet(p.getUUID(), new BetSession(100, wallet, "penguin", 5));
        p.getInventory().clearContent(); menu.removed(p);
        h.assertTrue(m.getBalance(wallet) == 800100 && m.bet(p.getUUID()) == null, "Losing terminal trapped the run stake");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void brokenLevelConfigFailsClosedButDoesNotConsumeEscrow(GameTestHelper h) throws Exception {
        var dir = Files.createTempDirectory("casino-levels-invalid-");
        try {
            Files.writeString(dir.resolve(CasinoProgression.FILE_NAME), "{broken");
            var rules = new CasinoProgression(); rules.load(dir);
            var p = player(h); var m = new TeamEconomyManager(); var wallet = wallet(p); m.setBalance(wallet, 300000); unlock(m, wallet, 5);
            var games = new GamblingService(p.getServer(), m, rules);
            h.assertTrue(!rules.valid() && !games.playInstant(p.getUUID(), wallet, "test", 10, "hilo", "low").accepted() && m.getBalance(wallet) == 300000, "Broken config silently opened games");
            m.putBet(p.getUUID(), new BetSession(100, wallet, "penguin", 5));
            h.assertTrue(games.cashOut(p.getUUID(), wallet, "test", "penguin").payout() == 100, "Broken config trapped escrow");
        } finally { Files.deleteIfExists(dir.resolve(CasinoProgression.FILE_NAME)); Files.deleteIfExists(dir); }
        h.succeed();
    }
}
