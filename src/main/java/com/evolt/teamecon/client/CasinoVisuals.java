package com.evolt.teamecon.client;

import com.evolt.teamecon.gambling.RouletteGame;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Small native pixel-art visuals shared by all casino screens. */
public final class CasinoVisuals {
    public static final ResourceLocation WHEEL = ResourceLocation.fromNamespaceAndPath("teamecon", "textures/gui/roulette_wheel.png");
    public static final ResourceLocation SLIME = ResourceLocation.fromNamespaceAndPath("teamecon", "textures/gui/slime_hop.png");
    public static final String[] SYMBOLS = {"cherry", "lemon", "orange", "bell", "star", "diamond"};
    private CasinoVisuals() {}

    public static Component label(String category, String name) {
        String key = "game.teamecon." + category + "." + name;
        return net.minecraft.client.resources.language.I18n.exists(key) ? Component.translatable(key) : Component.literal(name);
    }

    public static Item symbolItem(String symbol) {
        return switch (symbol) {
            case "cherry" -> Items.SWEET_BERRIES;
            case "lemon" -> Items.YELLOW_DYE;
            case "orange" -> Items.ORANGE_DYE;
            case "bell" -> Items.BELL;
            case "star" -> Items.NETHER_STAR;
            case "diamond" -> Items.DIAMOND;
            default -> Items.GOLD_NUGGET;
        };
    }
    public static double wheelAngle(int result, float progress) {
        int index = 0;
        for (int i = 0; i < RouletteGame.WHEEL.length; i++) if (RouletteGame.WHEEL[i] == result) index = i;
        double target = -90D - (index + .5D) * 360D / 37D;
        return target - 1080D * Math.pow(1D - progress, 3);
    }
    public static void wheel(GuiGraphics g, int x, int y, int result, float progress) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees((float) wheelAngle(result, progress)));
        g.blit(WHEEL, -32, -32, 64, 64, 0, 0, 512, 512, 512, 512);
        g.pose().popPose();
        g.fill(x - 2, y - 35, x + 3, y - 28, 0xFFF8DC80);
    }
    public static void prizeWheel(GuiGraphics g,int x,int y,int pocket,float progress){
        var texture=ResourceLocation.fromNamespaceAndPath("teamecon","textures/gui/prize_wheel.png");
        g.pose().pushPose();g.pose().translate(x,y,0);
        int display=com.evolt.teamecon.gambling.ColorWheelGame.displayPocket(pocket);
        g.pose().mulPose(Axis.ZP.rotationDegrees((float)(-90-(display+.5)*360/40-1440*Math.pow(1-progress,3))));
        g.blit(texture,-32,-32,64,64,0,0,512,512,512,512);g.pose().popPose();
        g.fill(x-3,y-36,x+4,y-30,0xFFFFE69A);g.fill(x-1,y-30,x+2,y-27,0xFFFFE69A);
    }
    public static void item(GuiGraphics g, ItemStack item, int x, int y, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, scale);
        g.renderItem(item, 0, 0);
        g.pose().popPose();
    }
    public static void penguin(GuiGraphics g, int x, int y, float progress, boolean won, boolean jumping) {
        g.fill(x, y, x + 164, y + 40, 0xFF133E59);
        for(int i=0;i<7;i++)g.fill(x+7+i*23,y+6+i%3*4,x+9+i*23,y+8+i%3*4,0xFFC8EAF1);
        int offsetX = (int) (g.pose().last().pose().m30()/g.pose().last().pose().m00());
        int offsetY = (int) (g.pose().last().pose().m31()/g.pose().last().pose().m11());
        g.enableScissor(offsetX+x,offsetY+y,offsetX+x+164,offsetY+y+40);
        float p=jumping?progress:1,split=won?0:Math.clamp((p-.72F)/.28F,0,1);
        for (int i = 0; i < 5; i++) {
            int iceX=x+5+i*31; int iceY=y+34-i*5;
            if(i==2&&split>0){
                if(jumping)for(int piece=0;piece<3;piece++){
                    g.pose().pushPose();g.pose().translate(iceX+4+piece*9+(piece-1)*split*14,iceY+3+split*split*20,0);
                    g.pose().mulPose(Axis.ZP.rotationDegrees((piece-1)*split*65));
                    g.fill(-4,-3,4,0,0xFFE1FAFF);g.fill(-3,0,3,5,0xFF75CAE8);g.pose().popPose();
                }
            }else{
                g.fill(iceX,iceY,iceX+27,iceY+4,0xFFE1FAFF);g.fill(iceX+3,iceY+4,iceX+24,iceY+8,0xFF75CAE8);
                if(!won&&i==2&&p>.60F){g.fill(iceX+13,iceY,iceX+15,iceY+4,0xFF27445A);g.fill(iceX+8,iceY+1,iceX+15,iceY+2,0xFF27445A);}
            }
        }
        if(!jumping&&!won){g.disableScissor();return;}
        float travel=Math.min(1,p/.72F);
        float px=x+39.5F+travel*31;
        float py=y+9-travel*5-(float)Math.sin(travel*Math.PI)*9+split*split*28;
        g.pose().pushPose();
        g.pose().translate(px + 10, py + 10, 0);
        g.pose().scale(20F / 24F, 20F / 24F, 1);
        g.pose().mulPose(Axis.ZP.rotationDegrees(jumping ? (float) Math.sin(p * Math.PI * 2) * 14 : 0));
        g.blit(SLIME, -12, -12, 0, 0, 24, 24, 24, 24);
        g.pose().popPose();
        g.disableScissor();
    }
    public static void crashCurve(GuiGraphics g,int x,int y,int width,int height,double peak,boolean crashed,boolean visible,int color){
        var chart=CrashChart.observed(peak);
        g.fill(x,y,x+width,y+height,0xFF10271F);
        for(int i=0;i<=4;i++){
            g.fill(x+width*i/4,y,x+width*i/4+1,y+height,0xFF2C493B);
            g.fill(x,y+height*i/4,x+width,y+height*i/4+1,0xFF2C493B);
        }
        if(!visible)return;
        for(int i=1;i<=64;i++)line(g,x+(int)((width-1)*chart.x((i-1)/64D)),y+height-1-(int)((height-2)*chart.y((i-1)/64D)),
                x+(int)((width-1)*chart.x(i/64D)),y+height-1-(int)((height-2)*chart.y(i/64D)),color);
        int end=x+(int)((width-1)*chart.x(1)),top=y+height-1-(int)((height-2)*chart.y(1));
        if(crashed)line(g,end,top,end,y+height-1,color);
        g.fill(end-1,(crashed?y+height-1:top)-1,end+2,(crashed?y+height-1:top)+2,color);
    }
    private static void line(GuiGraphics g,int x0,int y0,int x1,int y1,int color){
        int steps=Math.max(Math.abs(x1-x0),Math.abs(y1-y0));
        for(int i=0;i<=steps;i++){
            float fraction=steps==0?0:i/(float)steps;
            int x=Math.round(x0+(x1-x0)*fraction),y=Math.round(y0+(y1-y0)*fraction);
            g.fill(x,y,x+1,y+1,color);
        }
    }
}
