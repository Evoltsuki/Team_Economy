package com.evolt.teamecon.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import java.util.List;

/** Keeps a readable logical layout while reserving space around every panel, including Auto GUI scale. */
public abstract class CompactContainerScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    private float panelScale = 1;

    protected CompactContainerScreen(T menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    @Override protected void init() {
        var window = minecraft.getWindow();
        panelScale = Math.min(1F, Math.min(window.getGuiScaledWidth() * .78F / imageWidth,
                window.getGuiScaledHeight() * .78F / imageHeight));
        // Keep Minecraft's bitmap glyphs on whole framebuffer pixels. Fractional
        // downsampling otherwise drops strokes, especially in Chinese text.
        double pixels = Math.floor(panelScale * window.getGuiScale());
        if (pixels >= 1) panelScale = (float)(pixels / window.getGuiScale());
        width = (int)Math.ceil(window.getGuiScaledWidth() / panelScale);
        height = (int)Math.ceil(window.getGuiScaledHeight() / panelScale);
        super.init();
    }

    public final float panelScale() { return panelScale; }

    @Override public final void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        graphics.flush();
        var scaled = new GuiGraphics(minecraft, graphics.bufferSource()) {
            @Override public int guiWidth() { return width; }
            @Override public int guiHeight() { return height; }
            @Override public void enableScissor(int x0, int y0, int x1, int y1) {
                super.enableScissor((int)Math.floor(x0 * panelScale), (int)Math.floor(y0 * panelScale),
                        (int)Math.ceil(x1 * panelScale), (int)Math.ceil(y1 * panelScale));
            }
            @Override public boolean containsPointInScissor(int x, int y) {
                return super.containsPointInScissor((int)(x * panelScale), (int)(y * panelScale));
            }
        };
        scaled.pose().mulPose(graphics.pose().last().pose());
        scaled.pose().scale(panelScale, panelScale, 1);
        renderPanel(scaled, (int)(mouseX / panelScale), (int)(mouseY / panelScale), partial);
        scaled.flush();
    }

    protected void renderPanel(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        super.render(graphics, mouseX, mouseY, partial);
    }

    // Screen renders deferred widget tooltips after render(), in ordinary viewport coordinates.
    @Override public void setTooltipForNextRenderPass(List<FormattedCharSequence> lines, ClientTooltipPositioner positioner, boolean override) {
        super.setTooltipForNextRenderPass(lines, DefaultTooltipPositioner.INSTANCE, override);
    }

    @Override public final boolean mouseClicked(double x, double y, int button) { return clickPanel(x / panelScale, y / panelScale, button); }
    protected boolean clickPanel(double x, double y, int button) { return super.mouseClicked(x, y, button); }
    @Override public final boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return dragPanel(x / panelScale, y / panelScale, button, dx / panelScale, dy / panelScale);
    }
    protected boolean dragPanel(double x, double y, int button, double dx, double dy) { return super.mouseDragged(x, y, button, dx, dy); }
    @Override public final boolean mouseScrolled(double x, double y, double dx, double dy) { return scrollPanel(x / panelScale, y / panelScale, dx, dy); }
    protected boolean scrollPanel(double x, double y, double dx, double dy) { return super.mouseScrolled(x, y, dx, dy); }
    @Override public boolean mouseReleased(double x, double y, int button) { return super.mouseReleased(x / panelScale, y / panelScale, button); }
    @Override public void mouseMoved(double x, double y) { super.mouseMoved(x / panelScale, y / panelScale); }
}
