package com.evolt.teamecon.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
/** Flat, legible terminal controls with a visible focus border. */
final class TerminalButton extends Button {
    TerminalButton(int x,int y,int w,int h,Component text,OnPress press){super(x,y,w,h,text,press,DEFAULT_NARRATION);}
    static Builder of(Component text,OnPress press){return new Builder(text,press);}
    static final class Builder extends Button.Builder {
        private final Component text;private final OnPress press;private int x,y,w,h;
        Builder(Component text,OnPress press){super(text,press);this.text=text;this.press=press;}
        @Override public Builder bounds(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;return this;}
        @Override public Button build(){return new TerminalButton(x,y,w,h,text,press);}
    }
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
        int edge=isHoveredOrFocused()?0xFF8AE0CA:active?0xFF45626A:0xFF283F48;
        g.fill(getX(),getY(),getX()+width,getY()+height,edge);
        g.fill(getX()+1,getY()+1,getX()+width-1,getY()+height-1,active?isHoveredOrFocused()?0xFF31525B:0xFF213A46:0xFF192D36);
        var font=Minecraft.getInstance().font;String text=font.plainSubstrByWidth(getMessage().getString(),width-5);
        g.drawString(font,text,getX()+(width-font.width(text))/2,getY()+(height-8)/2,active?0xFFE8F2EE:0xFF7D999D,false);
    }
}
