package com.evolt.teamecon.shop;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BoxAdministrationTest {
    @TempDir Path dir;
    private JsonObject pool(String id){return JsonParser.parseString("""
            {"id":"%s","price":10,"entries":[{"item":"minecraft:air","count":1,"weight":1}]}
            """.formatted(id)).getAsJsonObject();}
    private BoxAdminConfig config() throws Exception {
        Files.writeString(dir.resolve("teamecon_blindbox.json"),"""
            {"version":1,"_comment":"keep me","pools":[{"id":"owner","price":12,"enabled":false,"note":"retain","entries":[{"item":"minecraft:air","count":1,"weight":1}]}]}
            """);
        BoxAdminConfig c=new BoxAdminConfig();c.load(dir);assertTrue(c.ready());return c;
    }
    @Test void createEditDeletePreservesOtherPoolsAndCustomFields()throws Exception{
        var c=config();var path=dir.resolve("teamecon_blindbox.json");String before=Files.readString(path);
        c.save("",pool("event"),false,c.revision(),null);
        assertEquals(before,Files.readString(dir.resolve("teamecon_blindbox.json.bak")));
        assertEquals("retain",c.pool("owner").get("note").getAsString());assertEquals(2,c.headers().size());
        JsonObject edit=c.pool("event");edit.addProperty("price",75);c.save("event",edit,false,c.revision(),null);
        assertEquals(75,c.pool("event").get("price").getAsInt());
        c.save("event",null,true,c.revision(),null);assertNull(c.pool("event"));assertEquals(1,c.headers().size());
        assertTrue(Files.readString(path).contains("keep me"));
    }
    @Test void staleEditsExternalWritesAndInvalidDraftsCannotOverwriteTheFile()throws Exception{
        var c=config();long revision=c.revision();c.save("",pool("new"),false,revision,null);
        String current=Files.readString(dir.resolve("teamecon_blindbox.json"));
        assertThrows(ConcurrentModificationException.class,()->c.save("",pool("another"),false,revision,null));
        assertThrows(IllegalArgumentException.class,()->c.save("",pool("owner"),false,c.revision(),null));
        var bad=pool("bad");bad.addProperty("price",-1);assertThrows(IllegalArgumentException.class,()->c.save("",bad,false,c.revision(),null));
        assertEquals(current,Files.readString(dir.resolve("teamecon_blindbox.json")));
        Files.writeString(dir.resolve("teamecon_blindbox.json"),current+"\n");
        assertThrows(java.io.IOException.class,()->c.save("",pool("another"),false,c.revision(),null));
    }
    @Test void potionVariantsRemainDistinctInSavedAndNetworkRewards(){
        var heal=new BoxReward("minecraft:potion","minecraft:healing",2);
        var speed=new BoxReward("minecraft:potion","minecraft:swiftness",1);
        var merged=BoxReward.merge(List.of(heal,speed,heal));assertEquals(2,merged.size());assertEquals(4,merged.getFirst().count());
        assertEquals(merged,BoxReward.decode(BoxReward.encode(merged)));
        assertThrows(IllegalArgumentException.class,()->new BoxReward("minecraft:diamond","minecraft:healing",1));
    }
    @Test void unchangedReleasedPoolsUpgradeButOwnerEditsArePreserved()throws Exception{
        var old=BlindBoxPools.expandedDefaultsJson();Path file=dir.resolve("teamecon_blindbox.json");Files.writeString(file,old.toString());
        var pools=new BlindBoxPools();pools.load(dir,null);
        assertEquals(old.toString(),Files.readString(dir.resolve("teamecon_blindbox.json.pre-potions.bak")));
        assertEquals(5,pools.byId("common").entries().stream().filter(e->!e.potion().isEmpty()).count());
        old.get(0).getAsJsonObject().addProperty("price",99);String customized=old.toString();Files.writeString(file,customized);
        pools.load(dir,null);assertEquals(99,pools.byId("common").price());assertEquals(customized,Files.readString(file));
    }
}
