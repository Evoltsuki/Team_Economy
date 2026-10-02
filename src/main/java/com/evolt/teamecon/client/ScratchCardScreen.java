package com.evolt.teamecon.client;

import com.evolt.teamecon.network.payloads.ScratchActionPayload;
import com.evolt.teamecon.network.payloads.ScratchResultPayload;
import com.evolt.teamecon.scratch.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;

/** Physical scratch ticket. Removing the coating automatically requests a single server settlement. */
public final class ScratchCardScreen extends CompactContainerScreen<ScratchCardMenu> {
    private static final int INK=UiTheme.TEXT, CELL_INK=0xFF253544, MUTED=UiTheme.MUTED, GOLD=UiTheme.ACCENT;
    private static final Map<UUID,BitSet> PROGRESS=new LinkedHashMap<>();
    private static final Item[] FRUIT={Items.APPLE,Items.SWEET_BERRIES,Items.MELON_SLICE,Items.CARROT,Items.CHORUS_FRUIT,Items.GOLDEN_APPLE};
    private final ScratchTicket ticket;
    private final ScratchBoard board;
    private final BitSet removed;
    private final List<Area> areas=new ArrayList<>();
    private int totalMask, ticks, retryAt;
    private boolean requested, answered;
    private long paid=-1,balance;
    private record Area(int x,int y,int width,int height,int offset) {
        int columns(){return (width+3)/4;} int rows(){return (height+3)/4;} int count(){return columns()*rows();}
    }
    public ScratchCardScreen(ScratchCardMenu menu,Inventory inventory,Component title) {
        super(menu,inventory,title);imageWidth=320;imageHeight=240;
        ticket=menu.ticket();board=ScratchBoard.create(ticket);
        if(PROGRESS.size()>64)PROGRESS.remove(PROGRESS.keySet().iterator().next());
        removed=PROGRESS.computeIfAbsent(ticket.id(),id->new BitSet());
        layout();
    }
    public static void clear(){PROGRESS.clear();}
    public static void receive(ScratchResultPayload p) {
        if(Minecraft.getInstance().screen instanceof ScratchCardScreen screen
                &&screen.menu.containerId==p.containerId()&&screen.ticket.id().equals(p.serial())) {
            screen.answered=true;screen.paid=p.amount();screen.balance=p.balance();PROGRESS.remove(p.serial());
            screen.menu.settled(p.amount());
            if(p.amount()>0&&screen.minecraft.player!=null)screen.minecraft.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP,.65F,1.15F);
        }
    }
    private void addArea(int x,int y,int w,int h){Area area=new Area(x,y,w,h,totalMask);areas.add(area);totalMask+=area.count();}
    private void layout() {
        for(int i=0;i<board.size();i++)switch(ticket.kind()) {
            case MATCH -> addArea(24+i%3*61,79+i/3*49,55,43);
            case DICE -> addArea(37+i%2*56,76+i/2*35,42,30);
            case FRUIT,VAULT -> addArea(25+i%3*44,76+i/3*35,39,30);
            case SEVENS -> addArea(24+i%3*61,76+i/3*35,55,30);
            case GEMS,CROWN -> addArea(24+i%4*46,76+i/4*35,40,30);
            case BINGO -> addArea(45+i%3*49,76+i/3*35,42,30);
        }
    }
    @Override protected void init() {
        super.init();
        addRenderableWidget(UiButton.of(Component.literal("×"),b->onClose()).bounds(leftPos+292,topPos+7,19,18).build());
    }
    @Override public void containerTick(){
        super.containerTick();ticks++;
        if(removed.cardinality()>=totalMask&&!answered&&ticks>=retryAt)requestSettlement();
    }
    private void requestSettlement(){
        requested=true;retryAt=ticks+40;
        ClientPayloadSender.sendToServer(new ScratchActionPayload(false,menu.containerId,ticket.id()));
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){
        UiTheme.panel(g,leftPos,topPos,imageWidth,imageHeight);
        g.fill(leftPos+12,topPos+10,leftPos+16,topPos+23,ticket.kind().color());
    }

    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY) {
        fit(g,title.getString(),23,12,261,INK);
        String rule=tr("rule."+ticket.kind().id()).getString();
        var lines=font.split(Component.literal(rule),280);
        for(int i=0;i<Math.min(2,lines.size());i++)g.drawString(font,lines.get(i),23,33+i*10,INK,false);
        UiTheme.section(g,17,55,197,129);
        Component subtitle=switch(ticket.kind()) {
            case MATCH -> tr("target",board.target());
            case VAULT -> tr("code",String.format(Locale.ROOT,"%03d",board.target()));
            case BINGO -> tr("line_prize",board.prize(0)*ticket.price());
            case GEMS,CROWN -> tr("collect");
            case DICE -> tr("sum_seven");
            case FRUIT -> tr("three_match");
            case SEVENS -> tr("find_seven");
        };
        UiTheme.centered(g,font,subtitle,115,61,GOLD);
        for(int i=0;i<areas.size();i++){
            Area a=areas.get(i);boolean uncovered=removed.get(a.offset,a.offset+a.count()).cardinality()==a.count();
            g.fill(a.x,a.y,a.x+a.width,a.y+a.height,0xFFF8F3E6);
            if(uncovered&&board.winningCell(i))g.renderOutline(a.x,a.y,a.width,a.height,0xFF26B980);
            // Covered prizes must not render through the foil. Item icons use a raised GUI
            // depth (150), and text may be buffered, so explicitly draw the coating last.
            if(!removed.get(a.offset,a.offset+a.count()).isEmpty())drawValue(g,i,a);
            g.flush();g.pose().pushPose();g.pose().translate(0,0,400);
            for(int cell=0;cell<a.count();cell++)if(!removed.get(a.offset+cell)){
                int x=a.x+(cell%a.columns())*4,y=a.y+(cell/a.columns())*4;
                int tone=(cell+cell/a.columns())%3;
                g.fill(x,y,Math.min(x+4,a.x+a.width),Math.min(y+4,a.y+a.height),tone==0?0xFFB8C5CE:tone==1?0xFFCCD5D9:0xFFAEBBC3);
            }
            if(removed.get(a.offset,a.offset+a.count()).isEmpty()){
                g.drawString(font,"◇",a.x+(a.width-font.width("◇"))/2,a.y+a.height/2-4,0xFF667B89,false);
            }
            g.flush();g.pose().popPose();
        }
        if(ticket.kind()==ScratchKind.DICE||ticket.kind()==ScratchKind.FRUIT||ticket.kind()==ScratchKind.VAULT)
            for(int row=0;row<3;row++)UiTheme.centered(g,font,"×"+board.prize(row),181,88+row*35,GOLD);
        g.drawString(font,tr("price",ticket.price()),224,58,INK,false);
        g.drawString(font,tr("paytable"),224,77,INK,false);
        int[] multipliers=ticket.kind().multipliers();
        for(int i=0;i<multipliers.length;i++){
            String label=(ticket.kind()==ScratchKind.GEMS||ticket.kind()==ScratchKind.CROWN)
                    ?(i==0?"0–2":String.valueOf(i+2))+"  ×"+multipliers[i]
                    :"×"+multipliers[i];
            g.drawString(font,label,224,91+i*12,INK,false);
        }
        fit(g,tr("max_prize",ticket.price()*multipliers[multipliers.length-1]).getString(),224,169,80,MUTED);
        UiTheme.section(g,17,189,287,24);
        String status=answered?(paid<0?tr("invalid").getString():paid>0?tr("credited",paid,balance).getString():tr("no_prize",balance).getString())
                :requested?tr("settling").getString():tr("automatic").getString();
        fit(g,status,24,197,274,answered&&paid<0?UiTheme.NEGATIVE:answered&&paid>0?UiTheme.POSITIVE:INK);
        int percent=Math.min(100,removed.cardinality()*100/Math.max(1,totalMask));
        fit(g,answered?tr("finished").getString():tr("drag",percent).getString(),23,220,280,MUTED);
    }
    private void drawValue(GuiGraphics g,int i,Area a) {
        int value=board.value(i),cx=a.x+a.width/2,cy=a.y+a.height/2;
        switch(ticket.kind()) {
            case MATCH -> {number(g,String.valueOf(value),cx,a.y+5,1.8F);String prize="×"+board.prize(i);g.drawString(font,prize,cx-font.width(prize)/2,a.y+28,CELL_INK,false);}
            case SEVENS -> {number(g,String.valueOf(value),a.x+15,a.y+6,1.8F);g.drawString(font,"×"+board.prize(i),a.x+28,a.y+12,CELL_INK,false);}
            case VAULT -> number(g,String.valueOf(value),cx,a.y+6,2F);
            case DICE -> dice(g,cx,cy,value);
            case FRUIT -> CasinoVisuals.item(g,new ItemStack(FRUIT[value]),cx-12,cy-12,1.5F);
            case GEMS -> CasinoVisuals.item(g,new ItemStack(value==1?Items.DIAMOND:Items.FLINT),cx-12,cy-12,1.5F);
            case BINGO -> {if(value==1)CasinoVisuals.item(g,new ItemStack(Items.GOLD_NUGGET),cx-11,cy-11,1.4F);else number(g,"—",cx,cy-5,1.4F);}
            case CROWN -> {
                if(value==1){g.fill(cx-12,cy+3,cx+12,cy+9,0xFFAA641B);g.fill(cx-12,cy-7,cx-6,cy+5,0xFFDAA324);g.fill(cx-3,cy-10,cx+3,cy+5,0xFFDAA324);g.fill(cx+6,cy-7,cx+12,cy+5,0xFFDAA324);g.fill(cx-12,cy+1,cx+12,cy+5,0xFFF8D45A);g.fill(cx-2,cy+2,cx+2,cy+6,0xFFBA4169);}
                else CasinoVisuals.item(g,new ItemStack(Items.IRON_NUGGET),cx-10,cy-10,1.3F);
            }
        }
    }
    private void number(GuiGraphics g,String s,int x,int y,float scale){g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);g.drawString(font,s,-font.width(s)/2,0,CELL_INK,false);g.pose().popPose();}
    private void dice(GuiGraphics g,int x,int y,int value){
        g.fill(x-12,y-12,x+12,y+12,0xFFE9E6DC);g.renderOutline(x-12,y-12,24,24,0xFFA39D8E);
        if(value%2==1)dot(g,x,y);
        if(value>=2){dot(g,x-7,y-7);dot(g,x+7,y+7);}
        if(value>=4){dot(g,x+7,y-7);dot(g,x-7,y+7);}
        if(value==6){dot(g,x-7,y);dot(g,x+7,y);}
    }
    private void dot(GuiGraphics g,int x,int y){g.fill(x-2,y-2,x+2,y+2,CELL_INK);}
    private boolean scratch(double mx,double my) {
        if(requested||answered)return false;
        double x=mx-leftPos,y=my-topPos;boolean hit=false;
        for(Area a:areas) {
            if(x<a.x-8||x>a.x+a.width+8||y<a.y-8||y>a.y+a.height+8)continue;
            hit=true;
            for(int c=0;c<a.count();c++){
                double dx=x-(a.x+(c%a.columns())*4+2),dy=y-(a.y+(c/a.columns())*4+2);
                if(dx*dx+dy*dy<=121)removed.set(a.offset+c);
            }
            if(removed.get(a.offset,a.offset+a.count()).cardinality()>=a.count()*.62)removed.set(a.offset,a.offset+a.count());
        }
        if(hit&&removed.cardinality()>=totalMask)requestSettlement();
        return hit;
    }
    @Override protected boolean clickPanel(double x,double y,int button){return button==0&&scratch(x,y)||super.clickPanel(x,y,button);}
    @Override protected boolean dragPanel(double x,double y,int button,double dx,double dy){
        if(button==0){boolean hit=false;int steps=Math.min(400,Math.max(1,(int)Math.ceil(Math.hypot(dx,dy)/3)));
            for(int i=0;i<=steps;i++)hit|=scratch(x-dx+dx*i/steps,y-dy+dy*i/steps);if(hit)return true;}
        return super.dragPanel(x,y,button,dx,dy);
    }
    @Override protected void renderPanel(GuiGraphics g,int mouseX,int mouseY,float partial){super.renderPanel(g,mouseX,mouseY,partial);}
    private void fit(GuiGraphics g,String s,int x,int y,int width,int color){g.drawString(font,font.width(s)<=width?s:font.plainSubstrByWidth(s,width-8)+"…",x,y,color,false);}
    private static Component tr(String key,Object... args){return Component.translatable("scratch.teamecon."+key,args);}
}
