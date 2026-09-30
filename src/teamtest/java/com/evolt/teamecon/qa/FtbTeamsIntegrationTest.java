package com.evolt.teamecon.qa;

import com.evolt.teamecon.economy.TeamEconomyManager;
import com.evolt.teamecon.gambling.GamblingService;
import com.evolt.teamecon.gambling.BetSession;
import com.evolt.teamecon.scratch.*;
import com.evolt.teamecon.init.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import com.evolt.teamecon.team.TeamUtil;
import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.List;
import java.util.UUID;

/** Exercises the installed FTB Teams API; excluded from production builds. */
@GameTestHolder("teamecon")
public final class FtbTeamsIntegrationTest {
    @GameTest(template="empty")
    public static void partyLevelsAreSharedButConcurrentRequestsDoNotBuyTwoLevels(GameTestHelper h) throws Exception {
        var server=h.getLevel().getServer();var teams=(TeamManagerImpl)FTBTeamsAPI.api().getManager();
        var owner=player(h,teams,"qa-level-owner");var member=player(h,teams,"qa-level-member");
        var party=(PartyTeam)teams.createPartyTeam(owner,"QA-level-"+UUID.randomUUID().toString().substring(0,6),"Levels",Color4I.rgb(0x428c72));
        party.invite(owner,List.of(member.getGameProfile()));party.join(member);
        h.assertTrue(TeamUtil.members(owner).containsAll(java.util.Set.of(owner.getUUID(),member.getUUID())),"Board omitted offline party members");
        var rules=com.evolt.teamecon.TeamEconomyMod.get().casinoProgression();
        long secondCost=rules.levelCost(3);
        var economy=new TeamEconomyManager();economy.setBalance(party.getId(),rules.levelCost(2)+secondCost);
        var shop=new com.evolt.teamecon.shop.ShopService(server,economy,com.evolt.teamecon.TeamEconomyMod.get().prices());
        h.assertTrue(shop.upgradeLevel(owner,2).outcome()==com.evolt.teamecon.shop.ShopService.Outcome.OK,"Owner could not buy party level");
        h.assertTrue(economy.casinoLevel(TeamUtil.walletKey(server,member.getUUID()))==2,"Member did not share level");
        h.assertTrue(shop.upgradeLevel(member,2).outcome()!=com.evolt.teamecon.shop.ShopService.Outcome.OK&&economy.getBalance(party.getId())==secondCost,"Concurrent member request double charged");
        h.assertTrue(shop.upgradeLevel(member,3).outcome()==com.evolt.teamecon.shop.ShopService.Outcome.OK&&economy.getBalance(party.getId())==0,"Member could not upgrade shared wallet");
        party.leave(member.getUUID());
        h.assertTrue(economy.casinoLevel(TeamUtil.walletKey(server,member.getUUID()))==1&&economy.casinoLevel(party.getId())==3,"Leaving copied party level into personal wallet");
        h.succeed();
    }
    private static ServerPlayer player(GameTestHelper h, TeamManagerImpl teams, String name) {
        var player = com.evolt.teamecon.gametest.ProgressionTest.player(h, name);
        // Register the personal record using FTB's login path, without an external client.
        teams.playerLoggedIn(null, player.getUUID(), name);
        return player;
    }

    @GameTest(template = "empty")
    public static void partyMembersShareWalletAndLeavingPreservesEscrow(GameTestHelper h) throws Exception {
        h.assertTrue(TeamUtil.isTeamsLoaded(), "FTB Teams manager was not detected");
        var server = h.getLevel().getServer();
        var teams = (TeamManagerImpl) FTBTeamsAPI.api().getManager();
        var owner = player(h, teams, "qa-owner");
        var member = player(h, teams, "qa-member");
        var outsider = player(h, teams, "qa-outsider");
        String name = "QA-" + UUID.randomUUID().toString().substring(0, 8);
        var party = (PartyTeam) teams.createPartyTeam(owner, name, "Integration test", Color4I.rgb(0x428c72));
        party.invite(owner, List.of(member.getGameProfile()));
        party.join(member);

        UUID shared = TeamUtil.walletKey(server, owner.getUUID());
        h.assertTrue(shared.equals(party.getId()), "Owner still resolves to their personal team");
        h.assertTrue(TeamUtil.walletKey(server, member.getUUID()).equals(shared), "Party members do not share a wallet");
        h.assertTrue(TeamUtil.sameWallet(server, owner.getUUID(), member.getUUID()), "Same-team comparison failed");
        h.assertTrue(!TeamUtil.sameWallet(server, owner.getUUID(), outsider.getUUID()), "Different teams share a wallet");
        h.assertTrue(TeamUtil.displayName(server, member.getUUID()).equals(name), "Wallet name is not the party name");

        var economy = new TeamEconomyManager();
        var games = new GamblingService(server, economy);
        economy.setBalance(shared, 1000);
        com.evolt.teamecon.gametest.CasinoLevelsTest.unlock(economy, shared, 2);
        economy.setBalance(outsider.getUUID(), 25);
        h.assertTrue(games.startBet(member.getUUID(), TeamUtil.walletKey(server, member.getUUID()),
                "qa-member", 100, "penguin").accepted(), "Party stake was rejected");
        h.assertTrue(economy.getBalance(TeamUtil.walletKey(server, owner.getUUID())) == 900,
                "Owner cannot see the member's debit");
        h.assertTrue(economy.getBalance(outsider.getUUID()) == 25, "Unrelated wallet was changed");

        party.leave(member.getUUID());
        UUID personal = TeamUtil.walletKey(server, member.getUUID());
        h.assertTrue(personal.equals(member.getUUID()) && !personal.equals(shared), "Leaving did not restore personal wallet");
        h.assertTrue(games.playRound(member.getUUID(), personal, "penguin", "jump").status()
                == GamblingService.Status.TEAM_CHANGED, "Departed member can still wager party funds");
        var paid = games.cashOut(member.getUUID(), personal, "qa-member", "penguin");
        h.assertTrue(paid.payout() == 100 && economy.getBalance(shared) == 1000 && economy.getBalance(personal) == 0,
                "Cash-out moved party funds into the departing member's wallet");
        h.assertTrue(!games.cashOut(member.getUUID(), personal, "qa-member", "penguin").accepted(),
                "Escrow was paid twice");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void joiningRichPartyDoesNotUnlockAnotherPlayersProgression(GameTestHelper h) throws Exception {
        var server=h.getLevel().getServer();var teams=(TeamManagerImpl)FTBTeamsAPI.api().getManager();
        var owner=player(h,teams,"qa-gate-owner");var member=player(h,teams,"qa-gate-member");
        var party=(PartyTeam)teams.createPartyTeam(owner,"QA-gate-"+UUID.randomUUID().toString().substring(0,6),"Progression",Color4I.rgb(0x428c72));
        party.invite(owner,List.of(member.getGameProfile()));party.join(member);
        com.evolt.teamecon.gametest.ProgressionTest.grant(owner,"minecraft:story/mine_diamond");
        var rules=new com.evolt.teamecon.shop.PurchaseRules(server);
        h.assertTrue(TeamUtil.sameWallet(server,owner.getUUID(),member.getUUID()),"Test players do not share a wallet");
        h.assertTrue(rules.item(owner,"minecraft:diamond").unlocked(),"Owner progress was not recognized");
        h.assertTrue(!rules.item(member,"minecraft:diamond").unlocked(),"Party membership bypassed personal progression");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void delayedMachinePayoutKeepsItsPartyWalletAndTicketsFollowTheHolder(GameTestHelper h) throws Exception {
        var server=h.getLevel().getServer();var teams=(TeamManagerImpl)FTBTeamsAPI.api().getManager();
        var owner=player(h,teams,"qa-world-owner");var member=player(h,teams,"qa-world-member");
        var party=(PartyTeam)teams.createPartyTeam(owner,"QA-world-"+UUID.randomUUID().toString().substring(0,6),"Physical games",Color4I.rgb(0x428c72));
        party.invite(owner,List.of(member.getGameProfile()));party.join(member);
        UUID shared=party.getId();var economy=new TeamEconomyManager();var games=new GamblingService(server,economy);
        economy.setBalance(shared,900);
        var run=BetSession.restore(100,shared,"penguin",1000,2.5,3);
        var pos=h.absolutePos(new BlockPos(6,2,6));run.bindMachine(member.level().dimension().location().toString(),pos);economy.putBet(member.getUUID(),run);
        var pending=games.beginMachine(member,pos,"penguin","",0,true,30);
        h.assertTrue(pending!=null&&economy.getBalance(shared)==900,"Party cash-out paid before animation");
        party.leave(member.getUUID());
        games.settleMachine(com.evolt.teamecon.gambling.SessionKey.of(pending),pending.dueTick());
        h.assertTrue(economy.getBalance(shared)==1150&&economy.getBalance(member.getUUID())==0,"Leaving redirected delayed party payout");
        var serial=UUID.randomUUID();var kind=ScratchKind.MATCH;
        var ticket=new ScratchTicket(serial,kind,kind.price(),4,100,37);economy.putTicket(ticket);
        ItemStack item=new ItemStack(ModRegistries.SCRATCH_CARDS.get(kind).get());ScratchCardItem.stamp(item,serial);
        // Passing the physical card into a party credits its current holder's shared wallet.
        owner.getInventory().items.set(0,item);owner.containerMenu=new ScratchCardMenu(4,owner.getInventory(),ticket);
        long amount=new ScratchCardService(server,economy).reveal(owner,serial);
        h.assertTrue(amount==100&&economy.getBalance(shared)==1250&&economy.getBalance(member.getUUID())==0,"Physical ticket did not credit holder's party");
        h.succeed();
    }
}
