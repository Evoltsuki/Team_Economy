package com.evolt.teamecon.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** One control language, with the established teal shop and violet mystery-box palettes. */
final class UiTheme {
    static final Palette SHOP=new Palette(false), BOX=new Palette(true);
    static final int TEXT=SHOP.text, MUTED=SHOP.muted, ACCENT=SHOP.accent;
    static final int POSITIVE=0xFF8AE0B3, NEGATIVE=0xFFEFA58B;
    static final int EDGE=SHOP.edge, SHADOW=SHOP.edge, LIGHT=SHOP.hoverEdge;
    static final int PANEL=SHOP.panel, SECTION=SHOP.section, SLOT=SHOP.slot, SELECTION=SHOP.accent;
    private UiTheme() {}

    static final class Palette {
        final int text,muted,accent,edge,panel,section,slot,headerTop,headerBottom;
        final int control,selected,hover,hoverEdge,disabled;
        private Palette(boolean box) {
            text=box?0xFFF2EEF6:0xFFEDF1EB;muted=box?0xFFADA9BE:0xFF97ABA8;
            accent=box?0xFFF1D48E:0xFFF0CC7D;edge=box?0xFF51435F:0xFF354B50;
            panel=box?0xFF1D1929:0xFF101B22;section=box?0xFF30273C:0xFF172A30;
            slot=box?0xFF191321:0xFF0B151E;
            headerTop=box?0xFF3D304A:0xFF263B40;headerBottom=box?0xFF211B2B:0xFF19292F;
            control=box?0xFF342B41:0xFF273E42;selected=box?0xFF654B75:0xFF314B46;
            hover=box?0xFF44364F:0xFF294149;hoverEdge=box?0xFFAF8CC4:0xFF78968D;
            disabled=box?0xFF262231:0xFF17272D;
        }
        void panel(GuiGraphics g,int x,int y,int w,int h) {
            g.fill(x,y,x+w,y+h,edge);
            g.fill(x+1,y+1,x+w-1,y+h-1,panel);
            g.fillGradient(x+2,y+2,x+w-2,y+32,headerTop,headerBottom);
        }
        void section(GuiGraphics g,int x,int y,int w,int h) { inset(g,x,y,w,h,section); }
        private void inset(GuiGraphics g,int x,int y,int w,int h,int fill) {
            g.fill(x,y,x+w,y+h,edge);g.fill(x+1,y+1,x+w-1,y+h-1,fill);
        }
        /** x/y are the icon origin, matching vanilla inventory slot coordinates. */
        void slot(GuiGraphics g,int x,int y) { inset(g,x-1,y-1,18,18,slot); }
        void tile(GuiGraphics g,int x,int y,int w,int h,boolean chosen,boolean hovered) {
            inset(g,x,y,w,h,chosen?selected:hovered?hover:section);
            if(chosen)g.renderOutline(x,y,w,h,accent);
            else if(hovered)g.renderOutline(x,y,w,h,hoverEdge);
        }
        void button(GuiGraphics g,int x,int y,int w,int h,boolean active,boolean hovered,boolean chosen) {
            g.fill(x,y,x+w,y+h,chosen?accent:active&&hovered?hoverEdge:edge);
            g.fill(x+1,y+1,x+w-1,y+h-1,!active?disabled:chosen?selected:hovered?hover:control);
            // Selected tabs have both a tinted surface and a gold edge.
        }
    }
    static void panel(GuiGraphics g,int x,int y,int w,int h){SHOP.panel(g,x,y,w,h);}
    static void section(GuiGraphics g,int x,int y,int w,int h){SHOP.section(g,x,y,w,h);}
    static void slot(GuiGraphics g,int x,int y){SHOP.slot(g,x,y);}
    static void tile(GuiGraphics g,int x,int y,int w,int h,boolean chosen,boolean hovered){SHOP.tile(g,x,y,w,h,chosen,hovered);}
    static void button(GuiGraphics g,int x,int y,int w,int h,boolean active,boolean hovered,boolean chosen){SHOP.button(g,x,y,w,h,active,hovered,chosen);}
    static EditBox input(Font font,int x,int y,int w,int h,Component label) {
        EditBox box=new EditBox(font,x,y,w,h,label);
        box.setTextColor(0xFFF0F0F0);box.setTextColorUneditable(0xFF999999);
        return box;
    }
    static void centered(GuiGraphics g,Font font,Component text,int x,int y,int color) {
        g.drawString(font,text,x-font.width(text)/2,y,color,false);
    }
    static void centered(GuiGraphics g,Font font,String text,int x,int y,int color) {
        g.drawString(font,text,x-font.width(text)/2,y,color,false);
    }
}
