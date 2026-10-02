package com.evolt.teamecon.client;
import com.evolt.teamecon.casino.MachineBetMenu;
import com.evolt.teamecon.network.payloads.MachineBetPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class MachineBetScreen extends CompactContainerScreen<MachineBetMenu> {
    private EditBox input;
    private Button apply;
    private int factor=1;
    public MachineBetScreen(MachineBetMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=260;imageHeight=154;}
    @Override protected void init(){
        super.init();
        factor=menu.factor();
        input=UiTheme.input(font,leftPos+20,topPos+59,150,18,tr("factor"));input.setMaxLength(7);input.setFilter(s->s.matches("[0-9]*"));
        input.setValue(String.valueOf(factor));input.setResponder(s->{try{factor=Integer.parseInt(s);}catch(NumberFormatException e){factor=0;}refresh();});
        addRenderableWidget(input);setInitialFocus(input);
        addRenderableWidget(UiButton.of(tr("max"),b->input.setValue(""+Math.min(1_000_000,menu.limit()/menu.base()))).bounds(leftPos+177,topPos+58,63,20).build());
        apply=addRenderableWidget(UiButton.of(tr("apply"),b->{
            ClientPayloadSender.sendToServer(new MachineBetPayload(menu.containerId,factor));apply.active=false;
        }).bounds(leftPos+20,topPos+120,105,20).build());
        addRenderableWidget(UiButton.of(Component.translatable("gui.cancel"),b->onClose()).bounds(leftPos+135,topPos+120,105,20).build());refresh();
    }
    private void refresh(){if(apply!=null)apply.active=MachineBetMenu.multiplied(menu.base(),factor,menu.limit())>0;}
    @Override protected void renderBg(GuiGraphics g,float p,int mx,int my){
        UiTheme.panel(g,leftPos,topPos,imageWidth,imageHeight);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawString(font,tr("title"),16,12,UiTheme.TEXT,false);g.drawString(font,tr("base",menu.base()),20,32,UiTheme.TEXT,false);
        g.drawString(font,tr("factor"),20,47,UiTheme.MUTED,false);
        long value=MachineBetMenu.multiplied(menu.base(),factor,menu.limit());
        g.drawString(font,value>0?tr("preview",value):tr("invalid"),20,86,value>0?UiTheme.POSITIVE:UiTheme.NEGATIVE,false);
        g.drawString(font,tr("limit",menu.limit()),20,102,UiTheme.MUTED,false);
    }
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(input.isFocused()&&minecraft.options.keyInventory.matches(key,scan))return true;
        if(key==257&&apply.active){apply.onPress();return true;}return super.keyPressed(key,scan,mods);
    }
    private static Component tr(String key,Object...args){return Component.translatable("machine.teamecon.bet_input."+key,args);}
}
