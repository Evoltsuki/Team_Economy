package com.evolt.teamecon.client;

import com.evolt.teamecon.shop.ShopBlock;
import com.evolt.teamecon.shop.ShopMachineBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ShopMachineRenderer implements BlockEntityRenderer<ShopMachineBlockEntity> {
    private static final ItemStack[] GOODS={new ItemStack(Items.BREAD),new ItemStack(Items.IRON_INGOT),new ItemStack(Items.EMERALD),
            new ItemStack(Items.APPLE),new ItemStack(Items.DIAMOND),new ItemStack(Items.ENCHANTED_BOOK)};
    @Override public void render(ShopMachineBlockEntity e, float partial, PoseStack p,
                                  MultiBufferSource b, int light, int overlay) {
        p.pushPose();
        MachineRenderUtil.face(p,e.getBlockState().getValue(ShopBlock.FACING));
        boolean boxes=e.getBlockState().is(com.evolt.teamecon.init.ModRegistries.BLIND_BOX_MACHINE.get());
        for(int row=0;row<2;row++)for(int col=0;col<3;col++){
            float x=.25F+col*.17F,y=1.08F+row*.40F;
            if(boxes){
                int color=new int[]{0xFFB790DE,0xFFE3BD73,0xFF72C5BB}[(row+col)%3];
                MachineRenderUtil.box(p,b,x-.057F,y-.08F,.83F,.114F,.16F,.10F,color,light);
                MachineRenderUtil.box(p,b,x-.012F,y-.08F,.935F,.024F,.16F,.006F,0xFFE9DCA6,light);
                MachineRenderUtil.box(p,b,x-.057F,y-.017F,.935F,.114F,.025F,.006F,0xFFE9DCA6,light);
            }else MachineRenderUtil.item(p,b,e.getLevel(),GOODS[row*3+col],x,y,.93F,.18F,0,15728880);
        }
        MachineRenderUtil.text(p,b,net.minecraft.network.chat.Component.translatable(boxes?"block.teamecon.blind_box_machine":"block.teamecon.shop_machine").getString(),
                .5F,1.845F,.989F,.009F,.67F,0xFFFFE5A5,15728880);
        float progress = e.progress(partial);
        ResourceLocation id = ResourceLocation.tryParse(e.item());
        var item = id != null && BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.get(id) : Items.EMERALD;
        if(progress<1){
            float y=.51F-progress*.11F;
            MachineRenderUtil.item(p,b,e.getLevel(),new ItemStack(item),.445F,y,1.025F,.19F,progress*180F,light);
        }
        // Hinged flap lifts during delivery; the server has already delivered the real item.
        p.pushPose();p.translate(.44F,.615F,.992F);
        p.mulPose(com.mojang.math.Axis.XP.rotationDegrees(progress<1?(float)Math.sin(progress*Math.PI)*-48:0));
        MachineRenderUtil.box(p,b,-.21F,-.08F,0,.42F,.08F,.012F,0xFF405C64,light);p.popPose();
        MachineRenderUtil.text(p,b,net.minecraft.network.chat.Component.translatable("machine.teamecon.pickup").getString(),.435F,.747F,.991F,.006F,.40F,0xFFE6F7EB,15728880);
        p.popPose();
    }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(ShopMachineBlockEntity e){
        return new net.minecraft.world.phys.AABB(e.getBlockPos()).expandTowards(0,1,0).inflate(.15);
    }
}
