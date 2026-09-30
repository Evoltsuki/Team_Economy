package com.evolt.teamecon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

final class MachineRenderUtil {
    static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("teamecon", "textures/block/white.png");
    private MachineRenderUtil() {}
    static void face(PoseStack pose, Direction direction) {
        pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(-direction.toYRot()));
        pose.translate(-.5, 0, -.5);
    }
    static void item(PoseStack pose, MultiBufferSource buffer, Level level, ItemStack stack,
                     float x, float y, float z, float scale, float angle, int light) {
        pose.pushPose(); pose.translate(x, y, z); pose.scale(scale, scale, scale);
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED,
                light, OverlayTexture.NO_OVERLAY, pose, buffer, level, 0);
        pose.popPose();
    }
    static void text(PoseStack pose, MultiBufferSource buffer, String text, float x, float y, float z, float scale, int color, int light) {
        text(pose,buffer,text,x,y,z,scale,.78F,color,light);
    }
    static void text(PoseStack pose, MultiBufferSource buffer, String text, float x, float y, float z, float scale, float maxWidth, int color, int light) {
        Font font = Minecraft.getInstance().font;
        scale = Math.min(scale, maxWidth / Math.max(1, font.width(text)));
        pose.pushPose(); pose.translate(x, y, z + .003F); pose.scale(scale, -scale, scale);
        font.drawInBatch(text, -font.width(text) / 2F, 0, color, false, pose.last().pose(),
                buffer, Font.DisplayMode.NORMAL, 0, light);
        pose.popPose();
    }
    static void plane(PoseStack pose, MultiBufferSource buffer, ResourceLocation texture,
                      float x0, float y0, float x1, float y1, float z, int color, int light) {
        VertexConsumer v = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
        vertex(v, pose, x0, y0, z, 0, 1, 0, 0, 1, color, light);
        vertex(v, pose, x1, y0, z, 1, 1, 0, 0, 1, color, light);
        vertex(v, pose, x1, y1, z, 1, 0, 0, 0, 1, color, light);
        vertex(v, pose, x0, y1, z, 0, 0, 0, 0, 1, color, light);
    }
    static void box(PoseStack p, MultiBufferSource buffer, float x, float y, float z,
                    float w, float h, float d, int color, int light) {
        VertexConsumer v = buffer.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        float X = x + w, Y = y + h, Z = z + d;
        quad(v,p,new float[]{x,y,Z,X,y,Z,X,Y,Z,x,Y,Z},0,0,1,color,light);
        quad(v,p,new float[]{X,y,z,x,y,z,x,Y,z,X,Y,z},0,0,-1,color,light);
        quad(v,p,new float[]{x,y,z,x,y,Z,x,Y,Z,x,Y,z},-1,0,0,color,light);
        quad(v,p,new float[]{X,y,Z,X,y,z,X,Y,z,X,Y,Z},1,0,0,color,light);
        quad(v,p,new float[]{x,Y,Z,X,Y,Z,X,Y,z,x,Y,z},0,1,0,color,light);
        quad(v,p,new float[]{x,y,z,X,y,z,X,y,Z,x,y,Z},0,-1,0,color,light);
    }
    static void quad(VertexConsumer v, PoseStack p, float[] a, float nx, float ny, float nz, int color, int light) {
        for (int i=0;i<4;i++) vertex(v,p,a[i*3],a[i*3+1],a[i*3+2],
                i==0||i==3?0:1,i<2?1:0,nx,ny,nz,color,light);
    }
    static void vertex(VertexConsumer v, PoseStack p, float x, float y, float z,
                               float u, float texV, float nx, float ny, float nz, int color, int light) {
        v.addVertex(p.last().pose(),x,y,z).setColor(color).setUv(u,texV)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p.last(),nx,ny,nz);
    }
}
