package com.evolt.teamecon.gametest;
import com.evolt.teamecon.TeamEconomyMod;
import com.evolt.teamecon.economy.*;
import com.evolt.teamecon.gambling.*;
import com.evolt.teamecon.scratch.*;
import com.evolt.teamecon.network.payloads.*;
import com.evolt.teamecon.init.ModRegistries;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.gametest.GameTestHolder;
import java.util.UUID;

@GameTestHolder("teamecon")
public final class PortSmokeTest {
    @BeforeBatch(batch="teamecon")
    public static void initializeNamespacedBatch(net.minecraft.server.level.ServerLevel level) {
        initializeTestServer(level);
    }
    @BeforeBatch(batch="defaultBatch")
    public static void initializeTestServer(net.minecraft.server.level.ServerLevel level) {
        // Forge 52's GameTestServer fires AboutToStart but omits ServerStarting.
        // Production servers use the normal lifecycle; only this test fixture compensates.
        if (TeamEconomyMod.get().economy() == null) TeamEconomyMod.get().reloadServices(level.getServer());
    }
    public static ServerPlayer player(GameTestHelper h,String name){return PortPlayers.create(h,name);}
    @GameTest(template="empty")
    public static void configPricesAndRecipesLoad(GameTestHelper h){
        var mod=TeamEconomyMod.get();
        h.assertTrue(mod.economy()!=null&&mod.gambling()!=null&&mod.shop()!=null,"Server services unavailable");
        h.assertTrue(mod.prices().resolve("minecraft:diamond").known()&&mod.prices().resolve("minecraft:iron_block").known(),"Recipe pricing unavailable");
        h.assertTrue(ModRegistries.SLOT_MACHINE.get()!=null&&ModRegistries.SCRATCH_CARDS.size()==8,"Registry incomplete");
        h.assertTrue(mod.shop().blindBoxes().all().size()==2&&mod.shop().enchants().all().size()>30,"Port lost prize/book catalog");h.succeed();
    }
    @GameTest(template="empty")
    public static void cardSerialAndInventoryMergingRemainDistinct(GameTestHelper h){
        var p=player(h,"forge-cards");var a=new ItemStack(ModRegistries.SCRATCH_CARDS.get(ScratchKind.values()[0]).get());
        var b=a.copy();var id=UUID.randomUUID();ScratchCardItem.stamp(a,id);ScratchCardItem.stamp(b,UUID.randomUUID());
        h.assertTrue(id.equals(ScratchCardItem.serial(a))&&!id.equals(ScratchCardItem.serial(b)),"Card serial not preserved");
        var item=a.getItem();p.getInventory().clearContent();p.getInventory().add(a);p.getInventory().add(b);
        h.assertTrue(p.getInventory().countItem(item)==2,"Cards lost in inventory");h.succeed();
    }
    @GameTest(template="empty")
    public static void packetCatalogAndActionsRoundTrip(GameTestHelper h){
        var buf=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{
            var action=new CasinoActionPayload(2,7,CasinoActionPayload.Action.PLAY,"hilo","high",100);
            CasinoActionPayload.STREAM_CODEC.encode(buf,action);
            h.assertTrue(action.equals(CasinoActionPayload.STREAM_CODEC.decode(buf)),"Action packet changed");
            var catalog=new ShopSyncPayload(2,1000,"team",0,0,1,"items","boxes","enchants","","","rewards");
            ShopSyncPayload.STREAM_CODEC.encode(buf,catalog);
            h.assertTrue(catalog.equals(ShopSyncPayload.STREAM_CODEC.decode(buf)),"Catalog packet changed");
        }finally{buf.release();}h.succeed();
    }
}
