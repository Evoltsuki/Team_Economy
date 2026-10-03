package com.evolt.teamecon.client.compat;

import com.evolt.teamecon.client.BoxAdminScreen;
import com.evolt.teamecon.shop.BoxPrizeGrid;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** Loaded by JEI only; the main mod has no runtime dependency on JEI. */
@JeiPlugin
public final class TeamEconomyJei implements IModPlugin {
    @Override public ResourceLocation getPluginUid(){return ResourceLocation.fromNamespaceAndPath("teamecon","box_editor");}
    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration){
        registration.addGuiScreenHandler(BoxAdminScreen.class,screen->{
            Rect2i area=screen.panelArea();var window=Minecraft.getInstance().getWindow();
            return new IGuiProperties(){
                public Class<? extends Screen> screenClass(){return BoxAdminScreen.class;}
                public int guiLeft(){return area.getX();}
                public int guiTop(){return area.getY();}
                public int guiXSize(){return area.getWidth();}
                public int guiYSize(){return area.getHeight();}
                public int screenWidth(){return window.getGuiScaledWidth();}
                public int screenHeight(){return window.getGuiScaledHeight();}
            };
        });
        registration.addGhostIngredientHandler(BoxAdminScreen.class,new IGhostIngredientHandler<BoxAdminScreen>(){
            @Override public <I> List<Target<I>> getTargetsTyped(BoxAdminScreen screen,ITypedIngredient<I> ingredient,boolean doStart){
                ItemStack sample=ingredient.getItemStack().orElse(ItemStack.EMPTY);
                if(!screen.ready()||sample.isEmpty())return List.of();
                List<Target<I>> targets=new ArrayList<>();
                int page=screen.prizePage();
                for(int cell=0;cell<BoxAdminScreen.PAGE_SIZE;cell++){
                    final int slot=page*BoxAdminScreen.PAGE_SIZE+cell;
                    if(slot>=BoxPrizeGrid.CAPACITY)break;
                    targets.add(new Target<I>(){
                        public Rect2i getArea(){return screen.prizeArea(slot);}
                        public void accept(I ignored){
                            if(Minecraft.getInstance().screen==screen&&screen.prizePage()==page)
                                screen.acceptJeiSample(sample,slot);
                        }
                    });
                }
                return targets;
            }
            @Override public void onComplete(){}
            // The panel highlights the cell under the cursor in its own coordinate space.
            @Override public boolean shouldHighlightTargets(){return false;}
        });
    }
}
