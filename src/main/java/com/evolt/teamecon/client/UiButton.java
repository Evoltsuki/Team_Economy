package com.evolt.teamecon.client;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Dark controls with shared focus and selection states, in either palette. */
final class UiButton extends Button {
    private final BooleanSupplier selected;
    private final UiTheme.Palette theme;
    UiButton(int x,int y,int w,int h,Component text,OnPress press,BooleanSupplier selected,UiTheme.Palette theme) {
        super(x,y,w,h,text,press,DEFAULT_NARRATION);this.selected=selected;this.theme=theme;
    }
    static Builder of(Component text,OnPress press){return new Builder(text,press);}
    static final class Builder extends Button.Builder {
        private final Component text;private final OnPress press;private int x,y,w,h;
        private BooleanSupplier selected=()->false;
        private UiTheme.Palette theme=UiTheme.SHOP;
        Builder(Component text,OnPress press){super(text,press);this.text=text;this.press=press;}
        @Override public Builder bounds(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;return this;}
        Builder selected(BooleanSupplier value){selected=value;return this;}
        Builder theme(UiTheme.Palette value){theme=value;return this;}
        @Override public Button build(){return new UiButton(x,y,w,h,text,press,selected,theme);}
    }
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
        theme.button(g,getX(),getY(),width,height,active,isHoveredOrFocused(),selected.getAsBoolean());
        var font=Minecraft.getInstance().font;
        String text=font.plainSubstrByWidth(getMessage().getString(),width-6);
        UiTheme.centered(g,font,text,getX()+width/2,getY()+(height-8)/2,active?theme.text:theme.muted);
    }
}
