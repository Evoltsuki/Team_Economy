package com.evolt.teamecon.client;

import com.evolt.teamecon.shop.*;
import com.evolt.teamecon.network.payloads.*;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Operator-only form editor; selection icons never act as inventory slots. */
public final class BoxAdminScreen extends CompactContainerScreen<BoxAdminMenu> {
    private record Choice(BoxReward reward,ItemStack icon,SearchText search){}
    private final List<Choice> choices=new ArrayList<>();
    private JsonArray headers=new JsonArray();
    private JsonObject draft;
    private String originalId="",status="",query="";
    private long sequence,revision;
    private int poolPage,prizePage,pickerPage,selected=-1;
    private boolean started,waiting,editable,dirty,picking,confirmDelete;
    private EditBox idField,nameField,priceField,stageField,countField,weightField,search;
    public BoxAdminScreen(BoxAdminMenu menu,Inventory inventory,Component title){super(menu,inventory,title);}
    public boolean ready(){return editable&&!waiting;}
    public JsonObject currentPool(){return draft==null?null:draft.deepCopy();}
    @Override protected void init(){
        imageWidth=520;imageHeight=330;super.init();
        idField=nameField=priceField=stageField=countField=weightField=search=null;
        button(495,8,18,18,Component.literal("×"),this::onClose);
        for(int i=0;i<7;i++){
            int index=poolPage*7+i;if(index>=headers.size())break;
            JsonObject row=headers.get(index).getAsJsonObject();String id=row.get("id").getAsString();
            button(8,45+i*23,116,21,Component.literal(id),()->{if(leaveDraft())request(0,id,"");});
        }
        button(8,212,24,18,Component.literal("‹"),()->{if(leaveDraft()){poolPage=Math.max(0,poolPage-1);rebuildWidgets();}});
        button(100,212,24,18,Component.literal("›"),()->{if(leaveDraft()){poolPage=Math.min(Math.max(0,(headers.size()-1)/7),poolPage+1);rebuildWidgets();}});
        button(8,239,116,20,tr("new"),()->{if(leaveDraft())newPool();}).active=editable&&!waiting;
        button(8,265,116,20,tr(confirmDelete?"confirm_delete":"delete"),()->{
            if(originalId.isEmpty())return;
            if(!confirmDelete){confirmDelete=true;capture();rebuildWidgets();return;}
            request(2,originalId,"");
        }).active=editable&&!waiting&&!originalId.isEmpty();
        button(8,291,116,20,tr("refresh"),()->{dirty=false;picking=false;request(3,originalId,"");});
        if(draft!=null){if(picking)buildPicker();else buildEditor();}
        if(!started){started=true;request(0,"","");}
    }
    @Override public void resize(Minecraft minecraft,int width,int height){capture();super.resize(minecraft,width,height);}
    private Button button(int x,int y,int w,int h,Component label,Runnable action){
        Button b=Button.builder(label,ignored->action.run()).bounds(leftPos+x,topPos+y,w,h).build();
        b.setTooltip(Tooltip.create(label));return addRenderableWidget(b);
    }
    private EditBox field(int x,int y,int w,String key,String value,int length){
        EditBox b=new EditBox(font,leftPos+x,topPos+y,w,16,tr(key));b.setMaxLength(length);b.setValue(value);
        b.setResponder(v->{dirty=true;confirmDelete=false;});addRenderableWidget(b);return b;
    }
    private JsonArray entries(){return draft.getAsJsonArray("entries");}
    private static String text(JsonObject obj,String key,String fallback){return obj.has(key)?obj.get(key).getAsString():fallback;}
    private static boolean flag(JsonObject obj,String key,boolean fallback){return obj.has(key)?obj.get(key).getAsBoolean():fallback;}
    private void buildEditor(){
        idField=field(136,49,104,"id",text(draft,"id",""),32);
        nameField=field(252,49,132,"name",text(draft,"name",""),64);
        priceField=field(396,49,112,"price",text(draft,"price","64"),10);
        button(252,88,78,20,tr(flag(draft,"enabled",true)?"enabled":"disabled"),()->toggle("enabled",true));
        stageField=field(136,90,104,"stage",text(draft,"stage",""),128);
        button(336,88,84,20,tr(flag(draft,"allowModdedItems",false)?"mods_on":"mods_off"),()->toggle("allowModdedItems",false));
        button(426,88,82,20,tr(flag(draft,"enforceValueCap",true)?"cap_on":"cap_off"),()->toggle("enforceValueCap",true));
        prizePage=Math.clamp(prizePage,0,Math.max(0,(entries().size()-1)/7));
        for(int i=0;i<7;i++){
            int index=prizePage*7+i;if(index>=entries().size())break;
            JsonObject e=entries().get(index).getAsJsonObject();BoxReward reward=reward(e);
            ItemStack icon=reward.stack();String label=icon.isEmpty()?tr("empty").getString():icon.getHoverName().getString();
            button(136,126+i*19,190,18,Component.literal(font.plainSubstrByWidth(label,115)+" ×"+reward.count()),()->{
                if(capture()){selected=index;rebuildWidgets();}
            }).setTooltip(Tooltip.create(Component.literal(label+" ×"+reward.count()+" · "+chance(e))));
        }
        button(136,263,24,18,Component.literal("‹"),()->prizePage(-1));
        button(302,263,24,18,Component.literal("›"),()->prizePage(1));
        button(166,263,132,18,tr("add"),()->{if(capture()){picking=true;query="";pickerPage=0;buildChoices();rebuildWidgets();}}).active=editable&&!waiting&&entries().size()<128;
        if(selected>=0&&selected<entries().size()){
            JsonObject e=entries().get(selected).getAsJsonObject();
            countField=field(342,165,72,"count",text(e,"count","1"),4);
            weightField=field(430,165,78,"weight",text(e,"weight","1"),7);
            button(342,201,166,20,tr("apply"),()->{if(capture())rebuildWidgets();});
            button(342,229,166,20,tr("remove"),()->{if(capture()){entries().remove(selected);selected=-1;dirty=true;rebuildWidgets();}});
        }
        button(342,289,78,22,tr("save"),()->{if(capture())request(1,originalId,draft.toString());}).active=editable&&!waiting;
        button(426,289,82,22,tr("discard"),()->{dirty=false;request(0,originalId,"");});
    }
    private void toggle(String key,boolean fallback){if(capture()){draft.addProperty(key,!flag(draft,key,fallback));dirty=true;rebuildWidgets();}}
    private boolean capture(){
        if(draft==null||picking)return true;
        try{
            if(idField!=null)draft.addProperty("id",idField.getValue());
            if(nameField!=null)draft.addProperty("name",nameField.getValue());
            if(priceField!=null)draft.addProperty("price",Long.parseLong(priceField.getValue()));
            if(stageField!=null)draft.addProperty("stage",stageField.getValue());
            if(countField!=null&&selected>=0&&selected<entries().size()){
                int count=Integer.parseInt(countField.getValue()),weight=Integer.parseInt(weightField.getValue());
                if(count<1||count>2304||weight<1||weight>1_000_000)throw new IllegalArgumentException();
                JsonObject e=entries().get(selected).getAsJsonObject();e.addProperty("count",count);e.addProperty("weight",weight);
            }
            return true;
        }catch(RuntimeException ex){status="invalid";return false;}
    }
    private boolean leaveDraft(){if(dirty){status="unsaved";return false;}return !waiting;}
    private void prizePage(int delta){if(capture()){prizePage=Math.clamp(prizePage+delta,0,Math.max(0,(entries().size()-1)/7));rebuildWidgets();}}
    private void newPool(){
        draft=new JsonObject();draft.addProperty("id","new_box");draft.addProperty("price",64);draft.addProperty("stage","");
        draft.addProperty("enabled",true);draft.addProperty("allowModdedItems",false);draft.addProperty("enforceValueCap",true);draft.add("entries",new JsonArray());
        originalId="";dirty=true;selected=-1;prizePage=0;confirmDelete=false;status="";picking=false;rebuildWidgets();
    }
    private BoxReward reward(JsonObject e){return new BoxReward(e.get("item").getAsString(),text(e,"potion",""),e.has("count")?e.get("count").getAsInt():1);}
    private String chance(JsonObject e){
        long total=0,same=0;BoxReward target=reward(e);
        for(JsonElement element:entries()){
            JsonObject row=element.getAsJsonObject();int weight=row.has("weight")?row.get("weight").getAsInt():1;
            total+=weight;if(reward(row).equals(target))same+=weight;
        }
        return total==0?"0%":String.format(Locale.ROOT,"%.4g%%",same*100D/total);
    }
    private void buildChoices(){
        if(!choices.isEmpty())return;
        for(var item:BuiltInRegistries.ITEM){
            String id=BuiltInRegistries.ITEM.getKey(item).toString();
            if(BoxReward.potionItem(id)){
                for(var potion:BuiltInRegistries.POTION.keySet())if(!potion.getPath().equals("empty"))addChoice(new BoxReward(id,potion.toString(),1));
            }else if(id.equals("minecraft:air")||BoxPrizePolicy.allowed(id)||!id.startsWith("minecraft:")&&!id.startsWith("teamecon:"))addChoice(new BoxReward(id,"",1));
        }
    }
    private void addChoice(BoxReward reward){
        ItemStack icon=reward.stack();String name=icon.isEmpty()?tr("empty").getString():icon.getHoverName().getString();
        choices.add(new Choice(reward,icon,SearchText.of(name,reward.itemKey()+" "+reward.potion())));
    }
    private List<Choice> filtered(){return choices.stream().filter(c->c.search.matches(query))
            .filter(c->c.reward.itemKey().startsWith("minecraft:")||flag(draft,"allowModdedItems",false)).toList();}
    private void buildPicker(){
        search=new EditBox(font,leftPos+136,topPos+47,274,18,tr("search"));search.setMaxLength(128);search.setValue(query);
        search.setResponder(value->{query=value;pickerPage=0;rebuildPickerRows();});addRenderableWidget(search);
        button(422,45,86,22,tr("back"),()->{picking=false;rebuildWidgets();});
        List<Choice> matches=filtered();pickerPage=Math.clamp(pickerPage,0,Math.max(0,(matches.size()-1)/54));
        for(int i=0;i<54;i++){
            int index=pickerPage*54+i;if(index>=matches.size())break;
            Choice choice=matches.get(index);int x=136+i%9*41,y=80+i/9*34;
            var b=new Button(leftPos+x,topPos+y,37,30,choice.icon.isEmpty()?tr("empty"):choice.icon.getHoverName(),ignored->{
                JsonObject e=new JsonObject();e.addProperty("item",choice.reward.itemKey());e.addProperty("count",1);e.addProperty("weight",1);
                if(!choice.reward.potion().isEmpty())e.addProperty("potion",choice.reward.potion());entries().add(e);
                selected=entries().size()-1;prizePage=selected/7;dirty=true;picking=false;rebuildWidgets();
            },supplier->supplier.get()){
                @Override public void renderWidget(GuiGraphics g,int mx,int my,float partial){
                    g.fill(getX(),getY(),getX()+width,getY()+height,isHoveredOrFocused()?0xFF7793A4:0xFF394650);
                    if(choice.icon.isEmpty())g.drawCenteredString(font,"—",getX()+18,getY()+10,0xFFFFFFFF);
                    else g.renderItem(choice.icon,getX()+10,getY()+7);
                }
            };
            b.setTooltip(Tooltip.create(b.getMessage().copy().append("\n"+choice.reward.itemKey()+" "+choice.reward.potion())));addRenderableWidget(b);
        }
        button(136,291,30,20,Component.literal("‹"),()->{pickerPage=Math.max(0,pickerPage-1);rebuildWidgets();});
        button(478,291,30,20,Component.literal("›"),()->{pickerPage=Math.min(Math.max(0,(matches.size()-1)/54),pickerPage+1);rebuildWidgets();});
    }
    private void rebuildPickerRows(){
        // Rebuilding preserves the query and restores keyboard focus on the replacement field.
        rebuildWidgets();if(search!=null){setFocused(search);search.setFocused(true);search.setCursorPosition(query.length());}
    }
    private void request(int action,String id,String json){
        if(waiting)return;waiting=true;confirmDelete=false;
        ClientPayloadSender.sendToServer(new BoxAdminActionPayload(menu.containerId,++sequence,revision,action,id,json));
    }
    public static void receive(BoxAdminSyncPayload payload){
        if(Minecraft.getInstance().screen instanceof BoxAdminScreen screen&&screen.menu.containerId==payload.containerId())screen.apply(payload);
    }
    private void apply(BoxAdminSyncPayload payload){
        if(payload.sequence()!=sequence)return;
        waiting=false;status=payload.message();headers=JsonParser.parseString(payload.headers()).getAsJsonArray();editable=payload.editable();
        if(Set.of("invalid","io_error","busy").contains(status)){capture();rebuildWidgets();return;}
        if(status.equals("conflict")){capture();editable=false;rebuildWidgets();return;}
        revision=payload.revision();draft=payload.pool().isEmpty()?null:JsonParser.parseString(payload.pool()).getAsJsonObject();
        originalId=draft==null?"":draft.get("id").getAsString();selected=-1;dirty=false;picking=false;prizePage=0;rebuildWidgets();
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF18232C);
        g.fill(leftPos+130,topPos+29,leftPos+imageWidth-6,topPos+imageHeight-16,0xFF253440);
        label(g,title,10,12,460);label(g,tr("pools"),8,31,116);
        if(draft!=null&&!picking){
            label(g,tr("id"),136,36,104);label(g,tr("name"),252,36,132);label(g,tr("price"),396,36,112);label(g,tr("stage"),136,77,104);
            label(g,tr("prizes"),136,113,190);
            if(selected>=0&&selected<entries().size()){
                JsonObject e=entries().get(selected).getAsJsonObject();BoxReward reward=reward(e);ItemStack icon=reward.stack();
                if(!icon.isEmpty())g.renderItem(icon,leftPos+344,topPos+118);
                label(g,icon.isEmpty()?tr("empty"):icon.getHoverName(),365,122,143);
                label(g,tr("count"),342,152,72);label(g,tr("weight"),430,152,78);
                label(g,tr("chance",chance(e)),342,188,166);
            }
        }
        if(picking)label(g,tr("search_hint"),172,297,296);
        if(!status.isEmpty())label(g,tr(status),136,318,374);
    }
    private void label(GuiGraphics g,Component text,int x,int y,int width){g.drawString(font,font.plainSubstrByWidth(text.getString(),width),leftPos+x,topPos+y,0xFFE8EEF1,false);}
    private static Component tr(String key,Object...args){return Component.translatable("gui.teamecon.box_admin."+key,args);}
}
