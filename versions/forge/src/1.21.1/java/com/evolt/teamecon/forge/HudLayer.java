package com.evolt.teamecon.forge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import com.evolt.teamecon.client.MachineHud;
import com.evolt.teamecon.client.TeamBoardHud;

/** Forge 1.21 removed overlay events. Append to vanilla's HUD layers once at setup. */
public final class HudLayer {
    public static void register(){
        try {
            var field=ObfuscationReflectionHelper.findField(Gui.class,"f_316662_"); // Gui.layers
            var layers=(LayeredDraw)field.get(Minecraft.getInstance().gui);
            layers.add((graphics,timer)->{MachineHud.draw(graphics);TeamBoardHud.render(graphics);});
        } catch(IllegalAccessException ex){throw new IllegalStateException("Cannot install Team Economy HUD layer",ex);}
    }
}
