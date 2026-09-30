package com.evolt.teamecon.client;

import com.evolt.teamecon.casino.CasinoMachineBlock;
import com.evolt.teamecon.casino.CasinoMachineBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static com.evolt.teamecon.client.MachineRenderUtil.*;

/** Tick-timed reels, roulette wheel, legacy scratch plate, dice, risk display and slime hops. */
public class CasinoMachineRenderer implements BlockEntityRenderer<CasinoMachineBlockEntity> {
    private final SlimeHopVisuals slime;
    public CasinoMachineRenderer(net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context context) {
        slime = new SlimeHopVisuals(context);
    }
    @Override public void render(CasinoMachineBlockEntity e, float partial, PoseStack p,
                                 MultiBufferSource b, int light, int overlay) {
        if (!(e.getBlockState().getBlock() instanceof CasinoMachineBlock machine)) return;
        if(e.getBlockState().getValue(CasinoMachineBlock.ASSEMBLED)){
            LargeMachineRenderer.render(e,partial,p,b,light,slime);return;
        }
        p.pushPose(); face(p,e.getBlockState().getValue(CasinoMachineBlock.FACING));
        float progress=e.progress(partial);
        boolean spinning=e.spinTicks()>0;
        String[] reels=e.reels();
        int color=e.winTicks()>0 && e.winTicks()%8<4 ? 0xFF72EAA5 : 0xFFFFD16F;
        switch(machine.game()) {
            case SLOTS -> {
                for(int i=0;i<3;i++) {
                    String symbol=spinning && progress<.6F+i*.2F
                            ? CasinoVisuals.SYMBOLS[(e.spinTicks()/2+i*2)%6] : reels[i];
                    item(p,b,e.getLevel(),new ItemStack(CasinoVisuals.symbolItem(symbol)),
                            .25F+i*.25F,.59F,1.045F,.27F,0,light);
                }
                p.pushPose(); p.translate(.98F,.42F,.72F);
                p.mulPose(Axis.XP.rotationDegrees(spinning?(float)Math.sin(progress*Math.PI)*65:0));
                box(p,b,-.025F,0,-.025F,.05F,.35F,.05F,0xFFCCA74A,light);
                box(p,b,-.07F,.30F,-.07F,.14F,.14F,.14F,0xFFD5443B,light);
                p.popPose();
            }
            case ROULETTE -> {
                int number=integer(reels[1]);
                p.pushPose(); p.translate(.5F,.57F,1.035F);
                p.mulPose(Axis.ZP.rotationDegrees(-(float)CasinoVisuals.wheelAngle(number,progress)));
                plane(p,b,CasinoVisuals.WHEEL,-.34F,-.34F,.34F,.34F,0,0xFFFFFFFF,light);
                p.popPose();
                box(p,b,.478F,.88F,1.04F,.044F,.09F,.05F,0xFFFFEBB2,light);
                if(!spinning) text(p,b,String.valueOf(number),.5F,.60F,1.055F,.014F,color,light);
            }
            case PENGUIN -> renderPenguin(e,p,b,progress,spinning,light);
            case MULTIPLIER -> {
                String value="mult".equals(reels[0])?"×"+reels[1]:"×1.00";
                text(p,b,value,.5F,.68F,1.04F,.027F,color,light);
                for(int i=0;i<8;i++) box(p,b,.2F+i*.077F,.41F,1.02F,.05F,.07F,.02F,
                        spinning&&i<progress*8?0xFF71E8A7:0xFF82633B,15728880);
            }
            case HILO -> {
                int number=spinning?(e.spinTicks()*17)%com.evolt.teamecon.gambling.HiLoRules.SIDES+1:integer(reels[1]);
                text(p,b,number==0?"--":String.valueOf(number),.5F,.68F,1.04F,.045F,color,light);
                item(p,b,e.getLevel(),new ItemStack(Items.QUARTZ),.28F,.33F,1.025F,.20F,progress*360,light);
                item(p,b,e.getLevel(),new ItemStack(Items.QUARTZ),.72F,.33F,1.025F,.20F,-progress*360,light);
            }
            case SCRATCH -> {
                box(p,b,.15F,.3F,1.015F,.7F,.38F,.015F,0xFFF0D598,light);
                String label="scratch".equals(reels[0])?CasinoVisuals.label("scratch",reels[1]).getString():"?";
                text(p,b,label,.5F,.56F,1.037F,.016F,color,light);
                if(spinning) {
                    plane(p,b,WHITE,.17F+progress*.66F,.32F,.83F,.66F,1.045F,0xFFB5C6C8,light);
                    item(p,b,e.getLevel(),new ItemStack(Items.GOLD_NUGGET),.17F+progress*.66F,.53F,1.07F,.22F,progress*720,light);
                }
            }
        }
        float lampY = machine.game() == com.evolt.teamecon.casino.GameType.SCRATCH ? .64F : .93F;
        for(int i=0;i<5;i++) box(p,b,.16F+i*.15F,lampY,1.04F,.065F,.045F,.018F,color,15728880);
        p.popPose();
    }

    private void renderPenguin(CasinoMachineBlockEntity e, PoseStack p, MultiBufferSource b,
                               float progress, boolean moving, int light) {
        boolean failed=!e.won() && java.util.Set.of("short","medium","long").contains(e.reels()[0]);
        float split=failed?Math.clamp((progress-.72F)/.28F,0,1):0;
        for(int i=0;i<3;i++) {
            float x=.12F+i*.28F;
            if(i==2&&split>0){
                if(moving)for(int part=0;part<2;part++){
                    p.pushPose();p.translate(x+.05F+part*.10F+(part-.5F)*split*.2F,.28F-split*split*.26F,1.04F);
                    p.mulPose(Axis.ZP.rotationDegrees((part==0?-1:1)*split*65));
                    box(p,b,-.047F,-.025F,-.10F,.094F,.05F,.2F,0xFFB5E9F9,light);p.popPose();
                }
            }else{
                box(p,b,x,.23F,.9F,.2F,.055F,.22F,0xFF75D0E7,light);
                box(p,b,x-.015F,.275F,.885F,.23F,.02F,.25F,0xFFE3FCFF,light);
                if(failed&&i==2&&progress>.60F)box(p,b,x+.09F,.296F,.89F,.018F,.005F,.24F,0xFF27445A,15728880);
            }
        }
        if(failed&&!moving)return;
        float travel=Math.min(1,progress/.72F);
        float x=moving?.21F+travel*.56F:e.won()?.77F:.21F;
        float y=.295F+(moving?(float)Math.sin(travel*Math.PI)*.20F:0)-split*split*.32F;
        p.pushPose();p.translate(x,y,1.0F);p.scale(.67F,.67F,.67F);
        p.mulPose(Axis.ZP.rotationDegrees(split*75));
        slime.render(p,b,moving?progress:0,light);p.popPose();
    }
    private static int integer(String text) {
        try{return Integer.parseInt(text);}catch(NumberFormatException ex){return 0;}
    }
    @Override public boolean shouldRenderOffScreen(CasinoMachineBlockEntity entity) { return true; }
}
