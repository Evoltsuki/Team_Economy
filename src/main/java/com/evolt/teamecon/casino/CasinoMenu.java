package com.evolt.teamecon.casino;

import com.evolt.teamecon.init.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu shared by every game machine and the wireless terminal. It carries the game id, a
 * deposit slot where items are exchanged for points, and the player inventory. All game
 * outcomes are decided server-side; the menu never holds money.
 */
public class CasinoMenu extends AbstractContainerMenu {

    public static final int DEPOSIT_SLOT = 0;
    public static final int PLAYER_SLOTS = 1;

    private final String game;
    private final boolean remote;
    private boolean inventoryVisible=true;
    public void showInventory(boolean show){inventoryVisible=show;}
    private final BlockPos pos;
    private final Container deposit;
    private long lastRequest;
    private long nextActionTick;

    /** Client constructor: reads the game id, the remote flag and the anchor position. */
    public CasinoMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readUtf(32), buf.readBoolean(), buf.readBlockPos(),
                new SimpleContainer(1));
    }

    public CasinoMenu(int containerId, Inventory playerInventory, String game, CasinoMachineBlockEntity entity) {
        this(containerId, playerInventory, game, entity.getBlockPos());
    }

    /** Machine constructor: anchored to the block, deposit items stay in the machine. */
    public CasinoMenu(int containerId, Inventory playerInventory, String game, BlockPos pos) {
        this(containerId, playerInventory, game, false, pos, new SimpleContainer(1));
    }

    /** Terminal constructor: remote, so the menu owns the deposit container itself. */
    public CasinoMenu(int containerId, Inventory playerInventory, String game, BlockPos playerPos, boolean remote) {
        this(containerId, playerInventory, game, remote, playerPos, new SimpleContainer(1));
    }

    private CasinoMenu(int containerId, Inventory playerInventory, String game, boolean remote, BlockPos pos,
                       Container deposit) {
        super(ModRegistries.CASINO_MENU.value(), containerId);
        this.game = game;
        this.remote = remote;
        this.pos = pos;
        this.deposit = deposit;
        checkContainerSize(playerInventory, 0);

        addSlot(new Slot(deposit, 0, 192, 116) {
            @Override public boolean isActive(){return inventoryVisible;}
            @Override
            public boolean mayPlace(ItemStack stack) {
                return true;
            }
        });

        // Player inventory spans the bottom of the wider 320x256 panel: three rows of nine,
        // then the hotbar, centered under the machine column.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 164 + row * 18){@Override public boolean isActive(){return inventoryVisible;}});
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 222){@Override public boolean isActive(){return inventoryVisible;}});
        }
    }

    public String game() {
        return game;
    }

    public Container deposit() {
        return deposit;
    }

    public boolean isRemote() {
        return remote;
    }

    public BlockPos pos() {
        return pos;
    }

    public boolean allows(String requestedGame) {
        return GameType.byId(requestedGame) != null && (remote || game.equals(requestedGame));
    }

    public boolean acceptRequest(long request, long tick, int delay) {
        if (request <= lastRequest || (delay>0 && tick < nextActionTick)) return false;
        lastRequest = request;
        nextActionTick = tick + delay;
        return true;
    }

    public long lastRequest() { return lastRequest; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = getSlot(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();
        if (index == DEPOSIT_SLOT) {
            if (!moveItemStackTo(original, PLAYER_SLOTS, PLAYER_SLOTS + 36, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!moveItemStackTo(original, DEPOSIT_SLOT, PLAYER_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (original.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, original);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        if (remote) {
            if (!player.isAlive() || !player.getInventory().contains(new ItemStack(ModRegistries.TERMINAL.get()))) return false;
            if (player instanceof net.minecraft.server.level.ServerPlayer p) {
                var mod = com.evolt.teamecon.TeamEconomyMod.get();
                return mod.casinoProgression() != null && mod.casinoProgression().terminalAccess(p, mod.economy().manager()).unlocked();
            }
            return true;
        }
        return ContainerLevelAccess.create(player.level(), pos).evaluate((level, p) ->
                player.isAlive() && player.distanceToSqr(p.getX() + .5D, p.getY() + .5D, p.getZ() + .5D) <= 64D
                        && level.getBlockState(p).getBlock() instanceof CasinoMachineBlock machine
                        && machine.game().id().equals(game), false);
    }

    /** Remote menus own the deposit container, so anything left in it goes back to the player. */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) clearContainer(player, deposit);
        if (remote && player instanceof net.minecraft.server.level.ServerPlayer p && !stillValid(p)) {
            var mod = com.evolt.teamecon.TeamEconomyMod.get();
            if (mod.gambling() != null) {
                var run = mod.gambling().sessionOf(p.getUUID());
                if (run != null && !run.worldBound() && !run.liveCrash()) mod.gambling().cashOut(p.getUUID(),
                        com.evolt.teamecon.team.TeamUtil.walletKey(p.getServer(), p.getUUID()), p.getName().getString(), run.game());
            }
        }
    }
}
