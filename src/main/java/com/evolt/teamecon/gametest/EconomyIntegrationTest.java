package com.evolt.teamecon.gametest;

import com.evolt.teamecon.TeamEconomyMod;
import com.evolt.teamecon.casino.CasinoMenu;
import com.evolt.teamecon.economy.*;
import com.evolt.teamecon.gambling.*;
import com.evolt.teamecon.init.ModRegistries;
import com.evolt.teamecon.network.payloads.*;
import com.evolt.teamecon.price.PriceService;
import com.evolt.teamecon.shop.ShopService;
import com.evolt.teamecon.team.TeamUtil;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.nio.file.*;
import java.util.UUID;

@GameTestHolder("teamecon")
public final class EconomyIntegrationTest {
    private EconomyIntegrationTest() {}
    private static ServerPlayer player(GameTestHelper h) {
        var p=ProgressionTest.player(h,"economy-test");
        var pos=h.absolutePos(new BlockPos(3,2,3));
        p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        p.getInventory().clearContent();
        ProgressionTest.grant(p,"minecraft:story/mine_diamond");
        return p;
    }

    @GameTest(template="empty")
    public static void partialInventoryNeverGivesFreePurchases(GameTestHelper h) {
        var p=player(h); var manager=new TeamEconomyManager();
        UUID wallet=TeamUtil.walletKey(h.getLevel().getServer(),p.getUUID());
        manager.setBalance(wallet,100000);
        PriceService prices=new PriceService(); prices.basePrices().put("minecraft:diamond",256);
        ShopService shop=new ShopService(h.getLevel().getServer(),manager,prices);
        for(int i=0;i<36;i++) p.getInventory().items.set(i,new ItemStack(Items.COBBLESTONE,64));
        p.getInventory().items.set(0,new ItemStack(Items.DIAMOND,63));
        var failed=shop.buyItem(p,Items.DIAMOND,2);
        h.assertTrue(failed.outcome()==ShopService.Outcome.NO_SPACE,"Partial delivery must be rejected");
        h.assertTrue(p.getInventory().items.get(0).getCount()==63&&manager.getBalance(wallet)==100000,"Failed purchase mutated inventory or wallet");
        var bought=shop.buyItem(p,Items.DIAMOND,1);
        h.assertTrue(bought.outcome()==ShopService.Outcome.OK&&p.getInventory().items.get(0).getCount()==64,"Valid delivery failed");
        h.assertTrue(manager.getBalance(wallet)==100000-bought.charged(),"Purchase did not charge exactly once");
        h.assertTrue(manager.ledger(wallet).getFirst().total()<0,"Purchase ledger sign must be negative");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void commandPurchasesAreAlsoAtomic(GameTestHelper h) {
        var p=player(h); var manager=new TeamEconomyManager();
        UUID wallet=TeamUtil.walletKey(h.getLevel().getServer(),p.getUUID()); manager.setBalance(wallet,100000);
        PriceService prices=new PriceService(); prices.basePrices().put("minecraft:diamond",256);
        var economy=new EconomyService(h.getLevel().getServer(),manager,prices);
        for(int i=0;i<36;i++)p.getInventory().items.set(i,new ItemStack(Items.COBBLESTONE,64));
        p.getInventory().items.set(0,new ItemStack(Items.DIAMOND,63));
        h.assertTrue(economy.buy(p,Items.DIAMOND,2).outcome()==EconomyService.Outcome.NO_SPACE,"Command must preflight delivery");
        h.assertTrue(p.getInventory().items.get(0).getCount()==63&&manager.getBalance(wallet)==100000,"Command gave free partial goods");
        h.assertTrue(economy.buy(p,Items.DIAMOND,Integer.MAX_VALUE).outcome()!=EconomyService.Outcome.OK,"Unbounded purchase accepted");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void escrowAndFractionalIncomeSurviveDiskSave(GameTestHelper h) throws Exception {
        TeamEconomyManager manager=new TeamEconomyManager();
        UUID player=UUID.randomUUID(), original=UUID.randomUUID(), current=UUID.randomUUID();
        manager.setBalance(original,500); manager.setBalance(current,17); manager.setSaleRemainder(original,.625);
        BetSession run=BetSession.restore(100,original,"penguin",1000,2.25,3); manager.putBet(player,run);
        Path path=Files.createTempFile("teamecon-economy-test-",".dat");
        try {
            NbtIo.writeCompressed(manager.save(new CompoundTag(),h.getLevel().registryAccess()),path);
            TeamEconomyManager loaded=TeamEconomyManager.load(NbtIo.readCompressed(path,NbtAccounter.unlimitedHeap()),h.getLevel().registryAccess());
            h.assertTrue(loaded.saleRemainder(original)==.625,"Fractional income was lost");
            GamblingService games=new GamblingService(h.getLevel().getServer(),loaded);
            h.assertTrue(games.playRound(player,current,"penguin","jump").status()==GamblingService.Status.TEAM_CHANGED,"Changed team may not gamble the old wallet");
            var paid=games.cashOut(player,current,"test","penguin");
            h.assertTrue(paid.payout()==225&&loaded.getBalance(original)==725&&loaded.getBalance(current)==17,"Cash-out changed ownership");
            h.assertTrue(games.cashOut(player,current,"test","penguin").payout()==0,"Repeated cash-out paid twice");
            h.assertTrue(loaded.bet(player)==null,"Closed escrow was retained");
        } finally { Files.deleteIfExists(path); }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void closingBothMenuTypesConservesDepositedItems(GameTestHelper h) {
        var p=player(h);
        for(boolean remote:new boolean[]{false,true}) {
            CasinoMenu menu=new CasinoMenu(remote?2:1,p.getInventory(),remote?"":"slots",p.blockPosition(),remote);
            menu.deposit().setItem(0,new ItemStack(Items.DIAMOND,3));
            int before=p.getInventory().countItem(Items.DIAMOND)
                    +h.getEntities(net.minecraft.world.entity.EntityType.ITEM).stream().filter(e->e.getItem().is(Items.DIAMOND)).mapToInt(e->e.getItem().getCount()).sum();
            menu.removed(p); menu.removed(p);
            int after=p.getInventory().countItem(Items.DIAMOND)
                    +h.getEntities(net.minecraft.world.entity.EntityType.ITEM).stream().filter(e->e.getItem().is(Items.DIAMOND)).mapToInt(e->e.getItem().getCount()).sum();
            h.assertTrue(menu.deposit().isEmpty()&&after-before==3,"Closing menu lost or duplicated deposited items");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void rarePricesAndOperatorItemRestrictionsAreLoaded(GameTestHelper h) {
        PriceService prices=TeamEconomyMod.get().prices();
        h.assertTrue(prices.resolve("minecraft:nether_star").unitPrice()==8192,"Nether star fell back to rarity pricing");
        h.assertTrue(prices.snapshot().get("minecraft:nether_star")==8192,"Client snapshot overwrote the base price");
        h.assertTrue(prices.resolve("minecraft:elytra").unitPrice()==16384,"Exploration loot is underpriced");
        h.assertTrue(!prices.resolve("minecraft:command_block").known(),"Operator block was priced for survival");
        h.assertTrue(!TeamEconomyMod.get().shop().itemCatalog().contains("minecraft:creeper_spawn_egg"),"Spawn egg leaked into shop");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void enchantedBooksUseTheVanillaStoredComponent(GameTestHelper h) {
        var enchant=h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING);
        ItemStack book=ShopService.enchantedBook(enchant,1);
        h.assertTrue(book.is(Items.ENCHANTED_BOOK),"Shop did not deliver a book");
        h.assertTrue(book.get(DataComponents.STORED_ENCHANTMENTS).getLevel(enchant)==1,"Book cannot be used on an anvil");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void emptyBlindBoxesStillChargeAndCannotBeRerolled(GameTestHelper h) throws Exception {
        var p=player(h); var manager=new TeamEconomyManager();
        UUID wallet=TeamUtil.walletKey(h.getLevel().getServer(),p.getUUID()); manager.setBalance(wallet,10000);
        Path dir=Files.createTempDirectory("teamecon-shop-test-");
        try {
            Files.writeString(dir.resolve("teamecon_blindbox.json"),"""
                    [{"id":"empty","price":10,"entries":[{"item":"minecraft:air","count":1,"weight":1}]},
                     {"id":"mixed","price":1000,"entries":[{"item":"minecraft:air","count":1,"weight":1},{"item":"minecraft:diamond","count":2,"weight":1}]}]
                    """);
            var shop=new ShopService(h.getLevel().getServer(),manager,TeamEconomyMod.get().prices()); shop.loadConfigs(dir);
            var empty=shop.buyBlindBox(p,"empty");
            h.assertTrue(empty.outcome()==ShopService.Outcome.OK&&manager.getBalance(wallet)==9990,"Empty result allowed a free reroll");
            for(int i=0;i<36;i++)p.getInventory().items.set(i,new ItemStack(Items.COBBLESTONE,64));
            p.getInventory().items.set(0,new ItemStack(Items.DIAMOND,63));
            for(int i=0;i<20;i++)h.assertTrue(shop.buyBlindBox(p,"mixed").outcome()==ShopService.Outcome.NO_SPACE,"Capacity checks must precede the random draw");
            h.assertTrue(manager.getBalance(wallet)==9990&&p.getInventory().items.get(0).getCount()==63,"Rejected blind box mutated state");
        } finally {
            try(var files=Files.list(dir)) { for(Path file:files.toList())Files.deleteIfExists(file); }
            Files.deleteIfExists(dir);
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void shopReceiptsCanContainLocalizedItemAndEnchantmentNames(GameTestHelper h) {
        var item=com.evolt.teamecon.network.ModNetwork.formatMessage("shop.teamecon.item_received","minecraft:diamond,1,384");
        var book=com.evolt.teamecon.network.ModNetwork.formatMessage("shop.teamecon.book_received","minecraft:unbreaking,3");
        var terminal=com.evolt.teamecon.network.ModNetwork.formatMessage("shop.teamecon.item_received","teamecon:terminal,1,100000");
        h.assertTrue(!item.getString().isEmpty()&&!book.getString().isEmpty()&&!terminal.getString().contains("teamecon:terminal"),"Purchase receipts lost localized arguments");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void shopPacketsCarryCatalogChunksLargerThanTheOldLimit(GameTestHelper h) {
        String items="minecraft:diamond,384,1,;".repeat(500);
        ShopSyncPayload sent=new ShopSyncPayload(7,100,"test",12,0,1,items,"","","shop.teamecon.box_batch","64,4096","minecraft:diamond,64;minecraft:air,1");
        FriendlyByteBuf buf=new FriendlyByteBuf(Unpooled.buffer());
        try {
            ShopSyncPayload.STREAM_CODEC.encode(buf,sent);
            h.assertTrue(ShopSyncPayload.STREAM_CODEC.decode(buf).equals(sent),"Catalog packet was truncated");
            CasinoActionPayload request=new CasinoActionPayload(7,123,CasinoActionPayload.Action.PLAY,"roulette","number:0",100);
            CasinoActionPayload.STREAM_CODEC.encode(buf,request);
            h.assertTrue(CasinoActionPayload.STREAM_CODEC.decode(buf).equals(request),"Bound game request did not round-trip");
        } finally { buf.release(); }
        h.succeed();
    }
}
