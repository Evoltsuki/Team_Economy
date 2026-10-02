package com.evolt.teamecon.init;

import com.evolt.teamecon.TeamEconomyMod;
import com.evolt.teamecon.casino.CasinoMachineBlock;
import com.evolt.teamecon.casino.CasinoMachineBlockEntity;
import com.evolt.teamecon.casino.MachineBlockItem;
import com.evolt.teamecon.casino.CasinoMenu;
import com.evolt.teamecon.casino.GameType;
import com.evolt.teamecon.casino.TerminalItem;
import com.evolt.teamecon.shop.ShopBlock;
import com.evolt.teamecon.shop.ShopMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * All registry content: four game machines, the wireless terminal, the menu and the creative tab.
 */
public final class ModRegistries {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TeamEconomyMod.MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TeamEconomyMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TeamEconomyMod.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, TeamEconomyMod.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TeamEconomyMod.MOD_ID);

    public static final DeferredItem<TerminalItem> TERMINAL =
            ITEMS.registerItem("terminal", props -> new TerminalItem(props.rarity(Rarity.EPIC).stacksTo(1)));
    public static final DeferredItem<com.evolt.teamecon.casino.GuideBookItem> GUIDE_BOOK =
            ITEMS.registerItem("guide_book",props->new com.evolt.teamecon.casino.GuideBookItem(props.stacksTo(1)));
    public static final java.util.Map<com.evolt.teamecon.scratch.ScratchKind, DeferredItem<com.evolt.teamecon.scratch.ScratchCardItem>> SCRATCH_CARDS = new java.util.EnumMap<>(com.evolt.teamecon.scratch.ScratchKind.class);
    static {
        for (var kind : com.evolt.teamecon.scratch.ScratchKind.values())
            SCRATCH_CARDS.put(kind, ITEMS.registerItem("scratch_card_" + kind.id(), props -> new com.evolt.teamecon.scratch.ScratchCardItem(props, kind)));
    }

    public static final DeferredBlock<CasinoMachineBlock> SLOT_MACHINE = machine("slot_machine", GameType.SLOTS);
    public static final DeferredBlock<CasinoMachineBlock> MULTIPLIER_MACHINE = machine("multiplier_machine", GameType.MULTIPLIER);
    public static final DeferredBlock<CasinoMachineBlock> SCRATCH_TABLE = machine("scratch_table", GameType.SCRATCH);
    public static final DeferredBlock<CasinoMachineBlock> HILO_TABLE = machine("hilo_table", GameType.HILO);
    public static final DeferredBlock<CasinoMachineBlock> ROULETTE_TABLE = machine("roulette_table", GameType.ROULETTE);
    public static final DeferredBlock<CasinoMachineBlock> PENGUIN_MACHINE = machine("penguin_machine", GameType.PENGUIN);
    public static final DeferredBlock<CasinoMachineBlock> COLOR_WHEEL_TABLE = machine("color_wheel_table", GameType.COLOR_WHEEL);
    public static final DeferredBlock<com.evolt.teamecon.casino.MachinePartBlock> MACHINE_PART = BLOCKS.registerBlock("machine_part",
            com.evolt.teamecon.casino.MachinePartBlock::new,BlockBehaviour.Properties.of().strength(3F).sound(SoundType.METAL)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK).noOcclusion().noLootTable());

    private static DeferredBlock<CasinoMachineBlock> machine(String name, GameType game) {
        DeferredBlock<CasinoMachineBlock> block = BLOCKS.registerBlock(name,
                props -> new CasinoMachineBlock(props, game),
                BlockBehaviour.Properties.of()
                        .strength(3.0F)
                        .sound(SoundType.METAL)
                        .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
                        .noOcclusion());
        return block;
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CasinoMachineBlockEntity>> CASINO_MACHINE_ENTITY =
            BLOCK_ENTITIES.register("casino_machine", () ->
                    BlockEntityType.Builder.of(CasinoMachineBlockEntity::new,
                                    SLOT_MACHINE.get(), MULTIPLIER_MACHINE.get(),
                                    SCRATCH_TABLE.get(), HILO_TABLE.get(), ROULETTE_TABLE.get(), PENGUIN_MACHINE.get(), COLOR_WHEEL_TABLE.get())
                            .build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<CasinoMenu>> CASINO_MENU =
            MENUS.register("casino", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(CasinoMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<com.evolt.teamecon.casino.MachineBetMenu>> MACHINE_BET_MENU =
            MENUS.register("machine_bet", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(com.evolt.teamecon.casino.MachineBetMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<com.evolt.teamecon.scratch.ScratchCardMenu>> SCRATCH_MENU =
            MENUS.register("scratch_card", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(com.evolt.teamecon.scratch.ScratchCardMenu::new));

    public static final DeferredItem<BlockItem> SLOT_MACHINE_ITEM =
            ITEMS.registerItem("slot_machine", props -> new MachineBlockItem(SLOT_MACHINE.get(), props),
                    new Item.Properties());

    public static final DeferredItem<BlockItem> MULTIPLIER_MACHINE_ITEM =
            ITEMS.registerItem("multiplier_machine", props -> new MachineBlockItem(MULTIPLIER_MACHINE.get(), props),
                    new Item.Properties());

    public static final DeferredItem<BlockItem> SCRATCH_TABLE_ITEM =
            ITEMS.registerItem("scratch_table", props -> new MachineBlockItem(SCRATCH_TABLE.get(), props),
                    new Item.Properties());

    public static final DeferredItem<BlockItem> HILO_TABLE_ITEM =
            ITEMS.registerItem("hilo_table", props -> new MachineBlockItem(HILO_TABLE.get(), props),
                    new Item.Properties());

    public static final DeferredBlock<ShopBlock> SHOP_MACHINE = BLOCKS.registerBlock("shop_machine",
            ShopBlock::new,
            BlockBehaviour.Properties.of().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
                    .strength(3.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion());

    public static final DeferredItem<BlockItem> ROULETTE_TABLE_ITEM =
            ITEMS.registerItem("roulette_table", props -> new MachineBlockItem(ROULETTE_TABLE.get(), props));
    public static final DeferredItem<BlockItem> PENGUIN_MACHINE_ITEM =
            ITEMS.registerItem("penguin_machine", props -> new MachineBlockItem(PENGUIN_MACHINE.get(), props));
    public static final DeferredItem<BlockItem> COLOR_WHEEL_TABLE_ITEM =
            ITEMS.registerItem("color_wheel_table", props -> new MachineBlockItem(COLOR_WHEEL_TABLE.get(), props));

    public static final DeferredBlock<ShopBlock> BLIND_BOX_MACHINE = BLOCKS.registerBlock("blind_box_machine",ShopBlock::new,
            BlockBehaviour.Properties.of().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK).strength(3F).sound(SoundType.METAL).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK));
    public static final DeferredItem<BlockItem> BLIND_BOX_MACHINE_ITEM = ITEMS.registerSimpleBlockItem("blind_box_machine",BLIND_BOX_MACHINE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.evolt.teamecon.shop.ShopMachineBlockEntity>> SHOP_MACHINE_ENTITY =
            BLOCK_ENTITIES.register("shop_machine", () -> BlockEntityType.Builder.of(
                    com.evolt.teamecon.shop.ShopMachineBlockEntity::new, SHOP_MACHINE.get(), BLIND_BOX_MACHINE.get()).build(null));

    public static final DeferredItem<BlockItem> SHOP_MACHINE_ITEM =
            ITEMS.registerSimpleBlockItem("shop_machine", SHOP_MACHINE, new Item.Properties());

    public static final DeferredHolder<MenuType<?>, MenuType<ShopMenu>> SHOP_MENU =
            MENUS.register("shop", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(ShopMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<com.evolt.teamecon.price.PriceAdminMenu>> PRICE_ADMIN_MENU =
            MENUS.register("price_admin", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(com.evolt.teamecon.price.PriceAdminMenu::new));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB =
            CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.teamecon"))
                    .icon(() -> new ItemStack(SLOT_MACHINE_ITEM.value()))
                    .displayItems((parameters, output) -> {
                        output.accept(SLOT_MACHINE_ITEM.value());
                        output.accept(MULTIPLIER_MACHINE_ITEM.value());
                        output.accept(HILO_TABLE_ITEM.value());
                        output.accept(ROULETTE_TABLE_ITEM.value());
                        output.accept(PENGUIN_MACHINE_ITEM.value());
                        output.accept(COLOR_WHEEL_TABLE_ITEM.value());
                        output.accept(SHOP_MACHINE_ITEM.value());
                        output.accept(BLIND_BOX_MACHINE_ITEM.value());
                        output.accept(GUIDE_BOOK.value());
                    })
                    .build());

    private ModRegistries() {
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        CREATIVE_TABS.register(bus);
    }
}
