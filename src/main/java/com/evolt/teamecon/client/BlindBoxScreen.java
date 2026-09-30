package com.evolt.teamecon.client;

import com.evolt.teamecon.economy.MoneyMath;
import com.evolt.teamecon.network.ModNetwork;
import com.evolt.teamecon.network.payloads.ShopActionPayload;
import com.evolt.teamecon.shop.ShopMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** A dedicated opening counter with batch selection and the server's actual prize receipt. */
public final class BlindBoxScreen extends CompactContainerScreen<ShopMenu> {
    private static final int GOLD=0xFFF1D48E, WHITE=0xFFF2EEF6, DULL=0xFFADA9BE, LINE=0xFF51435F;
    private int amount=1, poolPage, rewardPage, leftWidth, waiting, revealTicks;
    private long sequence, revision=-1;
    private String poolId="";
    private List<ClientShopCache.PrizeRow> receipt=List.of();
    private final List<Button> amountButtons=new ArrayList<>();
    private Button open;
    private boolean preview=true;
    private String receiptPool="", requestedPool="";

    public BlindBoxScreen(ShopMenu menu, Inventory inventory, Component title) { super(menu,inventory,title); }
    private boolean ready() { return ClientShopCache.containerId()==menu.containerId; }
    private ClientShopCache.BoxRow pool() {
        return ClientShopCache.boxes().stream().filter(p->p.poolId().equals(poolId)).findFirst().orElse(null);
    }
    private int poolCapacity() { return Math.max(1,(imageHeight-97)/38); }
    private int prizeColumns() { return Math.max(1,(imageWidth-leftWidth-38)/28); }
    private int prizeCapacity() { return prizeColumns()*Math.max(1,(imageHeight-194)/28); }

    @Override protected void init() {
        imageWidth=360;imageHeight=260;super.init();
        menu.showSales(false);leftWidth=imageWidth>=380?126:100;amountButtons.clear();
        var pools=ready()?ClientShopCache.boxes():List.<ClientShopCache.BoxRow>of();
        if(pool()==null&&!pools.isEmpty())poolId=pools.getFirst().poolId();
        poolPage=Math.clamp(poolPage,0,Math.max(0,(pools.size()-1)/poolCapacity()));
        int start=poolPage*poolCapacity();
        for(int i=start;i<Math.min(pools.size(),start+poolCapacity());i++){
            var entry=pools.get(i);
            var button=addRenderableWidget(new BoxButton(12,52+(i-start)*38,leftWidth,34,poolName(entry.poolId()),()->{
                poolId=entry.poolId();preview=true;rewardPage=0;rebuildWidgets();
            }){
                @Override protected boolean chosen(){return poolId.equals(entry.poolId());}
                @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
                    frame(g);fit(g,getMessage(),getX()+6,getY()+5,width-12,WHITE);
                    fit(g,tr("unit_price",CasinoScreen.compact(entry.price())),getX()+6,getY()+20,width-12,GOLD);
                }
            });
            button.setTooltip(Tooltip.create(poolName(entry.poolId())));
            button.active=waiting==0;
        }
        if(pools.size()>poolCapacity()){
            addRenderableWidget(new BoxButton(12,imageHeight-39,22,18,Component.literal("‹"),()->{poolPage--;rebuildWidgets();})).active=poolPage>0;
            addRenderableWidget(new BoxButton(leftWidth-10,imageHeight-39,22,18,Component.literal("›"),()->{poolPage++;rebuildWidgets();})).active=(start+poolCapacity())<pools.size();
        }
        int x=leftWidth+26,w=imageWidth-x-14,buttonWidth=(w-8)/3;
        for(int i=0;i<3;i++){
            int count=new int[]{1,10,64}[i];
            amountButtons.add(addRenderableWidget(new BoxButton(x+i*(buttonWidth+4),imageHeight-66,buttonWidth,18,Component.literal("×"+count),()->{amount=count;refresh();}){
                @Override protected boolean chosen(){return amount==count;}
            }));
        }
        open=addRenderableWidget(new BoxButton(x,imageHeight-42,w,22,tr("open"),()->{
            var selected=pool();if(selected==null||waiting>0)return;
            waiting=100;requestedPool=selected.poolId();
            ClientPayloadSender.sendToServer(new ShopActionPayload(menu.containerId,++sequence,ShopActionPayload.Kind.BUY_BOX,selected.poolId(),amount));
            refresh();
        }){
            @Override protected boolean chosen(){return active;}
        });
        int tabWidth=(w-4)/2;
        addRenderableWidget(new BoxButton(x,68,tabWidth,18,tr("contents"),()->{preview=true;rewardPage=0;}){
            @Override protected boolean chosen(){return preview;}
        });
        addRenderableWidget(new BoxButton(x+tabWidth+4,68,tabWidth,18,tr("receipt"),()->{preview=false;rewardPage=0;}){
            @Override protected boolean chosen(){return !preview;}
        }).active=!receipt.isEmpty();
        addRenderableWidget(new BoxButton(x,imageHeight-100,18,16,Component.literal("‹"),()->turnPage(-1)));
        addRenderableWidget(new BoxButton(x+w-18,imageHeight-100,18,16,Component.literal("›"),()->turnPage(1)));
        refresh();
    }
    private List<ClientShopCache.PrizeRow> displayed(){
        var selected=pool();
        return preview ? selected==null?List.of():selected.prizes().stream().map(p->new ClientShopCache.PrizeRow(p.itemKey(),p.count())).toList() : receipt;
    }
    private void turnPage(int delta){rewardPage=Math.clamp(rewardPage+delta,0,Math.max(0,(displayed().size()-1)/prizeCapacity()));}
    private void refresh(){
        var selected=pool();long cost=selected==null?0:MoneyMath.total(selected.price(),amount);
        if(open!=null)open.active=ready()&&waiting==0&&selected!=null&&selected.unlocked()&&cost>0&&ClientShopCache.balance()>=cost;
        for(Button button:amountButtons)button.active=waiting==0;
    }
    @Override public void containerTick(){
        super.containerTick();if(waiting>0)waiting--;if(revealTicks>0)revealTicks--;
        if(ready()&&revision!=ClientShopCache.revision()){
            revision=ClientShopCache.revision();waiting=0;
            if(!ClientShopCache.rewards().isEmpty()&&receipt!=ClientShopCache.rewards()){
                receipt=ClientShopCache.rewards();receiptPool=requestedPool;preview=false;revealTicks=16;rewardPage=0;
            }
            rebuildWidgets();
        }
        refresh();
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        int x=leftPos,y=topPos;
        g.fill(x,y,x+imageWidth,y+imageHeight,LINE);
        g.fill(x+1,y+1,x+imageWidth-1,y+imageHeight-1,0xFF1D1929);
        g.fillGradient(x+1,y+1,x+imageWidth-1,y+42,0xFF3D304A,0xFF211B2B);
        g.fill(x+leftWidth+20,y+49,x+leftWidth+21,y+imageHeight-19,LINE);
        g.fill(x+12,y+imageHeight-17,x+imageWidth-12,y+imageHeight-16,LINE);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        fit(g,tr("title"),14,11,imageWidth-135,GOLD);
        String balance=CasinoScreen.compact(ready()?ClientShopCache.balance():0);
        g.drawString(font,balance,imageWidth-14-font.width(balance),12,GOLD,false);
        fit(g,tr("subtitle"),14,28,imageWidth-28,DULL);
        int x=leftWidth+28,w=imageWidth-x-16,cx=x+w/2;
        var selected=pool();
        fit(g,preview?(selected==null?tr("empty"):poolName(selected.poolId())):poolName(receiptPool),x,53,w,WHITE);
        var shown=displayed();int capacity=prizeCapacity();
        rewardPage=Math.clamp(rewardPage,0,Math.max(0,(shown.size()-1)/capacity));
        int start=rewardPage*capacity;
        for(int i=start;i<Math.min(shown.size(),start+capacity);i++){
            var prize=shown.get(i);int dx=x+(i-start)%prizeColumns()*28,dy=94+(i-start)/prizeColumns()*28;
            boolean rare=preview&&com.evolt.teamecon.shop.BoxPrizePolicy.exclusive(prize.itemKey());
            g.fill(dx,dy,dx+25,dy+25,rare?0xFF775E35:0xFF30273C);
            var id=ResourceLocation.tryParse(prize.itemKey());
            ItemStack icon=new ItemStack(id==null||prize.itemKey().equals("minecraft:air")?Items.PAPER:BuiltInRegistries.ITEM.get(id));
            g.renderItem(icon,dx+4,dy+1);
            String count=prize.itemKey().equals("minecraft:air")?"—":String.valueOf(prize.count());
            g.pose().pushPose();g.pose().translate(dx+12,dy+18,0);g.pose().scale(.625F,.625F,1);
            g.drawString(font,count,-font.width(count)/2,0,GOLD,false);g.pose().popPose();
        }
        fit(g,tr("page",rewardPage+1,Math.max(1,(shown.size()+capacity-1)/capacity)),x+22,imageHeight-96,w-44,DULL);
        long cost=selected==null?0:MoneyMath.total(selected.price(),amount);
        fit(g,tr("total",amount,CasinoScreen.compact(cost)),x,imageHeight-80,w,GOLD);
        Component message=ready()?ModNetwork.formatMessage(ClientShopCache.messageKey(),ClientShopCache.messageArgs()):tr("loading");
        fit(g,message.getString().isEmpty()?tr("delivery"):message,12,imageHeight-11,imageWidth-24,DULL);
    }
    @Override protected void renderPanel(GuiGraphics g,int mx,int my,float partial){
        super.renderPanel(g,mx,my,partial);
        var shown=displayed();int start=rewardPage*prizeCapacity(),x=leftPos+leftWidth+28;
        for(int i=start;i<Math.min(shown.size(),start+prizeCapacity());i++){
            int dx=x+(i-start)%prizeColumns()*28,dy=topPos+94+(i-start)/prizeColumns()*28;
            if(mx>=dx&&mx<dx+25&&my>=dy&&my<dy+25){
                var prize=shown.get(i);var id=ResourceLocation.tryParse(prize.itemKey());
                Component name=prize.itemKey().equals("minecraft:air")?tr("no_prize"):new ItemStack(BuiltInRegistries.ITEM.get(id)).getHoverName();
                var lines=new ArrayList<Component>();lines.add(name.copy().append(" ×"+prize.count()));
                if(preview){
                    lines.add(tr("one_prize"));
                    var selected = pool();
                    if (selected != null) {
                        int total = selected.prizes().stream().mapToInt(ClientShopCache.BoxPrize::weight).sum();
                        int weight = selected.prizes().stream().filter(p -> p.itemKey().equals(prize.itemKey()) && p.count() == prize.count())
                                .mapToInt(ClientShopCache.BoxPrize::weight).sum();
                        if (total > 0) lines.add(tr("chance", String.format(java.util.Locale.ROOT, "%.3f%%", weight * 100D / total)));
                    }
                    if(com.evolt.teamecon.shop.BoxPrizePolicy.exclusive(prize.itemKey()))lines.add(tr("rare_prize"));
                }
                g.renderComponentTooltip(font,lines,mx,my);
            }
        }
    }
    @Override protected boolean scrollPanel(double x,double y,double dx,double dy){
        if(x>=leftPos+leftWidth+20){turnPage(dy<0?1:-1);return true;}
        if(x>=leftPos&&x<leftPos+leftWidth+20){poolPage+=dy<0?1:-1;rebuildWidgets();return true;}
        return super.scrollPanel(x,y,dx,dy);
    }
    private class BoxButton extends Button {
        BoxButton(int x,int y,int w,int h,Component text,Runnable action){super(leftPos+x,topPos+y,w,h,text,b->action.run(),DEFAULT_NARRATION);}
        protected boolean chosen(){return false;}
        protected void frame(GuiGraphics g){
            g.fill(getX(),getY(),getX()+width,getY()+height,chosen()?GOLD:isHoveredOrFocused()?0xFFAF8CC4:LINE);
            g.fill(getX()+1,getY()+1,getX()+width-1,getY()+height-1,active?chosen()?0xFF654B75:0xFF342B41:0xFF262231);
        }
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){frame(g);String value=font.plainSubstrByWidth(getMessage().getString(),width-8);g.drawString(font,value,getX()+(width-font.width(value))/2,getY()+(height-8)/2,active?WHITE:DULL,false);}
    }
    private void fit(GuiGraphics g,Component text,int x,int y,int w,int color){g.drawString(font,font.plainSubstrByWidth(text.getString(),Math.max(1,w)),x,y,color,false);}
    private static Component poolName(String id){String key="shop.teamecon.pool."+id;return net.minecraft.client.resources.language.I18n.exists(key)?Component.translatable(key):Component.literal(id);}
    private static Component tr(String key,Object...args){return Component.translatable("gui.teamecon.boxes."+key,args);}
}
