package com.evolt.teamecon.price;

import com.evolt.teamecon.init.ModRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

/** No inventory slots: every visible item is a selection icon, never a movable stack. */
public final class PriceAdminMenu extends AbstractContainerMenu {
    private final String initialItem;
    private long lastRequest, nextSaveTick;
    public PriceAdminMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readUtf(256));
    }
    public PriceAdminMenu(int id, Inventory inventory, String initialItem) {
        super(ModRegistries.PRICE_ADMIN_MENU.value(), id);
        this.initialItem = initialItem;
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
        return player.containerMenu instanceof PriceAdminMenu menu && menu.containerId == containerId && menu.stillValid(player);
    }
    public static void open(ServerPlayer player) {
        if (!player.hasPermissions(2)) return;
        String item = player.getMainHandItem().isEmpty() ? "" : PriceService.itemKeyStatic(player.getMainHandItem().getItem());
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new PriceAdminMenu(id, inventory, item),
                Component.translatable("gui.teamecon.prices.title")), b -> b.writeUtf(item, 256));
    }
    @Override public boolean stillValid(Player player) { return player.isAlive() && player.hasPermissions(2); }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public void clicked(int slot, int button, ClickType type, Player player) { }
}
