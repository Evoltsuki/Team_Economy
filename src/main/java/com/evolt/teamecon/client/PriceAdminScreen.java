package com.evolt.teamecon.client;

import com.evolt.teamecon.economy.MoneyMath;
import com.evolt.teamecon.network.payloads.PriceAdminActionPayload;
import com.evolt.teamecon.network.payloads.PriceAdminSyncPayload;
import com.evolt.teamecon.price.PriceAdminMenu;
import com.evolt.teamecon.price.PriceService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import java.util.*;

/** Creative-style catalogue with inert icons and server-authorized price editing. */
public final class PriceAdminScreen extends CompactContainerScreen<PriceAdminMenu> {
    private record Row(String id, ItemStack icon, SearchText search) { }
    private record Category(Component name, ItemStack icon, Set<String> items) { }
    private final List<Row> all = new ArrayList<>();
    private final List<Category> categories = new ArrayList<>();
    private final List<Button> tiles = new ArrayList<>(), tabs = new ArrayList<>();
    private List<Row> filtered = List.of();
    private String query = "", selected = "", status = "";
    private int category, tabPage, rowOffset, buyMode, sellMode;
    private long sequence;
    private boolean waiting, dirty, applying, dragging;
    private PriceAdminSyncPayload data;
    private EditBox search, buyValue, sellValue;
    private Button buyModeButton, sellModeButton, save, reset, discard, previousTabs, nextTabs;
    private String buyDraft = "", sellDraft = "";

    public PriceAdminScreen(PriceAdminMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }
    public boolean ready() { return data != null && !waiting; }
    public PriceAdminSyncPayload currentPrice() { return data; }
    private static Component tr(String key, Object... args) { return Component.translatable("gui.teamecon.prices." + key, args); }

    private void buildCatalogue() {
        if (!all.isEmpty() || minecraft.level == null) return;
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR || !item.isEnabled(minecraft.level.enabledFeatures())) continue;
            ItemStack icon = new ItemStack(item);
            String id = PriceService.itemKeyStatic(item);
            all.add(new Row(id, icon, SearchText.of(icon.getHoverName().getString(), id)));
        }
        categories.add(new Category(tr("all"), new ItemStack(Items.COMPASS), Set.of()));
        CreativeModeTabs.tryRebuildTabContents(minecraft.level.enabledFeatures(), true, minecraft.level.registryAccess());
        for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
            if (tab.getType() != CreativeModeTab.Type.CATEGORY || tab.getDisplayItems().isEmpty()) continue;
            Set<String> ids = new HashSet<>();
            for (ItemStack item : tab.getDisplayItems()) ids.add(PriceService.itemKeyStatic(item.getItem()));
            categories.add(new Category(tab.getDisplayName(), tab.getIconItem(), Set.copyOf(ids)));
        }
    }

    @Override protected void init() {
        imageWidth = 400; imageHeight = 264;
        super.init();
        buildCatalogue(); tiles.clear(); tabs.clear();
        search = UiTheme.input(font, leftPos + 10, topPos + 61, 176, 16, tr("search"));
        search.setMaxLength(96); search.setHint(tr("search")); search.setValue(query);
        search.setResponder(value -> { query = value; rowOffset = 0; filter(); });
        addRenderableWidget(search);
        previousTabs = button(10, 31, 18, 20, Component.literal("‹"), () -> { tabPage--; refreshTabs(); });
        nextTabs = button(184, 31, 18, 20, Component.literal("›"), () -> { tabPage++; refreshTabs(); });
        for (int i = 0; i < 7; i++) {
            final int index = i;
            tabs.add(addRenderableWidget(new IconButton(30 + i * 22, 31, () -> {
                int value = tabPage * 7 + index;
                if (value < categories.size()) { category = value; rowOffset = 0; filter(); refreshTabs(); }
            })));
        }
        for (int i = 0; i < 54; i++) {
            final int index = i;
            tiles.add(addRenderableWidget(new IconButton(10 + i % 9 * 20, 89 + i / 9 * 20, () -> {
                int value = rowOffset * 9 + index;
                if (value < filtered.size()) select(filtered.get(value).id());
            })));
        }
        button(10, 215, 26, 18, Component.literal("↑"), () -> scroll(-1));
        button(176, 215, 26, 18, Component.literal("↓"), () -> scroll(1));
        buyModeButton = button(216, 94, 78, 18, tr("inherit"), () -> { buyMode = (buyMode + 1) % 3; changed(); });
        sellModeButton = button(216, 146, 78, 18, tr("inherit"), () -> { sellMode = (sellMode + 1) % 3; changed(); });
        buyValue = priceBox(300, 95, true); sellValue = priceBox(300, 147, false);
        save = button(216, 204, 80, 20, tr("save"), () -> save(false));
        reset = button(302, 204, 86, 20, tr("restore"), () -> save(true));
        discard = button(216, 230, 172, 18, tr("discard"), () -> request(false, -1, -1));
        button(184, 9, 18, 18, Component.literal("×"), this::onClose);
        refreshTabs(); filter(); refreshEditor();
        if (selected.isEmpty() && !menu.initialItem().isEmpty()) select(menu.initialItem());
    }

    private Button button(int x, int y, int w, int h, Component text, Runnable action) {
        return addRenderableWidget(UiButton.of(text, b -> action.run()).bounds(leftPos + x, topPos + y, w, h).build());
    }
    private EditBox priceBox(int x, int y, boolean buy) {
        EditBox box = UiTheme.input(font, leftPos + x, topPos + y, 86, 16, tr(buy ? "buy" : "sell"));
        box.setMaxLength(10); box.setFilter(value -> value.matches("[0-9]*"));
        box.setValue(buy ? buyDraft : sellDraft);
        box.setResponder(value -> { if (buy) buyDraft = value; else sellDraft = value; if (!applying) changed(); });
        return addRenderableWidget(box);
    }
    private void changed() { dirty = true; status = "unsaved"; refreshEditor(); }
    private static int mode(long value) { return value < 0 ? 0 : value == 0 ? 1 : 2; }
    private static String modeKey(int value) { return value == 0 ? "inherit" : value == 1 ? "disabled" : "custom"; }
    private void refreshEditor() {
        if (save == null) return;
        for (var widget : List.of(buyModeButton, sellModeButton, buyValue, sellValue, save, reset, discard)) widget.visible = !selected.isEmpty();
        boolean editable = data != null && data.editable() && !waiting;
        buyModeButton.active = editable && !selected.equals("minecraft:enchanted_book");
        sellModeButton.active = editable && !selected.startsWith("teamecon:");
        buyModeButton.setMessage(tr(modeKey(buyMode))); sellModeButton.setMessage(tr(modeKey(sellMode)));
        buyValue.setEditable(buyModeButton.active && buyMode == 2); sellValue.setEditable(sellModeButton.active && sellMode == 2);
        buyValue.active = buyModeButton.active && buyMode == 2; sellValue.active = sellModeButton.active && sellMode == 2;
        save.active = editable && dirty; reset.active = editable && (data.buy() != -1 || data.sell() != -1);
        discard.active = data != null && !waiting;
    }
    private void refreshTabs() {
        previousTabs.active = tabPage > 0; nextTabs.active = (tabPage + 1) * 7 < categories.size();
        for (int i = 0; i < tabs.size(); i++) {
            IconButton button = (IconButton) tabs.get(i); int index = tabPage * 7 + i;
            button.visible = index < categories.size();
            if (button.visible) { Category tab = categories.get(index); button.icon = tab.icon(); button.chosen = category == index; button.setMessage(tab.name()); button.setTooltip(Tooltip.create(tab.name())); }
        }
    }
    private void filter() {
        Set<String> ids = categories.isEmpty() ? Set.of() : categories.get(category).items();
        // Typing searches the entire registry, just like the creative search tab.
        filtered = all.stream().filter(row -> (query.isBlank() ? category == 0 || ids.contains(row.id()) : row.search().matches(query))).toList();
        rowOffset = Math.max(0, Math.min(maxOffset(), rowOffset));
        for (int i = 0; i < tiles.size(); i++) {
            IconButton button = (IconButton) tiles.get(i); int index = rowOffset * 9 + i;
            button.visible = index < filtered.size();
            if (button.visible) {
                Row row = filtered.get(index); button.icon = row.icon(); button.chosen = selected.equals(row.id()); button.setMessage(row.icon().getHoverName());
                button.setTooltip(Tooltip.create(row.icon().getHoverName().copy().append("\n" + row.id()).append("\n").append(tr("select"))));
            }
        }
    }
    private int maxOffset() { return Math.max(0, (filtered.size() + 8) / 9 - 6); }
    private void scroll(int delta) { rowOffset = Math.max(0, Math.min(maxOffset(), rowOffset + delta)); filter(); }
    private void select(String item) {
        if (dirty || waiting) { status = waiting ? "loading" : "unsaved"; return; }
        selected = item; data = null; filter(); request(false, -1, -1);
    }
    private long value(int mode, String text) {
        if (mode == 0) return -1;
        if (mode == 1) return 0;
        long value = Long.parseLong(text);
        if (value <= 0 || value > MoneyMath.MAX_PRICE) throw new IllegalArgumentException();
        return value;
    }
    private void save(boolean restore) {
        try { request(true, restore ? -1 : value(buyMode, buyDraft), restore ? -1 : value(sellMode, sellDraft)); }
        catch (IllegalArgumentException ex) { status = "invalid"; }
    }
    private void request(boolean save, long buy, long sell) {
        if (selected.isEmpty()) return;
        waiting = true; status = "loading"; refreshEditor();
        ClientPayloadSender.sendToServer(new PriceAdminActionPayload(menu.containerId, ++sequence, save, selected, buy, sell, data == null ? -1 : data.revision()));
    }
    public static void receive(PriceAdminSyncPayload payload) {
        if (Minecraft.getInstance().screen instanceof PriceAdminScreen screen && screen.menu.containerId == payload.containerId()
                && screen.sequence == payload.sequence() && screen.selected.equals(payload.item())) screen.apply(payload);
    }
    private void apply(PriceAdminSyncPayload payload) {
        data = payload; waiting = false; dirty = false;
        status = payload.message().isEmpty() ? payload.editable() ? "" : "restricted" : payload.message();
        buyMode = mode(payload.buy()); sellMode = mode(payload.sell());
        buyDraft = Long.toString(payload.buy() > 0 ? payload.buy() : Math.max(1, payload.effectiveBuy()));
        sellDraft = Long.toString(payload.sell() > 0 ? payload.sell() : Math.max(1, payload.reference()));
        applying = true; buyValue.setValue(buyDraft); sellValue.setValue(sellDraft); applying = false;
        refreshEditor();
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) { }
    @Override protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        UiTheme.panel(g, leftPos, topPos, imageWidth, imageHeight);
        g.drawString(font, title, leftPos + 10, topPos + 13, UiTheme.TEXT, false);
        UiTheme.section(g, leftPos + 210, topPos + 8, 182, 248);
        UiTheme.centered(g,font, tr("count", filtered.size()), leftPos + 107, topPos + 220, UiTheme.TEXT);
        g.drawString(font, font.plainSubstrByWidth(tr("search_hint").getString(), 192), leftPos + 10, topPos + 243, UiTheme.MUTED, false);
        g.fill(leftPos + 194, topPos + 89, leftPos + 202, topPos + 209, UiTheme.SLOT);
        int thumb = maxOffset() == 0 ? 89 : 89 + rowOffset * 102 / maxOffset();
        UiTheme.button(g, leftPos + 194, topPos + thumb, 8, 18, maxOffset()>0, dragging, false);
        if (selected.isEmpty()) { g.drawWordWrap(font, tr("choose"), leftPos + 218, topPos + 22, 166, UiTheme.TEXT); return; }
        Row row = all.stream().filter(r -> r.id().equals(selected)).findFirst().orElse(null);
        if (row != null) {
            g.renderItem(row.icon(), leftPos + 216, topPos + 16);
            g.drawString(font, font.plainSubstrByWidth(row.icon().getHoverName().getString(), 146), leftPos + 236, topPos + 20, UiTheme.TEXT, false);
        }
        g.drawString(font, font.plainSubstrByWidth(selected, 172), leftPos + 216, topPos + 38, UiTheme.MUTED, false);
        g.drawString(font, tr("reference", data == null || data.reference() <= 0 ? "—" : data.reference()), leftPos + 216, topPos + 56, UiTheme.MUTED, false);
        g.drawString(font, tr("buy"), leftPos + 216, topPos + 81, UiTheme.TEXT, false);
        g.drawString(font, tr("sell"), leftPos + 216, topPos + 133, UiTheme.TEXT, false);
        if (data != null) {
            g.drawString(font, tr("effective", data.effectiveBuy() > 0 ? data.effectiveBuy() : tr("disabled").getString()), leftPos + 216, topPos + 117, UiTheme.MUTED, false);
            g.drawString(font, tr("effective", data.effectiveSell() > 0 ? data.effectiveSell() : tr("disabled").getString()), leftPos + 216, topPos + 169, UiTheme.MUTED, false);
        }
        var lines = font.split(tr(status.isEmpty() ? "floor_hint" : status), 172);
        for (int i = 0; i < Math.min(2, lines.size()); i++) g.drawString(font, lines.get(i), leftPos + 216, topPos + 181 + i * 10,
                status.equals("saved") ? UiTheme.POSITIVE : status.isEmpty() ? UiTheme.MUTED : UiTheme.NEGATIVE, false);
    }
    @Override protected boolean scrollPanel(double x, double y, double dx, double dy) {
        if (x < leftPos + 206 && y >= topPos + 80) { scroll(dy > 0 ? -1 : 1); return true; }
        return super.scrollPanel(x, y, dx, dy);
    }
    private void scrollTo(double y) { rowOffset = (int)Math.round(Math.clamp((y - topPos - 98) / 102, 0, 1) * maxOffset()); filter(); }
    @Override protected boolean clickPanel(double x, double y, int button) {
        if (button == 0 && x >= leftPos + 194 && x < leftPos + 202 && y >= topPos + 89 && y < topPos + 209) { dragging = true; scrollTo(y); return true; }
        return super.clickPanel(x, y, button);
    }
    @Override protected boolean dragPanel(double x, double y, int button, double dx, double dy) {
        if (dragging && button == 0) { scrollTo(y); return true; }
        return super.dragPanel(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) { dragging = false; return super.mouseReleased(x, y, button); }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if ((search.isFocused() || buyValue.isFocused() || sellValue.isFocused()) && minecraft.options.keyInventory.matches(key, scan)) return true;
        return super.keyPressed(key, scan, modifiers);
    }
    private final class IconButton extends Button {
        private ItemStack icon = ItemStack.EMPTY;
        private boolean chosen;
        private IconButton(int x, int y, Runnable action) { super(leftPos + x, topPos + y, 20, 20, Component.empty(), b -> action.run(), DEFAULT_NARRATION); }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            UiTheme.tile(g, getX(), getY(), 20, 20, chosen, isHoveredOrFocused());
            g.renderItem(icon, getX() + 2, getY() + 2);
        }
    }
}
