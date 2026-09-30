package com.evolt.teamecon.client;

import com.evolt.teamecon.casino.*;
import com.evolt.teamecon.gambling.ColorWheelGame;
import com.evolt.teamecon.gambling.RouletteGame;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import static com.evolt.teamecon.client.MachineRenderUtil.*;
import static com.evolt.teamecon.client.MachineMeshes.*;

/** Large cabinets with tangible buttons, spinning reels, horizontal wheels and a rolling marble. */
final class LargeMachineRenderer {
    private static final int METAL=0xFFB7C9D0,DARK=0xFF14212B,BLACK=0xFF09141C,GOLD=0xFFE6B955,LIT=15728880;
    private LargeMachineRenderer(){}
    static void render(CasinoMachineBlockEntity e,float partial,PoseStack p,MultiBufferSource b,int light,SlimeHopVisuals slime){
        var game=((CasinoMachineBlock)e.getBlockState().getBlock()).game();var layout=MachineLayout.of(game);
        p.pushPose();face(p,e.getBlockState().getValue(CasinoMachineBlock.FACING));p.translate(0,-1,0);
        int accent=switch(game){case SLOTS->0xFFEC6872;case PENGUIN->0xFF6EE2F0;case MULTIPLIER->0xFF61DFAD;case HILO->0xFFA995ED;case COLOR_WHEEL->0xFFFFCE5B;default->0xFF60C49E;};
        if(game==GameType.COLOR_WHEEL)wheelStand(p,b,light);
        else if(layout.table())tableBody(e,p,b,layout,accent,light);
        else cabinet(e,p,b,layout,accent,light);
        switch(game){
            case SLOTS->slots(e,partial,p,b,light);
            case ROULETTE->{p.pushPose();p.translate(0,-1.00F,0);wheel(e,partial,p,b,false,light);p.popPose();}
            case COLOR_WHEEL->prizeWheel(e,partial,p,b,light);
            case PENGUIN->penguin(e,partial,p,b,light,slime);
            case MULTIPLIER->multiplier(e,partial,p,b,light);
            case HILO->hilo(e,partial,p,b,light);
            default->{}
        }
        controls(e,p,b,layout,accent,light);
        p.popPose();
    }
    private static void cabinet(CasinoMachineBlockEntity e,PoseStack p,MultiBufferSource b,MachineLayout l,int accent,int light){
        float w=l.width(),back=1-l.depth();
        // Stepped edges, inset panels, chrome rails, vent slats and separate feet.
        box(p,b,.08F,.1F,back+.1F,w-.16F,2.72F,l.depth()-.2F,DARK,light);
        box(p,b,.025F,.12F,back+.06F,w-.05F,.14F,l.depth()-.04F,METAL,light);
        box(p,b,.025F,2.77F,back+.06F,w-.05F,.16F,l.depth()-.04F,METAL,light);
        for(float x:new float[]{.06F,w-.14F}){
            box(p,b,x,.25F,.9F,.08F,2.48F,.12F,METAL,light);
            box(p,b,x+.02F,1.57F,1.025F,.04F,1.13F,.025F,accent,LIT);
        }
        for(float x:new float[]{.15F,w-.40F})for(float z:new float[]{back+.18F,.57F})box(p,b,x,0,z,.25F,.12F,.25F,BLACK,light);
        box(p,b,.16F,1.53F,.82F,w-.32F,1.15F,.18F,BLACK,light);
        box(p,b,.14F,2.68F,.9F,w-.28F,.06F,.18F,accent,LIT);
        box(p,b,.14F,1.50F,.9F,w-.28F,.055F,.18F,METAL,light);
        box(p,b,.22F,.27F,1.005F,w-.44F,.36F,.04F,BLACK,light);
        for(int i=0;i<5;i++)for(float x:new float[]{.01F,w-.035F})box(p,b,x,.5F+i*.11F,back+.35F,.025F,.035F,.5F,BLACK,light);
        for(float x:new float[]{.1F,w-.13F})for(float y:new float[]{.3F,1.5F,2.71F})sphere(p,b,x,y,1.04F,.025F,0xFFE4EDF1,light);
        // Keep the entire glyph height on a plaque in front of the upper trim.
        // The old title extended behind the accent strip at z=1.08, hiding its bottom.
        box(p,b,.16F,2.72F,1.085F,w-.32F,.27F,.03F,BLACK,light);
        text(p,b,title(e),w/2,2.97F,1.13F,.025F,w-.40F,accent,LIT);
        for(int i=0;i<Math.round(w*8);i++){
            int color=e.winTicks()>0&&e.winTicks()%8<4?0xFFFFFFFF:accent;
            box(p,b,.12F+i*.12F,2.64F,1.035F,.065F,.03F,.025F,color,LIT);
        }
        // Recessed payout tray, separate lip and coin slot.
        box(p,b,w*.29F,.21F,1.04F,w*.42F,.1F,.16F,METAL,light);
        box(p,b,w*.32F,.31F,1.03F,w*.36F,.12F,.018F,BLACK,light);
        box(p,b,w-.31F,.43F,1.055F,.1F,.035F,.025F,GOLD,light);
    }
    private static void tableBody(CasinoMachineBlockEntity e,PoseStack p,MultiBufferSource b,MachineLayout l,int accent,int light){
        float w=l.width(),back=1-l.depth();
        // Lower the furniture and wheel together; keep labels at their natural aspect ratio.
        // The wheel surface is .80 blocks above the floor, clear of the .697 tabletop.
        p.pushPose();p.scale(1,.42F,1);
        for(float x:new float[]{.16F,w-.43F})for(float z:new float[]{back+.16F,.57F}){
            box(p,b,x,0,z,.27F,1.45F,.27F,DARK,light);box(p,b,x-.035F,.03F,z-.035F,.34F,.13F,.34F,METAL,light);
        }
        box(p,b,.1F,.5F,back+.2F,w-.2F,.13F,l.depth()-.4F,BLACK,light);
        box(p,b,.035F,1.40F,back+.035F,w-.07F,.17F,l.depth()-.07F,DARK,light);
        box(p,b,.0F,1.55F,back+.0F,w,.07F,l.depth(),GOLD,light);
        box(p,b,.10F,1.62F,back+.10F,w-.2F,.04F,l.depth()-.2F,0xFF164D42,light);
        box(p,b,.13F,.55F,.88F,w-.26F,.85F,.14F,DARK,light);
        box(p,b,.18F,.25F,1.015F,w-.36F,.42F,.055F,BLACK,light);
        box(p,b,.15F,.24F,1.005F,w-.30F,.025F,.075F,METAL,light);
        box(p,b,.10F,.53F,1.005F,w-.2F,.035F,.035F,accent,LIT);
        p.popPose();
        for(float x:new float[]{.10F,w-.16F})box(p,b,x,.70F,back+.15F,.065F,.82F,.065F,METAL,light);
        box(p,b,.2F,.99F,back+.18F,w-.4F,.54F,.11F,DARK,light);
        text(p,b,title(e),w/2,1.47F,back+.31F,.026F,w-.55F,accent,LIT);
        text(p,b,tr("stake",CasinoScreen.compact(e.bet())),w/2,1.20F,back+.31F,.020F,w-.55F,0xFFFFD878,LIT);
    }

    private static void controls(CasinoMachineBlockEntity e,PoseStack p,MultiBufferSource b,MachineLayout l,int accent,int light){
        p.pushPose();p.translate(0,MachineLayout.controlsOffset(((CasinoMachineBlock)e.getBlockState().getBlock()).game()),0);
        float w=l.width();boolean roulette=e.machineId().equals("roulette");
        box(p,b,.12F,.52F,1.0F,w-.24F,roulette?.74F:1.03F,.055F,0xFF283D4A,light);
        MachineInteraction.Control hover=MachineInteraction.Control.NONE;
        if(Minecraft.getInstance().hitResult instanceof BlockHitResult hit){
            var state=e.getLevel().getBlockState(hit.getBlockPos());
            if((state.getBlock() instanceof CasinoMachineBlock||state.getBlock() instanceof MachinePartBlock)
                    && MachineStructure.base(hit.getBlockPos(),state).equals(e.getBlockPos().below()))hover=MachineInteraction.controlAt(e,hit);
        }
        String[] labels={"−100","−10","+10","+100",tr("bet_factor")};float buttonWidth=(w-.38F)/5;
        for(int i=0;i<5;i++)button(p,b,.15F+i*(w-.30F)/5,.56F,buttonWidth,.19F,labels[i],hover.ordinal()==i+1?accent:METAL,light);
        if(e.machineId().equals("roulette")){
            String[] ids={"red","black","number:0"};int[] colors={0xFFE46B70,0xFF202C39,0xFF58D696};
            for(int i=0;i<3;i++)button(p,b,.15F+i*(w-.30F)/3,.84F,(w-.38F)/3,.17F,
                    Component.translatable("gui.teamecon.roulette."+(i==2?"green":ids[i])).getString()+"  ×"+number((i==2?32:2)*(ClientCasinoProgression.ready()?ClientCasinoProgression.rules().profile("roulette").payoutScale():1)),
                    e.choice().equals(ids[i])?0xFFFFD778:colors[i],light);
        }else if(e.machineId().equals("hilo")){
            for(int i=0;i<2;i++)button(p,b,.15F+i*(w-.30F)/2,.84F,(w-.36F)/2,.17F,
                    Component.translatable("gui.teamecon."+(i==0?"bet_high":"bet_low")).getString(),
                    e.choice().equals(i==0?"high":"low")?0xFFFFD778:METAL,light);
        }else if(e.machineId().equals("color_wheel")){
            double scale=ClientCasinoProgression.ready()?ClientCasinoProgression.rules().profile("color_wheel").payoutScale():1;
            for(int i=0;i<ColorWheelGame.COLORS.length;i++){
                var color=ColorWheelGame.COLORS[i];float x=.22F+i*.53F;
                box(p,b,x,.855F,1.11F,.10F,.10F,.025F,color.argb(),LIT);
                text(p,b,"×"+number(color.multiplier()*scale),x+.28F,.965F,1.151F,.012F,.31F,0xFFFFFFFF,LIT);
            }
        }else text(p,b,choice(e),w/2,1.0F,1.10F,.018F,w-.40F,0xFFFFFFFF,LIT);
        boolean run=e.machineId().equals("multiplier")||e.machineId().equals("penguin");
        button(p,b,.15F,roulette?1.04F:1.29F,run?w*.62F-.17F:w-.3F,.2F,e.spinTicks()>0||e.hasRun()&&e.machineId().equals("multiplier")?tr("running_short"):e.hasRun()?tr("continue"):tr("start"),
                hover==MachineInteraction.Control.START?0xFF8FF5B2:0xFF45B885,light);
        if(run)button(p,b,w*.64F,1.29F,w*.36F-.15F,.2F,tr("cash_out"),e.hasRun()?0xFFE2B95D:0xFF53606A,light);
        if(!roulette){
        box(p,b,.18F,1.065F,1.068F,w-.36F,.185F,.045F,BLACK,light);
        text(p,b,tr("stake",CasinoScreen.compact(e.bet())),w/2,1.235F,1.126F,.018F,w-.42F,0xFFFFD878,LIT);
        }
        String line=e.spinTicks()>0?tr("running_short"):result(e);
        if(!e.machineId().equals("color_wheel")&&!e.machineId().equals("multiplier")&&!l.table())text(p,b,line,w/2,1.75F,e.machineId().equals("penguin")?1.315F:1.13F,.018F,w-.3F,accent,LIT);
        if(!e.machineId().equals("color_wheel"))text(p,b,tr("wallet",e.operator()==null?"--":CasinoScreen.compact(e.balance())),w/2,l.table()?.41F:.45F,1.085F,.013F,w-.5F,0xFFBACFD9,LIT);
        p.popPose();
    }
    private static void button(PoseStack p,MultiBufferSource b,float x,float y,float w,float h,String label,int color,int light){
        box(p,b,x-.014F,y-.014F,1.06F,w+.028F,h+.028F,.055F,BLACK,light);
        box(p,b,x,y,1.095F,w,h,.075F,color,light);
        int brightness=((color>>16&255)*299+(color>>8&255)*587+(color&255)*114)/1000;
        text(p,b,label,x+w/2,y+h-.05F,1.185F,.015F,w-.05F,brightness<115?0xFFF3F7FA:BLACK,LIT);
    }
    private static void slots(CasinoMachineBlockEntity e,float partial,PoseStack p,MultiBufferSource b,int light){
        float progress=e.progress(partial);String[] finalSymbols=e.reels();
        for(int i=0;i<3;i++){
            float x=.27F+i*.5F,phase=Math.clamp(progress/(.68F+i*.16F),0F,1F);
            float angle=(float)(Math.pow(1-phase,3)*Math.PI*12);
            reel(p,b,x,2.16F,.78F,.45F,.31F,angle,light);
            for(int j=0;j<6;j++){
                p.pushPose();p.translate(x+.225F,2.16F,.78F);p.mulPose(Axis.XP.rotation(angle+j*(float)Math.PI/3));
                String symbol=j==0?finalSymbols[i]:CasinoVisuals.SYMBOLS[(j+i)%6];
                item(p,b,e.getLevel(),new ItemStack(CasinoVisuals.symbolItem(symbol)),0,0,.322F,.30F,0,light);p.popPose();
            }
            box(p,b,x-.025F,1.80F,1.13F,.028F,.70F,.025F,GOLD,light);
        }
        box(p,b,.2F,1.8F,1.12F,1.6F,.07F,.08F,METAL,light);
        box(p,b,.2F,2.46F,1.12F,1.6F,.07F,.08F,METAL,light);
        sphere(p,b,2.035F,1.9F,.66F,.12F,METAL,light);
        p.pushPose();p.translate(2.035F,1.9F,.66F);p.mulPose(Axis.XP.rotationDegrees(e.spinTicks()>0?(float)Math.sin(progress*Math.PI)*85:0));
        box(p,b,-.035F,0,-.035F,.07F,.65F,.07F,METAL,light);sphere(p,b,0,.68F,0,.13F,0xFFE65763,light);p.popPose();
    }
    private static void wheel(CasinoMachineBlockEntity e,float partial,PoseStack p,MultiBufferSource b,boolean colors,int light){
        float progress=e.progress(partial),cx=1.5F,cz=-.5F;
        int pockets=colors?ColorWheelGame.POCKETS:37;
        int target=colors?integer(e.reels()[0]):indexOf(RouletteGame.WHEEL,integer(e.reels()[1]));
        double wheelRotation=e.spinTicks()>0?-Math.pow(1-progress,3)*Math.PI*6:0;
        ring(p,b,cx,1.66F,cz,.0F,1.24F,.11F,DARK,light,64);
        ring(p,b,cx,1.75F,cz,1.17F,1.25F,.07F,METAL,light,64);
        ring(p,b,cx,1.76F,cz,1.10F,1.15F,.025F,GOLD,light,64);
        ring(p,b,cx,1.77F,cz,0,.55F,.035F,colors?0xFF24384D:0xFF245647,light,48);
        for(int i=0;i<pockets;i++){
            double a=i*Math.PI*2/pockets+wheelRotation,end=(i+1)*Math.PI*2/pockets+wheelRotation;
            int color=colors?ColorWheelGame.pocket(i).argb():RouletteGame.WHEEL[i]==0?0xFF38AC79:RouletteGame.isRed(RouletteGame.WHEEL[i])?0xFFD85258:0xFF142332;
            sector(p,b,cx,1.80F,cz,.57F,1.07F,a+.012,end-.012,color,light);
            sector(p,b,cx,1.804F,cz,.90F,1.06F,a+.012,a+.029,GOLD,light);
            if(!colors){
                double middle=(a+end)/2;
                p.pushPose();p.translate(cx+Math.cos(middle)*.77,1.811F,cz+Math.sin(middle)*.77);
                p.mulPose(Axis.YP.rotation((float)(-middle+Math.PI/2)));p.mulPose(Axis.XP.rotationDegrees(-90));
                text(p,b,String.valueOf(RouletteGame.WHEEL[i]),0,.04F,.002F,.012F,.105F,0xFFFFFFFF,LIT);p.popPose();
            }
        }
        ring(p,b,cx,1.79F,cz,.51F,.58F,.04F,GOLD,light,48);
        ring(p,b,cx,1.80F,cz,0,.12F,.20F,METAL,light,24);
        sphere(p,b,cx,2.035F,cz,.14F,GOLD,light);
        double theta=(target+.5D)*Math.PI*2/pockets + (e.spinTicks()>0?Math.pow(1-progress,2)*Math.PI*16:0);
        float radius=progress<.65F?1.09F:1.09F-(progress-.65F)/.35F*.28F;
        float bounce=e.spinTicks()>0?(float)Math.abs(Math.sin(progress*Math.PI*24))*.035F*(1-progress):0;
        sphere(p,b,cx+(float)Math.cos(theta)*radius,1.87F+bounce,cz+(float)Math.sin(theta)*radius,.072F,0xFFF8FCFF,LIT);
        if(colors){
            for(int i=0;i<ColorWheelGame.COLORS.length;i++){
                var color=ColorWheelGame.COLORS[i];float x=.26F+i*.52F;
                box(p,b,x,1.52F,1.04F,.42F,.12F,.026F,color.argb(),LIT);
                double scale=ClientCasinoProgression.ready()?ClientCasinoProgression.rules().profile("color_wheel").payoutScale():1;
                text(p,b,"×"+String.format(java.util.Locale.ROOT,"%.2f",color.multiplier()*scale).replaceAll("\\.?0+$",""),x+.21F,1.625F,1.079F,.012F,.35F,0xFFFFFFFF,LIT);
            }
        }
    }
    private static void wheelStand(PoseStack p,MultiBufferSource b,int light){
        box(p,b,.1F,0,-.45F,2.8F,.12F,1.45F,METAL,light);
        box(p,b,.18F,.12F,-.25F,2.64F,.82F,1.25F,DARK,light);
        box(p,b,1.37F,.9F,.53F,.26F,1.15F,.28F,METAL,light);
        for(float x:new float[]{.22F,2.56F})box(p,b,x,.08F,.8F,.22F,.13F,.26F,BLACK,light);
    }
    private static void prizeWheel(CasinoMachineBlockEntity e,float partial,PoseStack p,MultiBufferSource b,int light){
        float cx=1.5F,cy=2.14F,z=1.025F;
        int target=ColorWheelGame.displayPocket(integer(e.reels()[0]));
        double rotation=-Math.PI/2-(target+.5)*Math.PI*2/40-Math.pow(1-e.progress(partial),3)*Math.PI*8;
        p.pushPose();p.translate(cx,cy,z);p.mulPose(Axis.XP.rotationDegrees(90));
        ring(p,b,0,0,0,0,1.08F,.05F,DARK,light,96);
        ring(p,b,0,.05F,0,1.01F,1.09F,.035F,GOLD,light,96);
        for(var sector:ColorWheelGame.sectors()){
            double a=sector.start()*Math.PI*2/40+rotation,end=(sector.start()+sector.weight())*Math.PI*2/40+rotation;
            sector(p,b,0,.088F,0,.16F,1.015F,a+.004,end-.004,GOLD,LIT);
            sector(p,b,0,.090F,0,.19F,.985F,a+.022,end-.022,sector.color().argb(),LIT);
        }
        p.popPose();
        // A high-contrast gold arrow projects in front of the ring and points at the winning wedge.
        var arrow=b.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(WHITE));
        quad(arrow,p,new float[]{cx-.16F,cy+1.16F,z+.22F,cx+.16F,cy+1.16F,z+.22F,cx,cy+.91F,z+.22F,cx,cy+.91F,z+.22F},0,0,1,GOLD,LIT);
        quad(arrow,p,new float[]{cx-.09F,cy+1.12F,z+.225F,cx+.09F,cy+1.12F,z+.225F,cx,cy+.97F,z+.225F,cx,cy+.97F,z+.225F},0,0,1,0xFFF9F2DA,LIT);
        box(p,b,cx-.49F,cy-.10F,z+.12F,.98F,.235F,.065F,GOLD,LIT);
        box(p,b,cx-.465F,cy-.075F,z+.187F,.93F,.185F,.012F,BLACK,LIT);
        text(p,b,e.spinTicks()>0?tr("running_short"):e.result().isEmpty()?title(e):result(e),cx,cy+.081F,z+.213F,.012F,.86F,GOLD,LIT);
    }
    private static void penguin(CasinoMachineBlockEntity e,float partial,PoseStack p,MultiBufferSource b,int light,SlimeHopVisuals slime){
        boolean moving=e.spinTicks()>0,failed=moving?!e.won():e.result().equals("lost");
        float progress=e.progress(partial),travel=moving?Math.min(1,progress/.68F):1;
        int destination=e.reels()[0].equals("jump")?Math.max(1,integer(e.reels()[1])):0;
        int baseLayer=Math.max(0,destination-3),target=destination-baseLayer,from=Math.max(0,target-1);
        float split=failed?Math.clamp((progress-.68F)/.32F,0,1):0;
        box(p,b,.18F,1.8F,1.007F,2.64F,.80F,.015F,0xFF236F91,LIT);
        for(int i=0;i<9;i++)box(p,b,.3F+i*.28F,2.48F+i%3*.035F,1.03F,.024F,.024F,.012F,0xFFD9F5FA,LIT);
        box(p,b,.17F,1.55F,1.275F,2.66F,.265F,.025F,BLACK,light);
        for(int i=0;i<5;i++){
            float x=.28F+i*.52F,y=1.87F+i*.105F;
            if(i==target&&split>0){
                if(moving)for(int a=0;a<2;a++)for(int c=0;c<2;c++){
                    p.pushPose();p.translate(x+.10F+a*.21F+(a-.5F)*split*.36F,y+.08F-split*split*.78F,1.0F+c*.12F);
                    p.mulPose(Axis.ZP.rotationDegrees((a==0?-1:1)*split*70));
                    p.mulPose(Axis.XP.rotationDegrees((c==0?-1:1)*split*45));
                    box(p,b,-.095F,-.05F,-.05F,.19F,.08F,.10F,0xFF75D0E7,light);
                    box(p,b,-.10F,.03F,-.055F,.20F,.025F,.11F,0xFFE3FCFF,LIT);p.popPose();
                }
            }else{
                box(p,b,x,y,.94F,.40F,.08F,.22F,0xFF75D0E7,light);
                box(p,b,x-.025F,y+.07F,.925F,.45F,.035F,.25F,0xFFE3FCFF,light);
                if(failed&&i==target&&progress>.53F)box(p,b,x+.185F,y+.104F,.935F,.022F,.007F,.23F,0xFF27445A,LIT);
            }
            double multiplier=com.evolt.teamecon.gambling.PenguinGame.layerMultiplier(baseLayer+i);
            text(p,b,"×"+String.format(java.util.Locale.ROOT,"%.2f",multiplier),x+.2F,y-.025F,1.22F,.012F,.46F,0xFFD9F5FA,LIT);
        }
        if(!moving&&failed)return;
        float index=moving?from+travel:target;
        float x=.48F+index*.52F,y=1.98F+index*.105F+(moving?(float)Math.sin(travel*Math.PI)*.21F:0)-split*split*.78F;
        p.pushPose();p.translate(x,y,1.065F);p.mulPose(Axis.ZP.rotationDegrees(split*75));
        slime.render(p,b,moving?progress:0,light);p.popPose();
    }
    private static void multiplier(CasinoMachineBlockEntity e,float partial,PoseStack p,MultiBufferSource b,int light){
        boolean crashed=e.result().equals("lost");
        double mult=crashed?0:e.multiplier();
        int color=crashed?0xFFED7D7D:0xFF87F6C6;
        box(p,b,.23F,1.80F,1.01F,1.54F,.78F,.055F,0xFF102B27,light);
        text(p,b,String.format(java.util.Locale.ROOT,"×%.2f",mult),1,2.57F,1.11F,.030F,1.40F,color,LIT);
        text(p,b,tr("live_value",e.cashOut()),1,2.28F,1.11F,.014F,1.40F,color,LIT);
        var chart=CrashChart.observed(e.chartPeak());
        float x=.30F,y=1.86F,w=1.40F,h=.25F,z=1.10F;
        for(int i=0;i<=4;i++){
            box(p,b,x+w*i/4,y,z,.004F,h,.005F,0xFF2B4B46,LIT);
            box(p,b,x,y+h*i/4,z,w,.004F,.005F,0xFF2B4B46,LIT);
        }
        if(e.hasRun()||!e.result().isEmpty()){
            for(int i=1;i<=48;i++)chartLine(p,b,x+w*(float)chart.x((i-1)/48D),y+h*(float)chart.y((i-1)/48D),
                    x+w*(float)chart.x(i/48D),y+h*(float)chart.y(i/48D),z+.012F,color);
            float end=x+w*(float)chart.x(1),top=y+h*(float)chart.y(1);
            if(crashed)chartLine(p,b,end,top,end,y,z+.014F,color);
            box(p,b,end-.014F,(crashed?y:top)-.014F,z+.017F,.028F,.028F,.009F,color,LIT);
        }
        text(p,b,"0s",x+.06F,1.82F,1.11F,.009F,.18F,0xFF97BFB4,LIT);
        text(p,b,(int)chart.horizon()+"s",x+w-.1F,1.82F,1.11F,.009F,.3F,0xFF97BFB4,LIT);
        text(p,b,e.hasRun()?tr("crash_choice"):result(e),1,1.67F,1.11F,.012F,1.44F,color,LIT);
    }
    private static void chartLine(PoseStack p,MultiBufferSource b,float x0,float y0,float x1,float y1,float z,int color){
        float dx=x1-x0,dy=y1-y0,length=(float)Math.hypot(dx,dy);if(length<.00001F)return;
        p.pushPose();p.translate(x0,y0,z);p.mulPose(Axis.ZP.rotation((float)Math.atan2(dy,dx)));
        box(p,b,0,-.005F,0,length,.01F,.006F,color,LIT);p.popPose();
    }
    private static void hilo(CasinoMachineBlockEntity e,float partial,PoseStack p,MultiBufferSource b,int light){
        int roll=e.spinTicks()>0?(int)(e.getLevel().getGameTime()*17%com.evolt.teamecon.gambling.HiLoRules.SIDES)+1:integer(e.reels()[1]);
        text(p,b,roll==0?"?":String.valueOf(roll),1,2.47F,1.10F,.07F,1.2F,0xFFC7B3FF,LIT);
        for(int i=0;i<2;i++){
            p.pushPose();p.translate(.58F+i*.82F,1.99F,1.02F);
            if(e.spinTicks()>0){p.mulPose(Axis.XP.rotationDegrees(e.progress(partial)*540));p.mulPose(Axis.ZP.rotationDegrees(e.progress(partial)*360));}
            box(p,b,-.16F,-.16F,-.12F,.32F,.32F,.24F,0xFFECE8DF,light);
            for(float x:new float[]{-.08F,.08F})for(float y:new float[]{-.08F,.08F})box(p,b,x-.025F,y-.025F,.123F,.05F,.05F,.005F,0xFF3A3761,light);
            p.popPose();
        }
    }
    private static int integer(String s){try{return Integer.parseInt(s);}catch(RuntimeException ex){return 0;}}
    private static int indexOf(int[] values,int value){for(int i=0;i<values.length;i++)if(values[i]==value)return i;return 0;}
    private static String title(CasinoMachineBlockEntity e){return Component.translatable("container.teamecon."+e.machineId()).getString();}
    static String choice(CasinoMachineBlockEntity e){
        String c=e.choice();
        if(c.startsWith("number:"))return tr("number",c.substring(7));
        return switch(e.machineId()){
            case "roulette"->Component.translatable("gui.teamecon.roulette."+(c.isEmpty()?"red":c)).getString();
            case "hilo"->Component.translatable("gui.teamecon."+("low".equals(c)?"bet_low":"bet_high")).getString();
            case "penguin"->tr("next_layer",e.hasRun()?e.runRounds()+1:1);
            case "multiplier"->tr("crash_choice");
            case "color_wheel"->tr("color_rule");
            default->tr("spin_rule");
        };
    }
    private static String result(CasinoMachineBlockEntity e){
        String result=e.result();
        if(result.startsWith("paid:"))return tr("paid",result.substring(5));
        if(result.startsWith("run:"))return tr("round",result.substring(4));
        if(result.equals("lost"))return tr("lost",e.bet());
        return tr("world_hint");
    }
    private static String tr(String key,Object... args){return Component.translatable("machine.teamecon."+key,args).getString();}
    private static String number(double value){return String.format(java.util.Locale.ROOT,"%.2f",value).replaceAll("\\.?0+$","");}
}
