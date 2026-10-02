package com.evolt.teamecon.shop;

import com.evolt.teamecon.init.ModRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Only the player's own inventory is mutable. Prize samples are configuration, never item slots. */
public final class BoxAdminMenu extends AbstractContainerMenu {
    public static final int INVENTORY_X = 114, INVENTORY_Y = 229, HOTBAR_Y = 293, SLOT_STEP = 20;
    private final String initialItem;
    private long lastRequest, nextSaveTick;
    public BoxAdminMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readUtf(256));
    }
    public BoxAdminMenu(int id, Inventory inventory, String initialItem) {
        super(ModRegistries.BOX_ADMIN_MENU.value(), id);
        this.initialItem = initialItem;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * SLOT_STEP, INVENTORY_Y + row * SLOT_STEP));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col, INVENTORY_X + col * SLOT_STEP, HOTBAR_Y));
    }
    public String initialItem() { return initialItem; }
    public boolean accept(long sequence, long tick, boolean save) {
        if (sequence <= lastRequest) return false;
        lastRequest = sequence;
        if (save && tick < nextSaveTick) return false;
        if (save) nextSaveTick = tick + 4;
        return true;
    }
    public static boolean authorized(ServerPlayer player, int containerId) {
        return player.containerMenu instanceof BoxAdminMenu menu && menu.containerId == containerId && menu.stillValid(player);
    }
    public static void open(ServerPlayer player) {
        if (!player.hasPermissions(2)) return;
        String item = "";
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new BoxAdminMenu(id, inventory, item),
                Component.translatable("gui.teamecon.box_admin.title")), b -> b.writeUtf(item, 256));
    }
    @Override public boolean stillValid(Player player) { return player.isAlive() && player.hasPermissions(2); }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (stillValid(player)) super.clicked(slot, button, type, player);
    }
}
