package com.evolt.teamecon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/** Uses the installed game's slime model and texture; no Minecraft artwork is bundled. */
final class SlimeHopVisuals {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/slime/slime.png");
    private final SlimeModel<Entity> inner;
    private final SlimeModel<Entity> outer;

    SlimeHopVisuals(BlockEntityRendererProvider.Context context) {
        inner = new SlimeModel<>(context.bakeLayer(ModelLayers.SLIME));
        outer = new SlimeModel<>(context.bakeLayer(ModelLayers.SLIME_OUTER));
    }

    void render(PoseStack p, MultiBufferSource b, float motion, int light) {
        float squash=1+(float)Math.sin(motion*Math.PI*2)*.10F;
        p.pushPose();
        p.scale(.72F*squash,-.72F/(squash*squash),-.72F*squash);
        p.translate(0,-1.5F,0);
        inner.renderToBuffer(p,b.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),light,OverlayTexture.NO_OVERLAY);
        outer.renderToBuffer(p,b.getBuffer(RenderType.entityTranslucent(TEXTURE)),light,OverlayTexture.NO_OVERLAY);
        p.popPose();
    }
}
