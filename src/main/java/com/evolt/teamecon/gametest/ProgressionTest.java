package com.evolt.teamecon.gametest;

import com.evolt.teamecon.TeamEconomyMod;
import com.evolt.teamecon.economy.*;
import com.evolt.teamecon.shop.*;
import com.evolt.teamecon.team.TeamUtil;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@GameTestHolder("teamecon")
public final class ProgressionTest {
    private record Fixture(ServerPlayer player, TeamEconomyManager manager, UUID wallet, ShopService shop, EconomyService economy) {}
    public static ServerPlayer player(GameTestHelper h,String name) {
        // 1.21 checks instanceof FakePlayer when awarding advancements. A real player
        // with an inert test connection exercises vanilla progression on both versions.
        var profile = new GameProfile(UUID.randomUUID(),name);
        var player = new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,
                net.minecraft.server.level.ClientInformation.createDefault());
        player.connection = FakePlayerFactory.get(h.getLevel(),profile).connection;
        return player;
    }
    private static Fixture fixture(GameTestHelper h) {
        var server=h.getLevel().getServer();
        var p=player(h,"progress-test");
        p.getInventory().clearContent();p.containerMenu=p.inventoryMenu;
        var manager=new TeamEconomyManager();var wallet=TeamUtil.walletKey(server,p.getUUID());
        manager.setBalance(wallet,1_000_000);
        var rules=new PurchaseRules(server,manager);var prices=TeamEconomyMod.get().prices();
        return new Fixture(p,manager,wallet,new ShopService(server,manager,prices,rules),new EconomyService(server,manager,prices,rules));
    }
    public static void grant(ServerPlayer player,String id) {
        var advancement=player.getServer().getAdvancements().get(ResourceLocation.parse(id));
        if(advancement==null)throw new IllegalArgumentException("Missing advancement: "+id);
        for(String criterion:advancement.value().criteria().keySet())player.getAdvancements().award(advancement,criterion);
        if(!player.getAdvancements().getOrStartProgress(advancement).isDone())throw new IllegalStateException("Test advancement was not recorded: "+id);
    }
    private static void deleteFixture(Path dir) throws Exception {
        try(var files=Files.list(dir)){for(Path file:files.toList())Files.delete(file);}
        Files.delete(dir);
    }

    @GameTest(template="empty")
    public static void moneyCannotBuyFirstDiamondOrItsBlockAndCommandsObeyTheSameGate(GameTestHelper h) {
        var f=fixture(h);
        for(var item:new net.minecraft.world.item.Item[]{Items.DIAMOND,Items.DIAMOND_BLOCK,Items.DIAMOND_PICKAXE,Items.DIAMOND_ORE}){
            h.assertTrue(f.shop.buyItem(f.player,item,1).outcome()==ShopService.Outcome.PROGRESSION_LOCKED,"Shop bypassed first diamond: "+item);
            h.assertTrue(f.economy.buy(f.player,item,1).outcome()==EconomyService.Outcome.PROGRESSION_LOCKED,"Command bypassed first diamond: "+item);
        }
        h.assertTrue(f.manager.getBalance(f.wallet)==1_000_000&&f.player.getInventory().isEmpty(),"Locked purchase mutated state");
        grant(f.player,"minecraft:story/mine_diamond");
        var shop=f.shop.buyItem(f.player,Items.DIAMOND,1);
        var command=f.economy.buy(f.player,Items.DIAMOND_BLOCK,1);
        h.assertTrue(shop.outcome()==ShopService.Outcome.OK&&command.outcome()==EconomyService.Outcome.OK,"Earned advancement did not unlock restocking");
        h.assertTrue(f.manager.getBalance(f.wallet)==1_000_000-shop.charged()-command.total(),"Unlocked purchases charged incorrectly");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void bossAndExplorationRewardsStaySellOnlyEvenAfterLateProgress(GameTestHelper h) {
        var f=fixture(h);grant(f.player,"minecraft:story/enter_the_nether");grant(f.player,"minecraft:nether/obtain_ancient_debris");
        for(var item:new net.minecraft.world.item.Item[]{Items.NETHER_STAR,Items.BEACON,Items.ELYTRA,Items.HEAVY_CORE,
                Items.MACE,Items.WITHER_SKELETON_SKULL,Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE,Items.NETHERITE_CHESTPLATE}){
            h.assertTrue(f.shop.buyItem(f.player,item,1).outcome()==ShopService.Outcome.PROGRESSION_LOCKED,"Shop sold protected reward: "+item);
            h.assertTrue(f.economy.buy(f.player,item,1).outcome()==EconomyService.Outcome.PROGRESSION_LOCKED,"Command sold protected reward: "+item);
        }
        ItemStack star=new ItemStack(Items.NETHER_STAR);
        var sold=f.economy.sell(f.player,star);
        h.assertTrue(sold.outcome()==EconomyService.Outcome.OK&&sold.total()>0&&star.isEmpty(),"Sell-only loot lost its resale value");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void blindBoxesUseIndependentPoolsWithoutAdvancementLocks(GameTestHelper h) throws Exception {
        var f=fixture(h);Path dir=Files.createTempDirectory("teamecon-progress-box-");
        try{
            Files.writeString(dir.resolve("teamecon_blindbox.json"),"""
                    [{"id":"diamond","price":1000,"entries":[{"item":"minecraft:diamond","count":1,"weight":1}]},
                     {"id":"mixed","price":10000,"entries":[{"item":"minecraft:air","count":1,"weight":99},{"item":"minecraft:nether_star","count":1,"weight":1}]}]
                    """);
            f.shop.loadConfigs(dir);
            h.assertTrue(f.shop.boxAccess(f.player,f.shop.blindBoxes().byId("mixed")).unlocked(),"Custom prize pools retained advancement locks");
            h.assertTrue(f.shop.buyBlindBox(f.player,"diamond").outcome()==ShopService.Outcome.OK,"Box still requires the first diamond advancement");
            h.assertTrue(f.manager.getBalance(f.wallet)==999000&&f.player.getInventory().countItem(Items.DIAMOND)==1,"Unlocked box failed to charge and deliver");
        }finally{deleteFixture(dir);}
        h.succeed();
    }

    @GameTest(template="empty")
    public static void enchantedBooksRequireRealEnchantingAndMendingAlsoRequiresTrading(GameTestHelper h) throws Exception {
        var f=fixture(h);Path dir=Files.createTempDirectory("teamecon-progress-books-");
        try{
            f.shop.loadConfigs(dir);
            h.assertTrue(f.shop.buyEnchant(f.player,"unbreaking3").outcome()==ShopService.Outcome.PROGRESSION_LOCKED,"Book bypassed enchanting progression");
            h.assertTrue(f.economy.buy(f.player,Items.ENCHANTED_BOOK,1).outcome()!=EconomyService.Outcome.OK,"Generic command delivered an unconfigured book");
            grant(f.player,"minecraft:story/enchant_item");
            h.assertTrue(f.shop.buyEnchant(f.player,"unbreaking3").outcome()==ShopService.Outcome.OK,"Enchanting advancement did not unlock book");
            h.assertTrue(f.shop.buyEnchant(f.player,"mending").outcome()==ShopService.Outcome.PROGRESSION_LOCKED,"Mending skipped trading");
            grant(f.player,"minecraft:adventure/trade");
            h.assertTrue(f.shop.buyEnchant(f.player,"mending").outcome()==ShopService.Outcome.OK,"Trading did not unlock mending");
        }finally{deleteFixture(dir);}
        h.succeed();
    }

    @GameTest(template="empty")
    public static void customModRulesFailClosedAndAnAllowRuleCannotOverrideSellOnly(GameTestHelper h) throws Exception {
        var f=fixture(h);var rules=f.shop.progression();Path dir=Files.createTempDirectory("teamecon-progress-config-");
        try{
            h.assertTrue(!rules.item(f.player,"example:advanced_circuit").unlocked(),"Unknown technology became buyable");
            Files.writeString(dir.resolve(PurchaseRules.FILE_NAME),"""
                    {"version":1,"items":[
                      {"match":["example:advanced_*"],"advancement":"minecraft:story/iron_tools"},
                      {"match":["example:creative_*"],"sellOnly":true},
                      {"match":["example:*"],"advancement":""}],"enchantments":[]}
                    """);
            rules.load(dir);
            h.assertTrue(!rules.item(f.player,"example:advanced_circuit").unlocked(),"Broad allow removed a specific gate");
            grant(f.player,"minecraft:story/iron_tools");
            h.assertTrue(!rules.item(f.player,"example:advanced_circuit").unlocked(),"Legacy config enabled forbidden mod trades");
            h.assertTrue(!rules.item(f.player,"example:creative_motor").unlocked(),"Broad allow overrode sell-only rule");
            Files.writeString(dir.resolve(PurchaseRules.FILE_NAME),"{broken");rules.load(dir);
            h.assertTrue(!rules.item(f.player,"minecraft:bread").unlocked(),"Malformed rules silently opened the shop");
        }finally{deleteFixture(dir);}
        h.succeed();
    }

    @GameTest(template="empty")
    public static void absentStageProviderDoesNotGrantConfiguredStages(GameTestHelper h) {
        var f=fixture(h);
        h.assertTrue(!f.shop.unlocked(f.player,"late_technology"),"Missing stage API silently unlocked a restricted offer");
        h.assertTrue(f.shop.unlocked(f.player,""),"An unrestricted offer became locked");
        h.succeed();
    }
}
