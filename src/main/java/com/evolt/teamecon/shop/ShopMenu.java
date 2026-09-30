package com.evolt.teamecon.shop;

import com.evolt.teamecon.init.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;

public class ShopMenu extends AbstractContainerMenu {
    public static final int SALE_SLOTS = 27;
    private final BlockPos pos;
    private boolean remote;
    private String initialTab="items";
    private long lastRequest;
    private long nextActionTick;
    private boolean catalogSent;
    private final SimpleContainer sale = new SimpleContainer(SALE_SLOTS);
    private boolean salesVisible;

    public ShopMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readBoolean() ? buf.readBlockPos() : (BlockPos) null);
        if(buf.isReadable()){remote=buf.readBoolean();initialTab=buf.readUtf(16);}
    }
    public ShopMenu(int id, Inventory inventory) { this(id, inventory, (BlockPos) null); }
    public ShopMenu(int id, Inventory inventory, BlockPos pos) {
        super(ModRegistries.SHOP_MENU.value(), id);
        this.pos = pos;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(saleSlot(sale, row * 9 + col, 12 + col * 18, 68 + row * 18));
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(saleSlot(inventory, col + row * 9 + 9, 12 + col * 18, 139 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(saleSlot(inventory, col, 12 + col * 18, 197));
        sale.addListener(container -> {
            if (inventory.player instanceof net.minecraft.server.level.ServerPlayer p && p.containerMenu == this)
                com.evolt.teamecon.network.ShopNetwork.sendSync(p);
        });
    }
    private Slot saleSlot(net.minecraft.world.Container container, int index, int x, int y) {
        return new Slot(container, index, x, y) {
            @Override public boolean isActive() { return salesVisible && !boxesOnly(); }
            @Override public boolean mayPlace(ItemStack stack) { return !boxesOnly(); }
        };
    }
    public boolean isRemote(){return remote;}
    public boolean boxesOnly(){return pos!=null&&initialTab.equals("boxes");}
    public String initialTab(){return initialTab;}
    public static void open(net.minecraft.server.level.ServerPlayer p,BlockPos pos,boolean remote,String tab){
        if(remote&&!com.evolt.teamecon.casino.TerminalItem.authorized(p))return;
        p.openMenu(new net.minecraft.world.SimpleMenuProvider((id,inv,player)->{
            ShopMenu menu=new ShopMenu(id,inv,pos);menu.remote=remote;menu.initialTab=tab;return menu;
        },net.minecraft.network.chat.Component.translatable(tab.equals("boxes")?"block.teamecon.blind_box_machine":remote?"container.teamecon.terminal":"container.teamecon.shop")),b->{
            b.writeBoolean(pos!=null);if(pos!=null)b.writeBlockPos(pos);b.writeBoolean(remote);b.writeUtf(tab,16);
        });
    }
    public SimpleContainer sale() { return sale; }
    public void showSales(boolean value) { salesVisible = value; }
    public BlockPos pos() { return pos; }
    public boolean catalogSent() { return catalogSent; }
    public void markCatalogSent() { catalogSent = true; }
    public void invalidateCatalog() { catalogSent = false; }
    public boolean acceptRequest(long request, long tick) {
        if (request <= lastRequest || tick < nextActionTick) return false;
        lastRequest = request;
        nextActionTick = tick + 4;
        return true;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (boxesOnly() || index < 0 || index >= slots.size() || !getSlot(index).hasItem()) return ItemStack.EMPTY;
        Slot slot = getSlot(index);
        ItemStack stack = slot.getItem(), copy = stack.copy();
        boolean deposit = index < SALE_SLOTS;
        if (!moveItemStackTo(stack, deposit ? SALE_SLOTS : 0, deposit ? slots.size() : SALE_SLOTS, deposit)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return copy;
    }
    @Override public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) clearContainer(player, sale);
        if(remote&&player instanceof net.minecraft.server.level.ServerPlayer p&&!stillValid(p)){
            var mod=com.evolt.teamecon.TeamEconomyMod.get();var run=mod.gambling()==null?null:mod.gambling().sessionOf(p.getUUID());
            if(run!=null&&!run.worldBound()&&!run.liveCrash())mod.gambling().cashOut(p.getUUID(),com.evolt.teamecon.team.TeamUtil.walletKey(p.getServer(),p.getUUID()),p.getName().getString(),run.game());
        }
    }
    @Override public boolean stillValid(Player player) {
        return player.isAlive() && (!remote || !(player instanceof net.minecraft.server.level.ServerPlayer p) || com.evolt.teamecon.casino.TerminalItem.authorized(p)) && (pos == null || (player.level().getBlockState(pos).getBlock() instanceof ShopBlock
                && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64));
    }
}
