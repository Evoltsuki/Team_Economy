package com.evolt.teamecon.gametest;
import com.evolt.teamecon.TeamEconomyMod;
import com.evolt.teamecon.economy.*;
import com.evolt.teamecon.shop.*;
import com.evolt.teamecon.team.TeamUtil;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.nio.file.*;
import java.util.*;

@GameTestHolder("teamecon")
public final class BoxFeaturesTest {
    @GameTest(template="empty")
    public static void sixtyFourPotionsPersistOverflowAndClaimExactlyOnce(GameTestHelper h)throws Exception{
        var p=ProgressionTest.player(h,"box-potions");p.getInventory().clearContent();
        var manager=new TeamEconomyManager();var wallet=TeamUtil.walletKey(p.getServer(),p.getUUID());manager.setBalance(wallet,10000);
        Path dir=Files.createTempDirectory("teamecon-box-potions-");
        try{
            Files.writeString(dir.resolve("teamecon_blindbox.json"),"""
                [{"id":"healing","price":10,"enforceValueCap":false,"entries":[{"item":"minecraft:potion","potion":"minecraft:healing","count":1,"weight":1}]}]
                """);
            var shop=new ShopService(p.getServer(),manager,TeamEconomyMod.get().prices());shop.loadConfigs(dir);
            var result=shop.buyBlindBox(p,"healing",64);
            h.assertTrue(result.outcome()==ShopService.Outcome.OK&&manager.getBalance(wallet)==9360,"64 openings were rejected or mischarged");
            h.assertTrue(p.getInventory().countItem(Items.POTION)==36&&shop.pendingBoxes(p).getFirst().count()==28,"Overflow lost rewards");
            h.assertTrue(p.getInventory().getItem(0).getHoverName().getString().equals(Component.translatable("item.minecraft.potion.effect.healing").getString()),"Delivered potion lost its effect");
            h.assertTrue(shop.buyBlindBox(p,"healing",64).outcome()==ShopService.Outcome.NO_SPACE&&manager.getBalance(wallet)==9360,"Pending batch was charged twice");
            var saved=manager.save(new CompoundTag(),h.getLevel().registryAccess());
            saved.getCompound("pendingBoxRewards").putString("broken-entry","invalid");
            var restored=TeamEconomyManager.load(saved,h.getLevel().registryAccess());
            var again=new ShopService(p.getServer(),restored,TeamEconomyMod.get().prices());
            p.getInventory().clearContent();again.claimBlindBoxes(p);
            h.assertTrue(p.getInventory().countItem(Items.POTION)==28&&again.pendingBoxes(p).isEmpty()&&restored.getBalance(wallet)==9360,"Reload/claim lost or duplicated rewards");
            again.claimBlindBoxes(p);h.assertTrue(p.getInventory().countItem(Items.POTION)==28,"Repeated claim duplicated prizes");
            h.succeed();
        }finally{try(var files=Files.list(dir)){for(Path file:files.toList())Files.delete(file);}Files.delete(dir);}
    }
    @GameTest(template="empty")
    public static void defaultsAndTypedPotionsValidateOnTheRunningRegistry(GameTestHelper h)throws Exception{
        Path dir=Files.createTempDirectory("teamecon-box-defaults-");
        try{
            var pools=new BlindBoxPools();pools.load(dir,TeamEconomyMod.get().prices());
            h.assertTrue(pools.all().size()==2,"Default pools failed value or registry validation");
            for(var pool:pools.all()){
                h.assertTrue(pool.totalWeight()==10000,"Default probabilities no longer sum to 100%");
                h.assertTrue(pool.entries().stream().filter(e->!e.potion().isEmpty()).count()==5,"Missing utility potions");
                h.assertTrue(pool.entries().stream().allMatch(e->e.reward().available()),"Unregistered default reward");
            }
            var admin=new BoxAdminConfig();admin.load(dir);long rev=admin.revision();String old=Files.readString(dir.resolve("teamecon_blindbox.json"));
            var invalid=admin.pool("common");invalid.getAsJsonArray("entries").get(0).getAsJsonObject().addProperty("potion","minecraft:no_such_potion");
            boolean rejected=false;try{admin.save("common",invalid,false,rev,TeamEconomyMod.get().prices());}catch(IllegalArgumentException ex){rejected=true;}
            h.assertTrue(rejected&&Files.readString(dir.resolve("teamecon_blindbox.json")).equals(old),"Invalid potion altered the config");
            h.succeed();
        }finally{try(var files=Files.list(dir)){for(Path file:files.toList())Files.delete(file);}Files.delete(dir);}
    }
    @GameTest(template="empty")
    public static void boxEditorRejectsForgedRequestsAndCannotGiveItems(GameTestHelper h){
        var player=ProgressionTest.player(h,"box-no-op");var menu=new BoxAdminMenu(93,player.getInventory(),"");
        h.assertTrue(menu.slots.isEmpty()&&!menu.stillValid(player),"Ordinary player can edit boxes");
        player.containerMenu=menu;var config=TeamEconomyMod.get().shop().boxAdmin();long revision=config.revision();
        com.evolt.teamecon.network.BoxAdminNetwork.handle(player,new com.evolt.teamecon.network.payloads.BoxAdminActionPayload(93,1,revision,2,"common",""));
        h.assertTrue(config.revision()==revision&&menu.quickMoveStack(player,0).isEmpty(),"Forged deletion or item extraction accepted");
        player.containerMenu=player.inventoryMenu;h.succeed();
    }
}
