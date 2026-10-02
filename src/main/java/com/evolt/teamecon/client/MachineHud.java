package com.evolt.teamecon.client;

import com.evolt.teamecon.casino.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Aiming at a physical control tells players what right-click will do, without opening a screen. */
@EventBusSubscriber(modid="teamecon",value=Dist.CLIENT)
public final class MachineHud {
    private MachineHud(){}
    @SubscribeEvent public static void draw(RenderGuiEvent.Post event) {
        var mc=Minecraft.getInstance();if(mc.screen!=null||mc.level==null||mc.options.hideGui||!(mc.hitResult instanceof BlockHitResult hit))return;
        var state=mc.level.getBlockState(hit.getBlockPos());
        if(!(state.getBlock() instanceof CasinoMachineBlock)&&!(state.getBlock() instanceof MachinePartBlock))return;
        var pos=MachineStructure.base(hit.getBlockPos(),state).above();
        if(!(mc.level.getBlockEntity(pos) instanceof CasinoMachineBlockEntity machine))return;
        List<Component> lines=new ArrayList<>();lines.add(machine.getDisplayName());
        if(!machine.getBlockState().getValue(CasinoMachineBlock.ASSEMBLED))lines.add(tr("legacy"));
        else {
            lines.add(tr("hud_bet",machine.bet(),LargeMachineRenderer.choice(machine)));
            var control=MachineInteraction.controlAt(machine,hit);
            lines.add(machine.spinTicks()>0?tr("running"):tr("control."+control.name().toLowerCase(Locale.ROOT)));
            if(ClientCasinoProgression.ready()&&ClientCasinoProgression.rules().profile(machine.machineId())!=null){
                var profile=ClientCasinoProgression.rules().profile(machine.machineId());
                if(ClientCasinoProgression.level()<profile.requiredLevel())lines.set(2,tr("level_locked",ClientCasinoProgression.level(),profile.requiredLevel()));
            }
            if(machine.hasRun())lines.add(tr("hud_run",String.format(Locale.ROOT,"%.2f",machine.multiplier()),machine.cashOut()));
        }
        var g=event.getGuiGraphics();int width=Math.min(Math.min(300,g.guiWidth()-24),lines.stream().mapToInt(mc.font::width).max().orElse(100)),x=(g.guiWidth()-width)/2,y=7;
        var wrapped = new ArrayList<net.minecraft.util.FormattedCharSequence>();
        for (Component line : lines) wrapped.addAll(mc.font.split(line, width));
        UiTheme.panel(g,x-6,y-5,width+12,wrapped.size()*10+8);
        for(int i=0;i<wrapped.size();i++)g.drawString(mc.font,wrapped.get(i),x,y+i*10,i==0?UiTheme.ACCENT:UiTheme.TEXT,false);
    }
    private static Component tr(String key,Object... args){return Component.translatable("machine.teamecon."+key,args);}
    private static String number(double n){return String.format(Locale.ROOT,"%.2f",n).replaceAll("\\.?0+$","");}
}
