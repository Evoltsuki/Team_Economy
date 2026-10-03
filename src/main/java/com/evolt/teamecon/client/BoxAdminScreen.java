package com.evolt.teamecon.client;

import com.evolt.teamecon.shop.*;
import com.evolt.teamecon.network.payloads.*;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;

/** Chest-style template grid above the real inventory. Templates never become real stacks. */
public final class BoxAdminScreen extends CompactContainerScreen<BoxAdminMenu> {
    public static final int GRID_X=112, GRID_Y=66, GRID_STEP=20, PAGE_SIZE=54;
    private static final int TEXT=UiTheme.BOX.text, MUTED=UiTheme.BOX.muted;
    private JsonArray headers=new JsonArray();
    private BoxPrizeGrid grid=new BoxPrizeGrid(new JsonArray());
    private JsonObject draft;
    private String originalId="",status="";
    private long sequence,revision;
    private int poolPage,prizePage,selected=-1,dragOrigin=-1,lastPaint=-1;
    private double pressX,pressY;
    private boolean started,waiting,editable,dirty,confirmDelete,settings=true,dragging;
    private boolean chanceEdited;
    private EditBox idField,nameField,priceField,stageField,countField,chanceField;
    public BoxAdminScreen(BoxAdminMenu menu,Inventory inventory,Component title){super(menu,inventory,title);}
    public boolean ready(){return editable&&!waiting;}
    public JsonObject currentPool(){capture();return draft==null?null:draft.deepCopy();}
    public int prizePage(){return prizePage;}
    private JsonObject selectedEntry(){return selected<0?null:grid.get(selected);}
    private static Component tr(String key,Object...args){return Component.translatable("gui.teamecon.box_admin."+key,args);}
    private static String text(JsonObject obj,String key,String fallback){return obj.has(key)?obj.get(key).getAsString():fallback;}
    private static boolean flag(JsonObject obj,String key,boolean fallback){return obj.has(key)?obj.get(key).getAsBoolean():fallback;}

    @Override protected void init(){
        imageWidth=468;imageHeight=344;super.init();
        idField=nameField=priceField=stageField=countField=chanceField=null;chanceEdited=false;
        button(443,8,18,18,Component.literal("×"),this::onClose);
        for(int i=0;i<6;i++){
            int index=poolPage*6+i;if(index>=headers.size())break;
            String id=headers.get(index).getAsJsonObject().get("id").getAsString();
            button(10,55+i*24,90,22,BoxNames.display(id,text(headers.get(index).getAsJsonObject(),"name","")),()->{if(leaveDraft())request(0,id,"");},()->id.equals(originalId)).active=!waiting;
        }
        button(10,204,22,18,Component.literal("‹"),()->{poolPage=Math.max(0,poolPage-1);capture();rebuildWidgets();});
        button(78,204,22,18,Component.literal("›"),()->{poolPage=Math.min(Math.max(0,(headers.size()-1)/6),poolPage+1);capture();rebuildWidgets();});
        button(10,234,90,20,tr("new"),()->{if(leaveDraft())newPool();}).active=ready();
        button(10,258,90,20,tr(confirmDelete?"confirm_delete":"delete"),()->{
            if(!confirmDelete){confirmDelete=true;capture();rebuildWidgets();return;}
            request(2,originalId,"");
        }).active=ready()&&!originalId.isEmpty();
        button(10,282,90,20,tr("refresh"),()->{dirty=false;request(3,originalId,"");}).active=!waiting;
        if(draft!=null){
            button(GRID_X,190,22,18,Component.literal("‹"),()->page(-1)).active=prizePage>0;
            button(268,190,22,18,Component.literal("›"),()->page(1)).active=prizePage<2;
            button(308,43,70,20,tr("box_tab"),()->{if(capture()){settings=true;rebuildWidgets();}},()->settings);
            button(382,43,74,20,tr("prize_tab"),()->{if(capture()){settings=false;rebuildWidgets();}},()->!settings).active=selectedEntry()!=null;
            if(settings)buildSettings();else buildPrizeEditor();
            button(308,308,70,20,tr("save"),()->{saveDraft();}).active=ready();
            button(382,308,74,20,tr("discard"),()->{dirty=false;request(0,originalId,"");}).active=!waiting;
        }
        if(!started){started=true;request(0,"","");}
    }
    @Override public void resize(Minecraft minecraft,int width,int height){capture();dragOrigin=-1;dragging=false;super.resize(minecraft,width,height);}
    private Button button(int x,int y,int w,int h,Component label,Runnable action){
        return button(x,y,w,h,label,action,()->false);
    }
    private Button button(int x,int y,int w,int h,Component label,Runnable action,java.util.function.BooleanSupplier selected){
        Button b=UiButton.of(label,ignored->action.run()).bounds(leftPos+x,topPos+y,w,h).selected(selected).theme(UiTheme.BOX).build();
        b.setTooltip(Tooltip.create(label));return addRenderableWidget(b);
    }
    private EditBox field(int x,int y,int w,String key,String value,int length){
        EditBox b=UiTheme.input(font,leftPos+x,topPos+y,w,16,tr(key));b.setMaxLength(length);b.setValue(value);
        b.setResponder(v->{dirty=true;confirmDelete=false;});addRenderableWidget(b);return b;
    }
    private void buildSettings(){
        nameField=field(310,87,142,"name",text(draft,"name",""),64);
        idField=field(310,123,142,"id",text(draft,"id",""),32);
        priceField=field(310,159,142,"price",text(draft,"price","64"),10);
        stageField=field(310,195,142,"stage",text(draft,"stage",""),128);
        button(308,222,148,20,tr(flag(draft,"enabled",true)?"enabled":"disabled"),()->toggle("enabled",true));
        button(308,248,148,20,tr(flag(draft,"allowModdedItems",false)?"mods_on":"mods_off"),()->toggle("allowModdedItems",false));
        button(308,274,148,20,tr(flag(draft,"enforceValueCap",true)?"cap_on":"cap_off"),()->toggle("enforceValueCap",true));
    }
    private void buildPrizeEditor(){
        JsonObject e=selectedEntry();if(e==null)return;
        countField=field(310,159,142,"count",text(e,"count","1"),4);
        chanceEdited=false;
        JsonObject displayed=new BoxPrizeGrid(BoxChances.asPercentages(grid.entries())).get(selected);
        chanceField=field(310,195,142,"percent",BoxChances.format(BoxChances.entryUnits(displayed)),16);
        chanceField.setResponder(value->{dirty=true;confirmDelete=false;chanceEdited=true;status="";});
        button(308,248,148,20,tr("apply"),()->{if(capture())rebuildWidgets();});
        button(308,274,148,20,tr("remove"),()->remove(selected));
    }
    private void toggle(String key,boolean fallback){if(capture()){draft.addProperty(key,!flag(draft,key,fallback));dirty=true;rebuildWidgets();}}
    private boolean capture(){
        if(draft==null)return true;
        try{
            if(idField!=null)draft.addProperty("id",idField.getValue());
            if(nameField!=null)draft.addProperty("name",nameField.getValue());
            if(priceField!=null)draft.addProperty("price",Long.parseLong(priceField.getValue()));
            if(stageField!=null)draft.addProperty("stage",stageField.getValue());
            if(countField!=null&&selectedEntry()!=null){
                int count=Integer.parseInt(countField.getValue());
                if(count<1||count>2304)throw new IllegalArgumentException();
                if(chanceEdited){
                    int value;
                    try{value=BoxChances.parse(chanceField.getValue());}
                    catch(RuntimeException ex){status="percent_invalid";return false;}
                    preparePercentages();
                    grid=new BoxPrizeGrid(BoxChances.rebalance(grid.entries(),selected,value));chanceEdited=false;
                }
                selectedEntry().addProperty("count",count);
            }
            draft.add("entries",grid.entries());return true;
        }catch(RuntimeException ex){status="invalid";return false;}
    }
    private void preparePercentages(){grid=new BoxPrizeGrid(BoxChances.asPercentages(grid.entries()));}
    private void saveDraft(){
        if(!capture())return;
        if(BoxChances.totalUnits(grid.entries())!=BoxChances.TOTAL){status="total_invalid";return;}
        request(1,originalId,draft.toString());
    }
    private long allocated(){return BoxChances.totalUnits(grid.entries());}
    private boolean leaveDraft(){if(dirty){status="unsaved";return false;}return !waiting;}
    private void page(int delta){if(capture()){prizePage=Math.clamp(prizePage+delta,0,2);rebuildWidgets();}}
    private void newPool(){
        draft=new JsonObject();draft.addProperty("id","new_box");draft.addProperty("price",64);draft.addProperty("stage","");
        draft.addProperty("enabled",true);draft.addProperty("allowModdedItems",false);draft.addProperty("enforceValueCap",true);
        grid=new BoxPrizeGrid(new JsonArray());draft.add("entries",grid.entries());
        originalId="";dirty=true;selected=-1;prizePage=0;confirmDelete=false;status="";settings=true;rebuildWidgets();
    }
    private BoxReward reward(JsonObject e){return new BoxReward(e.get("item").getAsString(),text(e,"potion",""),e.has("count")?e.get("count").getAsInt():1);}
    private String chance(JsonObject e){
        long total=0,same=0;BoxReward target=reward(e);JsonArray entries=grid.entries();
        boolean percentages=BoxChances.usesPercentages(entries);
        for(JsonElement element:entries){
            JsonObject row=element.getAsJsonObject();int weight=percentages?BoxChances.entryUnits(row):row.has("weight")?row.get("weight").getAsInt():1;
            total+=weight;if(reward(row).equals(target))same+=weight;
        }
        return percentages?BoxChances.format(same)+"%":total==0?"0%":String.format(Locale.ROOT,"%.6f%%",same*100D/total);
    }
    private void remove(int slot){
        if(!ready()||slot<0||!capture())return;
        preparePercentages();grid.set(slot,null);grid=new BoxPrizeGrid(BoxChances.rebalance(grid.entries(),-1,0));dirty=true;selected=-1;settings=true;status="";rebuildWidgets();
    }
    private void place(BoxReward reward,int slot){
        if(slot<0){status="full";return;}
        preparePercentages();
        boolean added=grid.get(slot)==null;
        int previous=added?BoxChances.TOTAL/(grid.entries().size()+1):BoxChances.entryUnits(grid.get(slot));
        JsonObject entry=new JsonObject();entry.addProperty("item",reward.itemKey());entry.addProperty("count",reward.count());entry.addProperty("chance",BoxChances.percent(previous));
        if(!reward.potion().isEmpty())entry.addProperty("potion",reward.potion());
        grid.set(slot,entry);if(added)grid=new BoxPrizeGrid(BoxChances.rebalance(grid.entries(),slot,previous));selected=slot;prizePage=slot/PAGE_SIZE;dirty=true;settings=false;status="sample_added";rebuildWidgets();
    }
    private void copySample(ItemStack stack,int slot,boolean single){
        if(!ready()||draft==null||stack.isEmpty()||!capture())return;
        try{
            BoxReward r=BoxReward.fromStack(stack);
            if(!BoxPrizePolicy.allowed(r.itemKey())){
                status=r.itemKey().startsWith("minecraft:")||r.itemKey().startsWith("teamecon:")?"unsupported_item":"mods_required";
                if(!status.equals("mods_required")||!flag(draft,"allowModdedItems",false))return;
            }
            place(single?r.withCount(1):r,slot);
        }catch(RuntimeException ex){status="unsupported_sample";}
    }
    /** JEI drops are templates, just like an inventory sample. No cheat-mode item grant. */
    public void acceptJeiSample(ItemStack stack,int slot){
        if(slot>=0&&slot<BoxPrizeGrid.CAPACITY)copySample(stack.copy(),slot,false);
    }
    public net.minecraft.client.renderer.Rect2i prizeArea(int slot){
        int cell=slot-prizePage*PAGE_SIZE;
        return screenArea(GRID_X+cell%9*GRID_STEP,GRID_Y+cell/9*GRID_STEP,18,18);
    }
    @Override protected int reservedRightWidth(){return net.neoforged.fml.ModList.get().isLoaded("jei")?72:0;}
    @Override protected void slotClicked(Slot slot,int slotId,int button,ClickType type){
        if(type==ClickType.QUICK_MOVE&&slot!=null){copySample(slot.getItem(),grid.firstEmpty(),false);return;}
        super.slotClicked(slot,slotId,button,type);
    }
    private int gridAt(double x,double y){
        int col=(int)Math.floor((x-leftPos-GRID_X)/GRID_STEP),row=(int)Math.floor((y-topPos-GRID_Y)/GRID_STEP);
        if(col<0||col>=9||row<0||row>=6)return -1;
        int slot=prizePage*PAGE_SIZE+row*9+col;return slot<BoxPrizeGrid.CAPACITY?slot:-1;
    }
    @Override protected boolean clickPanel(double x,double y,int button){
        int slot=gridAt(x,y);
        if(draft!=null&&slot>=0&&(button==0||button==1)){
            if(!ready())return true;
            if(!menu.getCarried().isEmpty()){copySample(menu.getCarried(),slot,button==1);lastPaint=slot;return true;}
            if(button==1){remove(slot);return true;}
            if(capture()){
                selected=grid.get(slot)==null?-1:slot;settings=selected<0;
                dragOrigin=selected;pressX=x;pressY=y;dragging=false;status="";rebuildWidgets();
            }
            return true;
        }
        dragOrigin=-1;dragging=false;lastPaint=-1;
        return super.clickPanel(x,y,button);
    }
    @Override protected boolean dragPanel(double x,double y,int button,double dx,double dy){
        if(dragOrigin>=0&&button==0){dragging|=Math.abs(x-pressX)+Math.abs(y-pressY)>3;return true;}
        int slot=gridAt(x,y);
        if(ready()&&slot>=0&&!menu.getCarried().isEmpty()){
            if(slot!=lastPaint){copySample(menu.getCarried(),slot,button==1);lastPaint=slot;}
            return true;
        }
        return super.dragPanel(x,y,button,dx,dy);
    }
    @Override public boolean mouseReleased(double x,double y,int button){
        int slot=gridAt(x/panelScale(),y/panelScale());
        if(dragOrigin>=0){
            if(dragging&&ready()&&slot>=0&&slot!=dragOrigin&&capture()){
                grid.swap(dragOrigin,slot);selected=slot;dirty=true;rebuildWidgets();
            }
            dragOrigin=-1;dragging=false;return true;
        }
        if(slot>=0&&slot!=lastPaint&&!menu.getCarried().isEmpty())copySample(menu.getCarried(),slot,button==1);
        lastPaint=-1;
        return super.mouseReleased(x,y,button);
    }

    private void request(int action,String id,String json){
        if(waiting)return;waiting=true;confirmDelete=false;dragOrigin=-1;dragging=false;
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
        grid=new BoxPrizeGrid(draft==null?new JsonArray():draft.getAsJsonArray("entries"));
        originalId=draft==null?"":draft.get("id").getAsString();selected=-1;dirty=false;settings=true;prizePage=0;rebuildWidgets();
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){}
    private void slotBackground(GuiGraphics g,int x,int y,boolean selected){
        UiTheme.BOX.slot(g,x+1,y+1);
        if(selected)g.renderOutline(x-1,y-1,20,20,UiTheme.BOX.accent);
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        int x=leftPos,y=topPos;
        UiTheme.BOX.panel(g,x,y,imageWidth,imageHeight);
        UiTheme.BOX.section(g,x+6,y+39,98,290);UiTheme.BOX.section(g,x+302,y+39,158,290);
        g.renderItem(new ItemStack(Items.CHEST),x+11,y+11);label(g,title,33,12,395,TEXT);
        label(g,tr("template_subtitle"),12,29,420,MUTED);label(g,tr("pools"),10,43,90,TEXT);
        for(int i=0;i<PAGE_SIZE;i++){
            int slot=prizePage*PAGE_SIZE+i,dx=x+GRID_X+i%9*GRID_STEP,dy=y+GRID_Y+i/9*GRID_STEP;
            slotBackground(g,dx,dy,slot==selected);
            if(slot>=BoxPrizeGrid.CAPACITY){g.fill(dx+1,dy+1,dx+17,dy+17,UiTheme.BOX.disabled);continue;}
            if(draft!=null&&grid.get(slot)!=null){
                BoxReward prize=reward(grid.get(slot));ItemStack icon=prize.stack();if(icon.isEmpty())icon=new ItemStack(Items.PAPER);
                g.renderItem(icon,dx+1,dy+1);g.renderItemDecorations(font,icon,dx+1,dy+1,prize.count()==1?"":String.valueOf(prize.count()));
            }
            if(gridAt(mx,my)==slot)g.fill(dx+1,dy+1,dx+17,dy+17,0x408B6FA0);
        }
        label(g,Component.translatable("container.inventory"),112,216,180,TEXT);
        for(Slot slot:menu.slots)slotBackground(g,x+slot.x-1,y+slot.y-1,false);
        if(draft!=null){
            label(g,tr("prizes"),112,49,102,TEXT);label(g,tr("grid_page",prizePage+1,3),142,195,118,MUTED);
            if(settings){
                label(g,tr("name"),310,74,142,TEXT);label(g,tr("id"),310,110,142,TEXT);
                label(g,tr("price"),310,146,142,TEXT);label(g,tr("stage"),310,182,142,TEXT);
            }else if(selectedEntry()!=null){
                JsonObject e=selectedEntry();ItemStack icon=reward(e).stack();
                UiTheme.BOX.section(g,x+310,y+77,36,36);if(!icon.isEmpty())g.renderItem(icon,x+320,y+87);
                label(g,icon.isEmpty()?tr("empty"):icon.getHoverName(),310,123,142,TEXT);
                label(g,tr("count"),310,146,142,TEXT);label(g,tr("percent"),310,182,142,TEXT);
                long used=allocated();
                label(g,tr("allocated",BoxChances.format(used)),310,220,142,used>BoxChances.TOTAL?UiTheme.NEGATIVE:TEXT);
                label(g,tr("auto_balance"),310,234,142,MUTED);
            }
        }
        Component footer=status.isEmpty()?draft==null?tr("inventory_hint"):tr("auto_footer"):tr(status);
        label(g,footer,10,330,448,status.equals("unsupported_sample")||status.equals("unsupported_item")||status.equals("mods_required")||status.equals("invalid")||status.equals("percent_invalid")||status.equals("total_invalid")?UiTheme.NEGATIVE:MUTED);
    }
    @Override protected void renderPanel(GuiGraphics g,int mx,int my,float partial){
        super.renderPanel(g,mx,my,partial);
        renderTooltip(g,mx,my);
        int slot=gridAt(mx,my);
        if(!dragging&&menu.getCarried().isEmpty()&&draft!=null&&slot>=0&&grid.get(slot)!=null){
            JsonObject e=grid.get(slot);ItemStack icon=reward(e).stack();
            List<Component> lines=new ArrayList<>();lines.add(icon.isEmpty()?tr("empty"):icon.getHoverName());
            lines.add(tr("chance",chance(e)));lines.add(tr("slot_hint"));g.renderComponentTooltip(font,lines,mx,my);
        }
        if(dragging&&dragOrigin>=0&&grid.get(dragOrigin)!=null){
            ItemStack icon=reward(grid.get(dragOrigin)).stack();
            g.pose().pushPose();g.pose().translate(0,0,300);g.renderItem(icon,mx-8,my-8);g.pose().popPose();
        }
    }
    private void label(GuiGraphics g,Component text,int x,int y,int width,int color){g.drawString(font,font.plainSubstrByWidth(text.getString(),width),leftPos+x,topPos+y,color,false);}
}
