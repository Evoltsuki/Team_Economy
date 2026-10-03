package com.evolt.teamecon.shop;

import com.evolt.teamecon.config.TeConfig;
import com.evolt.teamecon.price.PriceService;
import com.google.gson.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Editing one pool preserves every other pool and owner-supplied JSON field. */
public final class BoxAdminConfig {
    private Path file;
    private String disk;
    private JsonObject root;
    private long revision;
    public void load(Path directory) {
        file=directory.resolve("teamecon_blindbox.json"); root=null; revision++;
        try {
            disk=read(); JsonElement value=JsonParser.parseString(disk);
            if(value.isJsonArray()){root=new JsonObject();root.addProperty("version",1);root.add("pools",value);}
            else root=value.getAsJsonObject();
            if(com.evolt.teamecon.config.ConfigJson.integer(root,"version",-1,1,1)!=1||root.getAsJsonArray("pools").size()>64)throw new IllegalArgumentException();
            Set<String> ids=new HashSet<>();
            for(JsonElement element:root.getAsJsonArray("pools")){
                ShopPool p=new ShopPool();p.read(element.getAsJsonObject());if(!ids.add(p.id()))throw new IllegalArgumentException();
            }
        } catch(IOException|RuntimeException ex){root=null;}
    }
    private String read() throws IOException {
        if(Files.size(file)>2_000_000)throw new IOException("Box config too large");
        return Files.readString(file);
    }
    public long revision(){return revision;}
    public boolean ready(){return root!=null;}
    public JsonArray headers(){
        JsonArray out=new JsonArray();if(root==null)return out;
        for(JsonElement value:root.getAsJsonArray("pools")){
            JsonObject p=value.getAsJsonObject(),header=new JsonObject();
            header.add("id",p.get("id"));header.add("price",p.get("price"));
            if(p.has("name"))header.add("name",p.get("name"));
            header.addProperty("enabled",!p.has("enabled")||p.get("enabled").getAsBoolean());out.add(header);
        }
        return out;
    }
    public JsonObject pool(String id){
        if(root!=null)for(JsonElement p:root.getAsJsonArray("pools"))if(p.getAsJsonObject().get("id").getAsString().equals(id))return p.getAsJsonObject().deepCopy();
        return null;
    }
    /** Failure leaves both disk and the live catalogue untouched. */
    public void save(String originalId,JsonObject replacement,boolean delete,long expectedRevision,PriceService prices)throws IOException{
        if(root==null)throw new IOException("Box config unavailable");
        if(expectedRevision!=revision)throw new ConcurrentModificationException("Stale box edit");
        if(!Objects.equals(disk,read()))throw new IOException("Reload externally changed config first");
        if(!originalId.isEmpty()&&pool(originalId)==null)throw new ConcurrentModificationException("Box removed");
        String id=originalId;
        if(!delete){
            if(replacement.toString().length()>24000)throw new IllegalArgumentException("Box too large for editor");
            ShopPool parsed=new ShopPool();parsed.read(replacement);id=parsed.id();
            if(!originalId.equals(id)&&pool(id)!=null)throw new IllegalArgumentException("Duplicate box ID");
            for(ShopPool.Entry entry:parsed.entries())if(!entry.itemKey().equals("minecraft:air")
                    &&(!parsed.allows(entry.itemKey())||!entry.reward().available()))throw new IllegalArgumentException("Unavailable prize");
            if(parsed.enforceValueCap()&&prices!=null&&BlindBoxPools.expectedValue(parsed,prices)>parsed.price()*TeConfig.GAMBLING.expectedValueCap.get()+1e-9)
                throw new IllegalArgumentException("Expected prize value exceeds the configured cap");
        }
        JsonObject next=root.deepCopy();JsonArray entries=new JsonArray();boolean replaced=false;
        for(JsonElement p:next.getAsJsonArray("pools")){
            if(p.getAsJsonObject().get("id").getAsString().equals(originalId)){if(!delete)entries.add(replacement.deepCopy());replaced=true;}
            else entries.add(p);
        }
        if(!delete&&!replaced)entries.add(replacement.deepCopy());
        if(entries.size()>64)throw new IllegalArgumentException("Too many boxes");
        next.add("pools",entries);
        String text=new GsonBuilder().setPrettyPrinting().create().toJson(next)+"\n";
        Path tmp=Files.createTempFile(file.getParent(),"teamecon-boxes-",".tmp");
        try{
            Files.writeString(tmp,text);
            Files.copy(file,file.resolveSibling(file.getFileName()+".bak"),StandardCopyOption.REPLACE_EXISTING);
            try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException ex){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
            root=next;disk=text;revision++;
        }finally{Files.deleteIfExists(tmp);}
    }
}
