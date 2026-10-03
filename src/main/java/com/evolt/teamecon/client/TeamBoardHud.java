package com.evolt.teamecon.client;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
@EventBusSubscriber(modid="teamecon",value=Dist.CLIENT)
public final class TeamBoardHud {
    private static JsonObject data;
    public static void update(String json){try{data=JsonParser.parseString(json).getAsJsonObject();}catch(RuntimeException e){data=null;}}
    public static void clear(){data=null;}
    public static boolean ready(){return data!=null;}
    @SubscribeEvent public static void render(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();if(data==null||mc.level==null||mc.screen!=null||mc.options.hideGui)return;
        var g=event.getGuiGraphics();var font=mc.font;var rows=data.getAsJsonArray("members");
        int capacity=Math.max(1,Math.min(7,(mc.getWindow().getGuiScaledHeight()/2-26)/11)),pages=Math.max(1,(rows.size()+capacity-1)/capacity);
        int page=(int)(mc.level.getGameTime()/120%pages),start=page*capacity,count=Math.min(capacity,rows.size()-start);
        float scale=1F;
        // Native glyph size and shadow remain readable over the world.
        int limit=Math.min(176,(int)(mc.getWindow().getGuiScaledWidth()/3/scale));
        String pageLabel=pages>1?(page+1)+"/"+pages:"";
        int pageSpace=pages>1?font.width(pageLabel)+5:0;
        String balance=Component.translatable("hud.teamecon.balance",CasinoScreen.compact(data.get("balance").getAsLong())).getString();
        int w=Math.min(limit,Math.max(font.width(data.get("team").getAsString())+pageSpace,font.width(balance)));
        // Size all pages together so the board does not jump sideways when paging.
        for(int i=0;i<rows.size();i++){
            var row=rows.get(i).getAsJsonObject();long n=row.get("earned").getAsLong();
            String value=(n<0?"−":n>0?"+":"")+CasinoScreen.compact(Math.abs(n));
            w=Math.min(limit,Math.max(w,font.width(row.get("name").getAsString())+5+font.width(value)));
        }
        int x=0,h=23+count*10,y=0;
        g.pose().pushPose();
        g.pose().translate(mc.getWindow().getGuiScaledWidth()-w*scale-7,(mc.getWindow().getGuiScaledHeight()-h*scale)/2,0);
        g.pose().scale(scale,scale,1);
        String title=font.plainSubstrByWidth(data.get("team").getAsString(),Math.max(1,w-pageSpace));
        g.drawString(font,title,x,y,UiTheme.ACCENT,true);
        g.drawString(font,font.plainSubstrByWidth(balance,w),x,y+11,UiTheme.TEXT,true);
        for(int i=0;i<count;i++){
            var row=rows.get(start+i).getAsJsonObject();long n=row.get("earned").getAsLong();String value=(n<0?"−":n>0?"+":"")+CasinoScreen.compact(Math.abs(n));
            String name=font.plainSubstrByWidth(row.get("name").getAsString(),Math.max(1,w-font.width(value)-5));
            g.drawString(font,name,x,y+23+i*10,row.get("online").getAsBoolean()?UiTheme.TEXT:UiTheme.MUTED,true);
            g.drawString(font,value,x+font.width(name)+5,y+23+i*10,n<0?UiTheme.NEGATIVE:UiTheme.POSITIVE,true);
        }
        if(pages>1)g.drawString(font,pageLabel,x+w-font.width(pageLabel),y,UiTheme.MUTED,true);
        g.pose().popPose();
    }
}
