package com.evolt.teamecon.client.mixin;

import com.evolt.teamecon.client.CompactContainerScreen;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Container overlays use viewport coordinates even when our panel is scaled. */
@Mixin(value=ContainerScreenEvent.Render.class,remap=false)
public abstract class ContainerRenderCoordinates {
    @Inject(method={"getMouseX","getMouseY"},at=@At("RETURN"),cancellable=true)
    private void teamecon$viewportMouse(CallbackInfoReturnable<Integer> result){
        if(((ContainerScreenEvent.Render)(Object)this).getContainerScreen() instanceof CompactContainerScreen<?> screen)
            result.setReturnValue(Math.round(result.getReturnValue()*screen.panelScale()));
    }
}
