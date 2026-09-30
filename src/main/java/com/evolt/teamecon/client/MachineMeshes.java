package com.evolt.teamecon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import static com.evolt.teamecon.client.MachineRenderUtil.*;

/** Low-poly rings, spheres and reels, drawn as actual world geometry. */
final class MachineMeshes {
    private MachineMeshes(){}
    static void sector(PoseStack p,MultiBufferSource b,float cx,float y,float cz,float inner,float outer,double a,double end,int color,int light){
        var v=b.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        quad(v,p,new float[]{cx+(float)Math.cos(a)*inner,y,cz+(float)Math.sin(a)*inner,
                cx+(float)Math.cos(end)*inner,y,cz+(float)Math.sin(end)*inner,
                cx+(float)Math.cos(end)*outer,y,cz+(float)Math.sin(end)*outer,
                cx+(float)Math.cos(a)*outer,y,cz+(float)Math.sin(a)*outer},0,1,0,color,light);
    }
    static void ring(PoseStack p,MultiBufferSource b,float cx,float y,float cz,float inner,float outer,float height,int color,int light,int segments){
        for(int i=0;i<segments;i++){
            double a=i*Math.PI*2/segments,e=(i+1)*Math.PI*2/segments;
            sector(p,b,cx,y+height,cz,inner,outer,a,e,color,light);
            // A shader pipeline can wrap/change consumers on nested getBuffer calls.
            var v=b.getBuffer(RenderType.entityCutoutNoCull(WHITE));
            float x0=cx+(float)Math.cos(a)*outer,z0=cz+(float)Math.sin(a)*outer,x1=cx+(float)Math.cos(e)*outer,z1=cz+(float)Math.sin(e)*outer;
            quad(v,p,new float[]{x0,y,z0,x1,y,z1,x1,y+height,z1,x0,y+height,z0},(float)Math.cos((a+e)/2),0,(float)Math.sin((a+e)/2),color,light);
            if(inner>0){x0=cx+(float)Math.cos(a)*inner;z0=cz+(float)Math.sin(a)*inner;x1=cx+(float)Math.cos(e)*inner;z1=cz+(float)Math.sin(e)*inner;
                quad(v,p,new float[]{x1,y,z1,x0,y,z0,x0,y+height,z0,x1,y+height,z1},-(float)Math.cos((a+e)/2),0,-(float)Math.sin((a+e)/2),color,light);}
        }
    }
    static void sphere(PoseStack p,MultiBufferSource b,float x,float y,float z,float radius,int color,int light){
        var v=b.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        for(int lat=0;lat<8;lat++)for(int lon=0;lon<16;lon++){
            for(int j=0;j<4;j++){
                double a=(lon+(j==1||j==2?1:0))*Math.PI*2/16;
                double t=(lat+(j>=2?1:0))*Math.PI/8;
                float nx=(float)(Math.sin(t)*Math.cos(a)),ny=(float)Math.cos(t),nz=(float)(Math.sin(t)*Math.sin(a));
                vertex(v,p,x+nx*radius,y+ny*radius,z+nz*radius,j==1||j==2?1:0,j>=2?1:0,nx,ny,nz,color,light);
            }
        }
    }
    static void reel(PoseStack p,MultiBufferSource b,float x,float y,float z,float width,float radius,float angle,int light){
        var v=b.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        for(int i=0;i<32;i++){
            double a=i*Math.PI*2/32+angle,e=(i+1)*Math.PI*2/32+angle;
            float y0=y+(float)Math.sin(a)*radius,z0=z+(float)Math.cos(a)*radius,y1=y+(float)Math.sin(e)*radius,z1=z+(float)Math.cos(e)*radius;
            quad(v,p,new float[]{x,y0,z0,x+width,y0,z0,x+width,y1,z1,x,y1,z1},0,(float)Math.sin((a+e)/2),(float)Math.cos((a+e)/2),i%8==0?0xFFD5C5A4:0xFFF4EAD5,light);
        }
    }
}
