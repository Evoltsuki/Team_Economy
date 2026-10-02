package com.evolt.teamecon.qa;

import com.evolt.teamecon.TeamEconomyMod;
import com.evolt.teamecon.casino.CasinoMachineBlock;
import com.evolt.teamecon.casino.CasinoMachineBlockEntity;
import com.evolt.teamecon.casino.MachineLayout;
import com.evolt.teamecon.scratch.*;
import com.evolt.teamecon.gambling.PendingMachineGame;
import com.evolt.teamecon.client.*;
import com.evolt.teamecon.init.ModRegistries;
import com.evolt.teamecon.shop.ShopBlock;
import com.evolt.teamecon.shop.ShopMachineBlockEntity;
import com.evolt.teamecon.team.TeamUtil;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Internal client integration checks and real framebuffer captures, never shipped in the mod. */
@EventBusSubscriber(modid = "teamecon", value = Dist.CLIENT)
public final class VisualQa {
    private record Step(String name, BooleanSupplier run) {}
    private static final Deque<Step> STEPS = new ArrayDeque<>();
    private static final List<String> PASSED = new ArrayList<>();
    private static final List<String> CAPTURES = new ArrayList<>();
    private static final Map<String, String> RESULTS = new LinkedHashMap<>();
    private static final BlockPos SHOP = new BlockPos(32, 64, 0);
    private static boolean requestedWorld, planned, finished;
    private static volatile boolean worldReady;
    private static int stepTicks, ticks;
    private static long requestBefore;
    private static volatile long balanceBefore;
    private static volatile PendingMachineGame pendingGame;
    private static UUID openedTicket;
    private static String pendingCapture;
    private static java.util.concurrent.CompletableFuture<Void> languageReload;
    private static volatile double crashBefore;
    private static final long STARTED = System.nanoTime();

    private static Minecraft mc() { return Minecraft.getInstance(); }
    private static Path output() { return mc().gameDirectory.toPath().resolve(RUN_NAME); }
    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
    }
    private static void action(String name, Runnable action) {
        STEPS.add(new Step(name, () -> { action.run(); return true; }));
    }
    private static void until(String name, BooleanSupplier ready) { STEPS.add(new Step(name, ready)); }
    private static void delay(int duration) { until("wait " + duration + " ticks", () -> stepTicks >= duration); }
    private static void snapshot(String name) {
        snapshot(name, () -> {});
    }
    private static void snapshot(String name, Runnable positionPointer) {
        action("prepare " + name, () -> {
            if(mc().screen!=null){
                org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc().getWindow().getWindow(),1,1);
                // Unfocused GLFW windows can defer cursor callbacks; keep captures independent of desktop focus.
                try {
                    var move=net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove",long.class,double.class,double.class);
                    move.setAccessible(true);move.invoke(mc().mouseHandler,mc().getWindow().getWindow(),1D,1D);
                } catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            }
            positionPointer.run();
        });
        delay(2);
        action("capture " + name, () -> {checkLayout();mc().getToasts().clear();pendingCapture=name;});
        until("save " + name, () -> CAPTURES.contains(name));
    }
    private static void server(Consumer<ServerPlayer> work) {
        var server = Objects.requireNonNull(mc().getSingleplayerServer());
        var id = Objects.requireNonNull(mc().player).getUUID();
        server.execute(() -> {
            try { work.accept(Objects.requireNonNull(server.getPlayerList().getPlayer(id))); }
            catch (Throwable ex) { mc().execute(() -> fail(ex)); }
        });
    }
    private static void camera(double x, double y, double z, float yaw, float pitch) {
        server(p -> {
            p.teleportTo(p.serverLevel(), x, y, z, yaw, pitch);
            p.getAbilities().flying = true; p.onUpdateAbilities(); p.setNoGravity(true);
        });
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("teamecon.visualQa") || finished) return;
        try {
            ticks++;
            if(teamPreview!=null)TeamBoardHud.update(teamPreview);
            require((System.nanoTime() - STARTED) / 1_000_000_000L < 600, "Client QA exceeded ten minutes");
            if (!requestedWorld) {
                if (!(mc().screen instanceof TitleScreen) || mc().getOverlay() != null) return;
                requestedWorld = true;
                mc().options.guiScale().set(2);
                mc().options.bobView().set(false);
                mc().options.fov().set(60);
                mc().options.hideGui = true;
                mc().getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                mc().resizeDisplay();
                var settings = new LevelSettings("Team Economy Visual QA", net.minecraft.world.level.GameType.CREATIVE,
                        false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
                mc().createWorldOpenFlows().createFreshLevel("teamecon-qa-" + System.currentTimeMillis(), settings,
                        new WorldOptions(20260920L, false, false),
                        registry -> registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions(), new TitleScreen());
                return;
            }
            if (!planned) {
                if (mc().player == null || mc().level == null || mc().getSingleplayerServer() == null || mc().screen != null) return;
                planned = true; plan(); setup();
            }
            if (STEPS.isEmpty()) { finish(); return; }
            stepTicks++;
            require(stepTicks < 600, "Timed out: " + STEPS.getFirst().name());
            Step step = STEPS.getFirst();
            if (step.run().getAsBoolean()) {
                if (!step.name().startsWith("wait ")) {
                    PASSED.add(step.name()); TeamEconomyMod.LOGGER.info("Visual QA: {}", step.name());
                }
                STEPS.removeFirst(); stepTicks = 0;
            }
        } catch (Throwable ex) { fail(ex); }
    }

    private static void setup() {
        server(p -> {
            var level = p.serverLevel();
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, p.getServer());
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, p.getServer());
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, p.getServer());
            level.setDayTime(6000);p.getServer().getWorldData().overworldData().setGameTime(10000);
            for (int x = -5; x <= 37; x++) for (int z = -6; z <= 24; z++) {
                level.setBlock(new BlockPos(x, 63, z), ((x + z) % 2 == 0 ? Blocks.POLISHED_DEEPSLATE : Blocks.DEEPSLATE_TILES).defaultBlockState(), 3);
            }
            List<CasinoMachineBlock> machines = List.of(ModRegistries.SLOT_MACHINE.get(), ModRegistries.ROULETTE_TABLE.get(),
                    ModRegistries.COLOR_WHEEL_TABLE.get(), ModRegistries.PENGUIN_MACHINE.get(), ModRegistries.MULTIPLIER_MACHINE.get(), ModRegistries.HILO_TABLE.get());
            for (int i = 0; i < machines.size(); i++) {
                var block = machines.get(i); var pos = new BlockPos(i * 5, 64, 0);
                var state = block.defaultBlockState().setValue(CasinoMachineBlock.FACING, Direction.SOUTH).setValue(CasinoMachineBlock.ASSEMBLED,true);
                level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                block.setPlacedBy(level, pos, state, p, new ItemStack(block));
            }
            level.setBlock(SHOP.below(), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 3);
            level.setBlock(SHOP, ModRegistries.SHOP_MACHINE.get().defaultBlockState().setValue(ShopBlock.FACING, Direction.SOUTH), 3);
            ModRegistries.SHOP_MACHINE.get().setPlacedBy(level,SHOP,level.getBlockState(SHOP),p,new ItemStack(ModRegistries.SHOP_MACHINE.get()));
            var boxPos=SHOP.east(3);var box=ModRegistries.BLIND_BOX_MACHINE.get();
            var boxState=box.defaultBlockState().setValue(ShopBlock.FACING,Direction.SOUTH);
            level.setBlock(boxPos,boxState,3);box.setPlacedBy(level,boxPos,boxState,p,new ItemStack(box));
            p.getInventory().clearContent();
            grant(p,"minecraft:story/iron_tools");
            ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE); tool.setDamageValue(300);
            p.getInventory().items.set(1, tool);
            p.getInventory().items.set(9, new ItemStack(Items.IRON_INGOT, 16));
            p.getInventory().selected = 2; p.inventoryMenu.broadcastChanges();
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(2));
            TeamEconomyMod.get().economy().manager().setBalance(TeamUtil.walletKey(p.getServer(), p.getUUID()), 1_000_000);
            p.teleportTo(level, 16, 67, 24, 180, 9);
            p.getAbilities().flying = true; p.onUpdateAbilities(); p.setNoGravity(true);
            worldReady = true;
        });
    }

    private static final Set<String> SCENARIOS = new LinkedHashSet<>(Arrays.asList(
            System.getProperty("teamecon.qaScenarios", "").split(",")));
    private static final String RUN_NAME = "qa-output-" + System.getProperty("teamecon.qaVersion", "dev") + "-" + String.join("-", SCENARIOS) + "-"
            + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    private static String teamPreview;

    /** Only changed features are planned. A failed group can be rerun with -PqaScenarios=store, etc. */
    private static void plan() {
        require(!SCENARIOS.isEmpty() && Set.of("store","machines","terminal","team","guide","boxes","hilo","food","showcase","readme","prices","boxadmin").containsAll(SCENARIOS),
                "Unknown QA group: " + SCENARIOS);
        RESULTS.put("scenarios",String.join(",",SCENARIOS));
        until("test world ready", () -> worldReady);
        delay(40);
        if(SCENARIOS.contains("store"))planStore();
        if(SCENARIOS.contains("boxadmin"))planBoxAdmin();
        if(SCENARIOS.contains("prices"))planPrices();
        if(SCENARIOS.contains("machines"))planMachines();
        if(SCENARIOS.contains("boxes")&&!SCENARIOS.contains("store"))planBoxes();
        if(SCENARIOS.contains("hilo")&&!SCENARIOS.contains("machines"))planHilo();
        if(SCENARIOS.contains("food"))planFood();
        if(SCENARIOS.contains("terminal"))planTerminal();
        if(SCENARIOS.contains("team"))planTeam();
        if(SCENARIOS.contains("guide")){
            planHandbook();action("guide group complete",()->RESULTS.put("guide","passed"));
        }
        if(SCENARIOS.contains("showcase"))planShowcase();
        if(SCENARIOS.contains("readme"))planReadme();
    }
    private static void scale(int scale) { mc().options.guiScale().set(scale);mc().resizeDisplay(); }
    private static double screenScale(){return mc().screen instanceof CompactContainerScreen<?> screen?screen.panelScale():1;}
    private static void pointAt(double x,double y){
        var window=mc().getWindow();
        double px=x*screenScale()*window.getScreenWidth()/window.getGuiScaledWidth();
        double py=y*screenScale()*window.getScreenHeight()/window.getGuiScaledHeight();
        org.lwjgl.glfw.GLFW.glfwSetCursorPos(window.getWindow(),px,py);
        try{
            var move=net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove",long.class,double.class,double.class);
            move.setAccessible(true);move.invoke(mc().mouseHandler,window.getWindow(),px,py);
        }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
    }
    private static void compactLayouts(String name){
        int[][] cases={{854,480,0},{1280,720,1},{1280,720,2},{1920,1080,0}};
        for(int[] dimensions:cases){
            String id=name+"-"+dimensions[0]+"x"+dimensions[1]+"-scale"+dimensions[2];
            action("resize "+id,()->{org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc().getWindow().getWindow(),dimensions[0],dimensions[1]);scale(dimensions[2]);});
            delay(8);snapshot(id);
            if(name.equals("boxes")&&dimensions[2]==0)snapshot(id+"-prize-tooltip",()->{
                var screen=(BlindBoxScreen)mc().screen;pointAt(screen.getGuiLeft()+140,screen.getGuiTop()+105);
            });
            if(name.equals("store")){
                action("scaled search focus and keyboard "+id,()->{
                    var input=field("gui.teamecon.shop.search");double f=screenScale();
                    require(mc().screen.mouseClicked((input.getX()+5)*f,(input.getY()+5)*f,0),"Search click missed");
                    mc().screen.mouseReleased((input.getX()+5)*f,(input.getY()+5)*f,0);
                    require(input.isFocused(),"Search did not focus");input.setValue("");
                    for(char c:"minecraft:stone".toCharArray())mc().screen.charTyped(c,0);
                    require(input.getValue().equals("minecraft:stone"),"Scaled text input failed");input.setValue("");
                    var screen=(ShopScreen)mc().screen;
                    require(screen.mouseScrolled((screen.getGuiLeft()+40)*f,(screen.getGuiTop()+125)*f,0,-1),"Catalogue scroll missed");
                    click("gui.teamecon.shop.tab.items");
                });
            }
            if(name.equals("boxes"))action("scaled box paging "+id,()->{click("shop.teamecon.pool.rare");click("›");click("‹");});
            if(name.equals("terminal")){
                action("scaled game tile and back "+id,()->click("gui.teamecon.tab.slots"));
                action("stake label clears input "+id,()->{
                    var screen=(CasinoScreen)mc().screen;var input=field("gui.teamecon.bet");
                    int labelEnd=screen.getGuiLeft()+192+mc().font.width(Component.translatable("gui.teamecon.bet"));
                    require(labelEnd+4<input.getX(),"Stake label crosses input border");
                });
                snapshot(id+"-game");
                action("scaled back button "+id,()->click("gui.teamecon.terminal.back"));
            }
        }
        action("restore viewport after "+name,()->{org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc().getWindow().getWindow(),1600,900);scale(2);});delay(8);
    }
    private static void closeScreen(){if(mc().screen!=null)mc().screen.onClose();}
    private static EditBox field(String key){
        String name=Component.translatable(key).getString();
        return mc().screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(e->e.getMessage().getString().equals(name)).findFirst().orElseThrow(()->new IllegalStateException("Missing field: "+name));
    }
    private static boolean hasButton(String key){
        String name=Component.translatable(key).getString();
        return mc().screen.children().stream().anyMatch(e->e instanceof Button b&&b.getMessage().getString().equals(name));
    }
    private static void unlockMachines(ServerPlayer p){
        var manager=TeamEconomyMod.get().economy().manager();var wallet=TeamUtil.walletKey(p.getServer(),p.getUUID());
        manager.setBalance(wallet,2_000_000);
        for(int next=manager.casinoLevel(wallet)+1;next<=5;next++)
            require(manager.upgradeCasinoLevel(wallet,next,TeamEconomyMod.get().casinoProgression().levelCost(next)),"Fixture upgrade failed");
    }
    private static void pricingMode(boolean buy) {
        var screen=(PriceAdminScreen)mc().screen;
        int y=screen.getGuiTop()+(buy?94:146);
        Button b=screen.children().stream().filter(e->e instanceof Button v && v.getY()==y).map(Button.class::cast).findFirst().orElseThrow();
        double f=screenScale();screen.mouseClicked((b.getX()+8)*f,(b.getY()+8)*f,0);screen.mouseReleased((b.getX()+8)*f,(b.getY()+8)*f,0);
    }
    private static void pricingField(boolean buy,String value) {
        mc().screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList().get(buy?1:2).setValue(value);
    }
    private static void pricingReady(){until("price response",()->mc().screen instanceof PriceAdminScreen s&&s.ready());delay(5);}
    private static void planPrices() {
        action("open OP pricing catalogue",()->server(p->{
            p.getServer().getPlayerList().op(p.getGameProfile());p.getInventory().clearContent();
            p.inventoryMenu.sendAllDataToRemote();com.evolt.teamecon.price.PriceAdminMenu.open(p);
        }));
        until("pricing screen open",()->mc().screen instanceof PriceAdminScreen);
        delay(12);snapshot("prices-creative-grid");
        for(String query:List.of("金锭","jinding","jd","minecraft:gold_ingot")){
            action("pricing search "+query,()->{
                edit(query);
                require(mc().screen.children().stream().anyMatch(e->e instanceof Button b && b.visible && b.getMessage().getString().equals(Items.GOLD_INGOT.getDescription().getString())),"Gold absent for "+query);
                checkLayout();
            });
            if(query.equals("jinding"))snapshot("prices-pinyin-search");
        }
        action("select gold without taking it",()->click(Items.GOLD_INGOT.getDescription().getString()));pricingReady();
        action("edit gold prices",()->{pricingMode(true);pricingMode(true);pricingField(true,"2000");pricingMode(false);pricingMode(false);pricingField(false,"80");});
        snapshot("prices-edit-gold");
        action("save gold price",()->click("gui.teamecon.prices.save"));pricingReady();
        action("verify accepted gold prices",()->{
            var d=((PriceAdminScreen)mc().screen).currentPrice();
            require(d.buy()==2000&&d.sell()==80&&d.effectiveBuy()==2000&&d.effectiveSell()==80,"Saved price differs");
            require(((PriceAdminScreen)mc().screen).getMenu().slots.isEmpty()&&mc().player.getInventory().isEmpty(),"Editor generated items");
        });snapshot("prices-saved-gold");
        action("show pinyin alongside pricing",()->edit("jinding"));snapshot("prices-search-and-editor");
        for(String language:List.of("en_us","zh_cn")) {
            action("pricing language "+language,()->{closeScreen();mc().getLanguageManager().setSelected(language);mc().options.languageCode=language;languageReload=mc().reloadResourcePacks();});
            until("pricing language ready",()->languageReload.isDone()&&mc().getOverlay()==null);
            action("open held item pricing",()->server(p->{p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.GOLD_INGOT));com.evolt.teamecon.price.PriceAdminMenu.open(p);}));
            pricingReady();
            action("search selected gold",()->edit(language.equals("en_us")?"gold ingot":"jinding"));
            snapshot(language.equals("en_us")?"prices-editor-en":"prices-editor-zh");
        }
        for(int scale:List.of(1,4,0)){
            action("pricing scale "+scale,()->{scale(scale);checkLayout();});snapshot("prices-scale-"+scale);
        }
        action("restore gold defaults",()->{scale(2);click("gui.teamecon.prices.restore");});pricingReady();
        action("verify defaults",()->require(((PriceAdminScreen)mc().screen).currentPrice().sell()==-1,"Reset not saved"));
        action("find mod item",()->{edit("teamecon_qa:pricing_sample");click("item.teamecon_qa.pricing_sample");});pricingReady();
        action("configure mod item",()->{pricingMode(true);pricingMode(true);pricingField(true,"200");pricingMode(false);pricingMode(false);pricingField(false,"30");click("gui.teamecon.prices.save");});pricingReady();
        action("verify real mod purchase and recycling",()->server(p->{
            String id="teamecon_qa:pricing_sample";var mod=TeamEconomyMod.get();
            var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(id));
            var wallet=TeamUtil.walletKey(p.getServer(),p.getUUID());mod.economy().manager().setBalance(wallet,10000);
            require(mod.shop().buyItem(p,item,1).outcome()==com.evolt.teamecon.shop.ShopService.Outcome.OK,"Mod purchase denied");
            require(mod.economy().manager().getBalance(wallet)==9800,"Mod purchase price differs");
            ItemStack delivered=p.getInventory().items.stream().filter(s->s.is(item)).findFirst().orElseThrow();
            long quote=mod.economy().saleQuote(p,delivered);
            require(quote>0&&mod.economy().sell(p,delivered).total()==quote,"Mod recycling quote differs");
            ItemStack named=new ItemStack(item);named.set(DataComponents.CUSTOM_NAME,Component.literal("Modified sample"));
            require(!mod.prices().canSell(named),"Modified mod item was accepted");
            var loaded=new com.evolt.teamecon.price.PriceOverrides();loaded.load(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get());
            require(loaded.get(id).buy()==200&&loaded.get(id).sell()==30,"Overrides did not persist");
            p.getInventory().clearContent();p.inventoryMenu.sendAllDataToRemote();
        }));delay(8);
        action("reset mod overrides",()->click("gui.teamecon.prices.restore"));pricingReady();
        action("close pricing editor",VisualQa::closeScreen);
        action("pricing complete",()->RESULTS.put("prices","passed"));
    }

    private static void boxAdminReady(){until("box admin ready",()->mc().screen instanceof BoxAdminScreen screen&&screen.ready());delay(5);}
    private static void boxClick(int x,int y,int button){
        var screen=(BoxAdminScreen)mc().screen;double f=screenScale(),px=(screen.getGuiLeft()+x)*f,py=(screen.getGuiTop()+y)*f;
        screen.mouseClicked(px,py,button);screen.mouseReleased(px,py,button);
    }
    private static void boxDrag(int x,int y,int dx,int dy){
        var screen=(BoxAdminScreen)mc().screen;double f=screenScale(),px=(screen.getGuiLeft()+x)*f,py=(screen.getGuiTop()+y)*f;
        double endX=(screen.getGuiLeft()+dx)*f,endY=(screen.getGuiTop()+dy)*f;
        screen.mouseClicked(px,py,0);screen.mouseDragged(endX,endY,0,endX-px,endY-py);screen.mouseReleased(endX,endY,0);
    }
    private static void shiftBoxInventory(int slot){
        try{
            var screen=(BoxAdminScreen)mc().screen;
            var method=BoxAdminScreen.class.getDeclaredMethod("slotClicked",net.minecraft.world.inventory.Slot.class,int.class,int.class,net.minecraft.world.inventory.ClickType.class);
            method.setAccessible(true);method.invoke(screen,screen.getMenu().getSlot(slot),slot,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE);
        }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
    }
    private static void planBoxAdmin(){
        action("prepare box editor",()->{
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc().getWindow().getWindow(),1600,900);scale(2);
            server(p->{p.getServer().getPlayerList().op(p.getGameProfile());p.getInventory().clearContent();
                p.getInventory().setItem(9,new ItemStack(Items.IRON_INGOT,16));p.getInventory().setItem(10,new ItemStack(Items.DIAMOND,8));
                p.getInventory().setItem(11,new ItemStack(Items.EMERALD,4));p.getInventory().setItem(12,new ItemStack(Items.GOLD_INGOT,4));
                p.inventoryMenu.sendAllDataToRemote();com.evolt.teamecon.shop.BoxAdminMenu.open(p);});
        });boxAdminReady();snapshot("box-admin-overview");
        action("new custom box",()->{
            click("gui.teamecon.box_admin.new");
            field("gui.teamecon.box_admin.id").setValue("alchemy");field("gui.teamecon.box_admin.name").setValue("炼金盲盒");field("gui.teamecon.box_admin.price").setValue("100");
            click("gui.teamecon.box_admin.cap_on");click("gui.teamecon.box_admin.add");
            field("gui.teamecon.box_admin.search").setValue("zhiliao");
            require(hasButton("item.minecraft.potion.effect.healing"),"Pinyin search did not find a healing potion");
        });snapshot("box-admin-potion-search");
        action("choose healing potion",()->{
            click("item.minecraft.potion.effect.healing");field("gui.teamecon.box_admin.weight").setValue("3");click("gui.teamecon.box_admin.apply");
            click("gui.teamecon.box_admin.add");field("gui.teamecon.box_admin.search").setValue("minecraft:swiftness");
            click("item.minecraft.potion.effect.swiftness");click("gui.teamecon.box_admin.apply");
        });snapshot("box-admin-edit-pool");
        action("shift copy inventory stack into prize template",()->{
            shiftBoxInventory(0);require(mc().player.getInventory().countItem(Items.IRON_INGOT)==16,"Shift sample consumed inventory");
            require(((BoxAdminScreen)mc().screen).currentPool().getAsJsonArray("entries").size()==3,"Shift sample not added");
        });
        action("drag real diamonds into a template cell",()->boxDrag(142,237,140,94));delay(5);
        action("sample leaves real cursor intact",()->{
            var screen=(BoxAdminScreen)mc().screen;require(screen.getMenu().getCarried().getCount()==8,"Sample consumed real cursor");
            require(screen.currentPool().getAsJsonArray("entries").size()==4,"Drag sample not added");boxClick(142,237,0);
        });delay(5);
        action("move prize template to an empty cell",()->{
            boxDrag(140,94,200,94);var grid=new com.evolt.teamecon.shop.BoxPrizeGrid(((BoxAdminScreen)mc().screen).currentPool().getAsJsonArray("entries"));
            require(grid.get(10)==null&&grid.get(13).get("count").getAsInt()==8,"Template drag did not preserve its stack");
        });snapshot("box-admin-inventory");
        action("remove only sample templates",()->{
            boxClick(160,74,1);boxClick(200,94,1);
            require(((BoxAdminScreen)mc().screen).currentPool().getAsJsonArray("entries").size()==2,"Right click did not remove samples");
            require(mc().player.getInventory().countItem(Items.DIAMOND)==8&&mc().player.getInventory().countItem(Items.IRON_INGOT)==16,"Template movement changed real inventory");
        });
        action("save custom pool",()->click("gui.teamecon.box_admin.save"));boxAdminReady();
        action("custom pool saved without spawning items",()->{
            require(((BoxAdminScreen)mc().screen).currentPool().get("id").getAsString().equals("alchemy"),"Custom box not saved");
            require(mc().player.getInventory().countItem(Items.DIAMOND)==8&&((BoxAdminScreen)mc().screen).getMenu().slots.size()==36,"Editor generated items");
            boxClick(142,237,0);require(((BoxAdminScreen)mc().screen).getMenu().getCarried().getCount()==8,"Inventory pickup failed");closeScreen();
        });delay(5);
        action("reopen saved box editor",()->server(com.evolt.teamecon.shop.BoxAdminMenu::open));boxAdminReady();
        action("select persisted pool",()->click("alchemy"));boxAdminReady();
        action("verify persisted fields",()->{
            require(mc().player.getInventory().countItem(Items.DIAMOND)==8&&((BoxAdminScreen)mc().screen).getMenu().getCarried().isEmpty(),"Closing the editor lost or duplicated the real cursor stack");
            var pool=((BoxAdminScreen)mc().screen).currentPool();require(pool.get("price").getAsLong()==100&&pool.getAsJsonArray("entries").size()==2,"Pool did not persist");
            field("gui.teamecon.box_admin.price").setValue("120");click("gui.teamecon.box_admin.save");
        });boxAdminReady();
        action("external box price edit and reload",()->{
            try {
                Path file=net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("teamecon_blindbox.json");
                var root=com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                for(var entry:root.getAsJsonArray("pools"))if(entry.getAsJsonObject().get("id").getAsString().equals("alchemy"))entry.getAsJsonObject().addProperty("price",121);
                Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(root));
            } catch(java.io.IOException ex){throw new IllegalStateException(ex);}
            click("gui.teamecon.box_admin.refresh");
        });boxAdminReady();
        action("reloaded external price can be edited",()->{
            require(((BoxAdminScreen)mc().screen).currentPool().get("price").getAsLong()==121,"Reload did not read the externally edited file");
            field("gui.teamecon.box_admin.price").setValue("120");click("gui.teamecon.box_admin.save");
        });boxAdminReady();snapshot("box-admin-saved");
        for(int[] layout:new int[][]{{854,480,0},{1280,720,2},{1920,1080,0}}){
            String name="box-admin-"+layout[0]+"x"+layout[1];
            action("resize inventory editor "+name,()->{org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc().getWindow().getWindow(),layout[0],layout[1]);scale(layout[2]);});delay(8);
            action("scaled prize drag and selection "+name,()->{
                boxDrag(120,74,160,114);boxDrag(160,114,120,74);
                require(((BoxAdminScreen)mc().screen).currentPool().getAsJsonArray("entries").size()==2,"Scaled drag changed the prize count");
            });snapshot(name);
        }
        action("restore inventory editor viewport",()->{org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc().getWindow().getWindow(),1600,900);scale(2);});delay(8);
        action("save rearranged template layout",()->click("gui.teamecon.box_admin.save"));boxAdminReady();
        for(String language:List.of("en_us","zh_cn")){
            action("box editor language "+language,()->{closeScreen();mc().getLanguageManager().setSelected(language);mc().options.languageCode=language;languageReload=mc().reloadResourcePacks();});
            until("box editor language ready",()->languageReload.isDone()&&mc().getOverlay()==null);
            action("open translated box editor",()->server(com.evolt.teamecon.shop.BoxAdminMenu::open));boxAdminReady();
            action("select translated custom pool",()->click("alchemy"));boxAdminReady();
            if(language.equals("en_us"))snapshot("box-admin-en");
        }
        action("close box editor",VisualQa::closeScreen);delay(5);
        action("approach custom box counter",()->camera(35.5,64.3,4.2,180,-5));delay(8);
        action("open custom box counter",()->server(p->{
            p.getInventory().clearContent();p.inventoryMenu.sendAllDataToRemote();
            TeamEconomyMod.get().economy().manager().setBalance(TeamUtil.walletKey(p.getServer(),p.getUUID()),100000);
            com.evolt.teamecon.shop.ShopMenu.open(p,SHOP.east(3),false,"boxes");
        }));
        until("custom pool appears",()->mc().screen instanceof BlindBoxScreen box&&ClientShopCache.containerId()==box.getMenu().containerId&&ClientShopCache.boxes().stream().anyMatch(p->p.poolId().equals("alchemy")));
        action("select custom potion pool",()->click("炼金盲盒"));snapshot("boxes-potion-probabilities");
        action("open sixty four unstackable rewards",()->{balanceBefore=ClientShopCache.balance();click("×64");click("gui.teamecon.boxes.open");});
        until("overflow saved on server",()->ClientShopCache.balance()==balanceBefore-7680&&ClientShopCache.pending().stream().mapToInt(ClientShopCache.PrizeRow::count).sum()==28);
        action("view pending prizes",()->click("gui.teamecon.boxes.pending"));snapshot("boxes-pending-64");
        action("reopen with pending rewards",()->{closeScreen();server(p->com.evolt.teamecon.shop.ShopMenu.open(p,SHOP.east(3),false,"boxes"));});
        until("pending survives menu reopen",()->mc().screen instanceof BlindBoxScreen&&ClientShopCache.pending().stream().mapToInt(ClientShopCache.PrizeRow::count).sum()==28);
        action("make room for remaining prizes",()->server(p->{p.getInventory().clearContent();p.inventoryMenu.sendAllDataToRemote();}));delay(5);
        action("claim overflow without another charge",()->click("gui.teamecon.boxes.claim"));
        until("all pending prizes claimed",()->ClientShopCache.pending().isEmpty()&&mc().player.getInventory().countItem(Items.POTION)==28);
        action("verify claim balance",()->require(ClientShopCache.balance()==balanceBefore-7680,"Claim charged again"));
        action("close claim view",VisualQa::closeScreen);delay(5);
        action("open editor for cleanup",()->server(com.evolt.teamecon.shop.BoxAdminMenu::open));boxAdminReady();
        action("select custom box for deletion",()->click("alchemy"));boxAdminReady();
        action("delete custom box with confirmation",()->{click("gui.teamecon.box_admin.delete");click("gui.teamecon.box_admin.confirm_delete");});boxAdminReady();
        action("deleted box absent",()->{require(!hasButton("alchemy"),"Deleted pool still appears");closeScreen();RESULTS.put("boxadmin","passed");});
    }

    private static void planStore(){
        action("view both revised two-block cabinets",()->camera(34,64.4,5.8,180,-2));delay(18);snapshot("store-cabinets-front");
        action("view shop side surfaces",()->camera(30.5,64.5,2.9,-141,-3));delay(12);snapshot("store-shop-side");
        action("view box side surfaces",()->camera(37.5,64.5,2.9,141,-3));delay(12);snapshot("store-box-side");
        action("view rear service panels",()->camera(34,64.5,-3.5,0,-2));delay(12);snapshot("store-cabinets-rear");
        action("approach revised storefront",()->camera(32.5,64,3.9,180,-8));delay(12);
        action("reset isolated QA player preference",()->com.evolt.teamecon.client.ShopTabPreferences.remember(
                mc().gameDirectory.toPath().resolve("config"),mc().player.getUUID(),"items"));
        openShop(false);
        action("initial items page",()->require(hasButton("gui.teamecon.shop.category.all"),"Items page not restored"));
        action("remember sale page",()->click("gui.teamecon.shop.tab.sell"));
        action("close sale page",VisualQa::closeScreen);delay(5);openShop(false);
        action("sale page restored on reopen",()->{
            require(((ShopScreen)mc().screen).getMenu().getSlot(0).isActive(),"Reopened shop lost sale page");
            require(hasButton("gui.teamecon.shop.sell_stack"),"Reopened shop lost sale controls");
        });snapshot("store-remembered-sell");
        action("remember items page",()->click("gui.teamecon.shop.tab.items"));
        action("close items page",VisualQa::closeScreen);delay(5);openShop(false);
        action("items page restored on reopen",()->{
            require(hasButton("gui.teamecon.shop.category.all"),"Reopened shop lost items page");
            require(!((ShopScreen)mc().screen).getMenu().getSlot(0).isActive(),"Sale slots active on items page");
            RESULTS.put("shopPageMemory","sale and items restored after closing and reopening");
        });
        action("visible categories and increased page capacity",()->{
            var screen=(ShopScreen)mc().screen;
            require(screen.pageCapacity()>=50,"Compact shop must retain at least 50 products per page");
            for(String type:List.of("all","blocks","tools","weapons","armor","food","magic","materials"))
                require(hasButton("gui.teamecon.shop.category."+type),"Missing direct category: "+type);
            require(!hasButton("gui.teamecon.shop.tab.boxes"),"Blind boxes remain in the ordinary shop");
            require(ClientShopCache.items().stream().allMatch(r->r.itemKey().startsWith("minecraft:")||r.itemKey().startsWith("teamecon:")),"Third-party item listed");
            RESULTS.put("catalogue",ClientShopCache.items().size()+" items; "+screen.pageCapacity()+" per page");
        });snapshot("store-dense-catalogue");
        action("full pinyin search",()->{
            edit("jinding");
            require(hasButton("item.minecraft.gold_ingot"),"Full pinyin did not find gold ingot");
            require(!hasButton("block.minecraft.gold_block"),"Full pinyin matched the wrong gold item");
        });snapshot("store-pinyin-full");
        action("pinyin initials search",()->{
            edit("jd");require(hasButton("item.minecraft.gold_ingot"),"Pinyin initials did not find gold ingot");
        });snapshot("store-pinyin-initials");
        action("reset pinyin search",()->edit(""));compactLayouts("store");
        action("next catalogue page",()->click("›"));snapshot("store-page-two");
        action("direct tools filter and tag search",()->{click("gui.teamecon.shop.category.tools");edit("#minecraft:pickaxes");});
        snapshot("store-tools-search");
        action("inspect rare axolotl price",()->{click("gui.teamecon.shop.category.all");edit("minecraft:axolotl_bucket");});
        action("rare bucket has its intended purchase floor",()->require(ClientShopCache.items().stream()
                .anyMatch(r->r.itemKey().equals("minecraft:axolotl_bucket")&&r.unitPrice()>=8192),"Cheap axolotl bucket"));
        snapshot("store-rare-bucket-price");
        action("prepare full inventory batch",()->{
            click("gui.teamecon.shop.tab.sell");
            server(p->{
                var items=new net.minecraft.world.item.Item[]{Items.COBBLESTONE,Items.OAK_LOG,Items.WHEAT,Items.IRON_INGOT};
                for(int i=0;i<26;i++)p.getInventory().items.set(9+i,new ItemStack(items[i%items.length],64));
                p.getInventory().items.set(35,new ItemStack(ModRegistries.GUIDE_BOOK.get()));
                p.containerMenu.broadcastChanges();
            });
        });delay(5);
        for(int i=0;i<27;i++){
            int slot=com.evolt.teamecon.shop.ShopMenu.SALE_SLOTS+i;
            action("shift-click sale deposit "+(i+1),()->mc().gameMode.handleInventoryMouseClick(
                    ((ShopScreen)mc().screen).getMenu().containerId,slot,0,ClickType.QUICK_MOVE,mc().player));
        }
        until("all 27 deposits synchronized",()->((ShopScreen)mc().screen).getMenu().sale().getItems().stream().noneMatch(ItemStack::isEmpty)
                &&ClientShopCache.saleQuote()>0);
        snapshot("store-27-deposit-quote");
        action("sell the full supported batch",()->{balanceBefore=ClientShopCache.balance();click("gui.teamecon.shop.sell_stack");});
        until("batch credited and unsupported item retained",()->{
            var sale=((ShopScreen)mc().screen).getMenu().sale();
            return ClientShopCache.balance()>balanceBefore&&sale.getItems().stream().filter(s->!s.isEmpty()).count()==1
                    &&sale.getItem(26).is(ModRegistries.GUIDE_BOOK.get());
        });snapshot("store-batch-sold");
        action("minimum sale layout",()->scale(4));snapshot("store-sale-minimum-gui");
        action("minimum category layout",()->click("gui.teamecon.shop.tab.items"));
        action("minimum shop still shows useful grid",()->require(((ShopScreen)mc().screen).pageCapacity()>=21,"Compact grid too small"));
        snapshot("store-catalogue-minimum-gui");
        action("ticket factor minimum layout",()->{click("gui.teamecon.shop.tab.cards");field("gui.teamecon.shop.factor_input").setValue("10");});
        snapshot("store-ticket-factor-minimum-gui");
        action("restore shop scale",()->scale(2));snapshot("store-ticket-factor");
        action("close storefront and return unsold item",VisualQa::closeScreen);delay(5);
        action("unsupported deposit returned",()->require(mc().player.getInventory().countItem(ModRegistries.GUIDE_BOOK.get())==1,"Unsold item did not return"));
        planBoxes();
    }
    private static void planBoxes(){
        action("approach independent box counter",()->camera(35.5,64.3,4.2,180,-5));delay(10);
        action("open independent box screen without advancements",()->server(p->com.evolt.teamecon.shop.ShopMenu.open(p,SHOP.east(3),false,"boxes")));
        until("independent box catalogue loaded",()->mc().screen instanceof BlindBoxScreen box&&ClientShopCache.containerId()==box.getMenu().containerId&&!ClientShopCache.boxes().isEmpty());
        action("box interface is independent and ungated",()->{
            require(ClientShopCache.boxes().stream().allMatch(ClientShopCache.BoxRow::unlocked),
                    "A configured prize locked a box: " + ClientShopCache.boxes());
            require(((BlindBoxScreen)mc().screen).getMenu().slots.stream().noneMatch(net.minecraft.world.inventory.Slot::isActive),"Box screen exposes shop inventory");
        });snapshot("boxes-independent-screen");
        action("preview contains actual server prizes",()->{
            require(ClientShopCache.boxes().stream().allMatch(b->!b.prizes().isEmpty()),"Missing preview entries");
            require(hasButton("gui.teamecon.boxes.contents")&&hasButton("gui.teamecon.boxes.receipt"),"Missing preview/receipt tabs");
        });
        action("minimum preview layout",()->scale(4));snapshot("boxes-preview-minimum");compactLayouts("boxes");
        action("restore preview size",()->scale(2));
        action("select treasure preview including prize-only eggs",()->{
            click("shop.teamecon.pool.rare");
            var treasure=ClientShopCache.boxes().stream().filter(b->b.poolId().equals("rare")).findFirst().orElseThrow();
            require(treasure.prizes().stream().filter(p->com.evolt.teamecon.shop.BoxPrizePolicy.exclusive(p.itemKey())).count()==com.evolt.teamecon.shop.BoxPrizePolicy.EGGS.size(),
                    "Treasure preview omitted friendly mob eggs");
        });snapshot("boxes-treasure-preview");
        action("buy a real treasure box",()->{
            click("×1");balanceBefore=ClientShopCache.balance();click("gui.teamecon.boxes.open");
        });
        until("treasure purchase debited and delivered",()->ClientShopCache.balance()==balanceBefore-512
                &&ClientShopCache.messageKey().equals("shop.teamecon.box_batch")&&!ClientShopCache.rewards().isEmpty());
        delay(3);snapshot("boxes-treasure-receipt");
        action("return to supply pool",()->click("shop.teamecon.pool.common"));
        for(int count:new int[]{1,10,64}){
            action("open batch of "+count+" boxes",()->{
                click("×"+count);balanceBefore=ClientShopCache.balance();
                click("gui.teamecon.boxes.open");
            });
            until("box batch "+count+" receipt received",()->ClientShopCache.balance()<balanceBefore&&ClientShopCache.messageKey().equals("shop.teamecon.box_batch")
                    &&ClientShopCache.messageArgs().startsWith(count+",")&&!ClientShopCache.rewards().isEmpty());
            delay(20);
            if(count!=1)snapshot("boxes-batch-"+count+"-receipt");
        }
        action("minimum independent box layout",()->scale(4));snapshot("boxes-minimum-gui");
        action("finish box inspection",()->{closeScreen();scale(2);});delay(5);
    }
    private static void planHilo(){
        action("view only changed high-low machine",()->camera(26,64,5,180,-3));delay(15);
        snapshot("hilo-ten-sided-panel");
        for(int side=0;side<2;side++){
            int choice=side;
            action("select high-low side "+side,()->worldClick(5,.62+choice*.82,.90));delay(5);
            action("start ten-sided round",()->worldClick(5,.6,1.39));
            until("high-low animation started",()->machine(5).spinTicks()>0);
            until("high-low settles",()->machine(5).spinTicks()==0&&!machine(5).result().isEmpty());
            action("ten-sided result in range",()->{int roll=Integer.parseInt(machine(5).reels()[1]);require(roll>=1&&roll<=10,"Out-of-range high-low roll");});
            snapshot("hilo-side-"+side+"-result");
        }
    }
    private static void planFood(){
        openShop();
        action("server food prices keep the intended order",()->{
            Map<String,Long> expected=Map.of("potato",8L,"beef",16L,"bread",24L,"cooked_beef",32L);
            expected.forEach((id,price)->require(ClientShopCache.items().stream()
                    .anyMatch(row->row.itemKey().equals("minecraft:"+id)&&row.unitPrice()==price),"Wrong food price: "+id));
        });
        for(String id:List.of("potato","beef","bread","cooked_beef")){
            action("find changed food "+id,()->edit("minecraft:"+id));delay(4);
            snapshot("food-price-"+id);
        }
        action("close food shop",VisualQa::closeScreen);
    }
    private static double offset(int index){return index==1?-.42:index==2?-.50:0;}
    private static void planMachines(){
        action("prepare changed machine access",()->{scale(2);mc().options.hideGui=true;server(p->{unlockMachines(p);p.getInventory().selected=2;p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(2));});});delay(8);
        String[] games={"slots","roulette","color_wheel","penguin","multiplier","hilo"};
        for(int i=0;i<games.length;i++){
            int index=i;String game=games[i];
            action("view revised "+game+" controls at standing height",()->camera(index*5+iWidth(index)/2,64,index==1?3:5,180,index==1?18:-3));
            delay(14);snapshot("machine-"+game+"-panel");
            if(i==0){
                action("manual plus establishes fixed base",()->worldClick(0,1.35,.65));
                until("manual stake changed to 200",()->machine(0).bet()==200);delay(4);
                for(int factor:new int[]{10,2,1}){
                    action("reopen physical factor editor "+factor,()->worldClick(0,1.72,.65));
                    until("physical factor editor ready "+factor,()->mc().screen instanceof MachineBetScreen);
                    action("fixed base remains 200 for "+factor,()->{
                        require(((MachineBetScreen)mc().screen).getMenu().base()==200,"Factor compounded the stored base");edit(""+factor);
                    });
                    if(factor==2)snapshot("machine-factor-reduced");
                    action("apply factor "+factor,()->click("machine.teamecon.bet_input.apply"));
                    until("physical factor "+factor+" applied",()->mc().screen==null&&machine(0).bet()==200L*factor&&machine(0).betFactor()==factor&&machine(0).spinTicks()==0);
                    delay(4);
                }
            }
            if(i==1){
                for(int choice=0;choice<3;choice++){
                    int c=choice;String expected=new String[]{"red","black","number:0"}[c];
                    action("lowered roulette choice "+expected,()->worldClick(1,.6+c*.9,.91+offset(1)));
                    until("roulette choice synchronized "+expected,()->machine(1).choice().equals(expected));delay(4);
                }
                snapshot("machine-roulette-lowered-choices");
            }
            if(i==4){planCrash();continue;}
            action("revised start hitbox "+game,()->worldClick(index,.6,(index==1?1.14:1.39)+offset(index)));
            until("revised start activates "+game,()->machine(index).spinTicks()>0);
            until("changed machine round settled "+game,()->machine(index).spinTicks()==0&&!machine(index).result().isEmpty());
            if(i==2)snapshot("machine-lucky-wheel-result");
            if(i==3){
                action("collect one-hop fixture",()->{if(machine(3).hasRun())worldClick(3,2.5,1.39);});
                until("one-hop fixture ended",()->!machine(3).hasRun());
            }
        }
        action("inspect compact machine HUD",()->{mc().options.hideGui=false;camera(1,64,3.9,180,8);});
        delay(15);snapshot("machine-compact-hud");
        action("finish physical inspection",()->mc().options.hideGui=true);
    }
    private static void seedGames(long seed){
        try{
            var field=com.evolt.teamecon.gambling.GamblingService.class.getDeclaredField("random");field.setAccessible(true);
            ((Random)field.get(TeamEconomyMod.get().gambling())).setSeed(seed);
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static void planCrash(){
        action("prepare deterministic crash draw",()->server(p->seedGames(1)));delay(3);
        action("start accelerating physical crash",()->worldClick(4,.6,1.39));
        until("physical crash starts",()->machine(4).hasRun());delay(32);snapshot("machine-crash-early-growth");
        action("prepare rare hundredfold display fixture",()->server(p->{
            var run=TeamEconomyMod.get().gambling().sessionOf(p.getUUID());
            long now=p.getServer().overworld().getGameTime();
            // QA only: move an existing run's clock to the rare tail; production still draws its deadline randomly.
            run.startCrash(now-com.evolt.teamecon.gambling.CrashGame.limitAfter(100),com.evolt.teamecon.gambling.CrashGame.limitAfter(run.maxMultiplier())+1);
            TeamEconomyMod.get().economy().manager().setDirty();
        }));
        until("hundredfold live multiplier rendered",()->machine(4).hasRun()&&machine(4).multiplier()>=100);
        snapshot("machine-crash-hundredfold");
        action("cash out rare display fixture",()->worldClick(4,1.7,1.39));
        until("rare fixture settled",()->!machine(4).hasRun());
    }
    private static void planTerminal(){
        action("prepare authorized terminal fixture",()->{
            scale(2);server(p->{unlockMachines(p);grant(p,"minecraft:end/kill_dragon");
                p.getInventory().items.set(0,new ItemStack(ModRegistries.TERMINAL.get()));p.getInventory().selected=0;
                p.inventoryMenu.sendAllDataToRemote();p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));});
        });delay(6);openTerminal();
        action("dashboard has no blind box service",()->require(!hasButton("gui.teamecon.terminal.boxes"),"Terminal retained box entry"));
        snapshot("terminal-revised-dashboard");compactLayouts("terminal");
        action("open remote revised shop",()->click("gui.teamecon.terminal.shop"));
        until("remote shop ready",()->mc().screen instanceof ShopScreen shop&&shop.getMenu().isRemote()&&ClientShopCache.containerId()==shop.getMenu().containerId);
        final int[] widgets={0};
        action("record remote shop widget count",()->widgets[0]=mc().screen.children().size());delay(12);
        action("remote home control does not duplicate per frame",()->require(mc().screen.children().size()==widgets[0],"Remote shop adds duplicate widgets"));
        snapshot("terminal-revised-shop");
        action("return from remote shop",()->click("gui.teamecon.shop.terminal_home"));until("dashboard returned",VisualQa::casinoReady);
        tab("slots");
        action("set terminal fixed base",()->field("gui.teamecon.bet").setValue("100"));
        for(int factor:new int[]{10,2,1}){
            action("set terminal factor "+factor,()->{field("gui.teamecon.bet_factor").setValue(""+factor);click("gui.teamecon.bet_multiply");});
            action("terminal factor uses original base "+factor,()->require(field("gui.teamecon.bet").getValue().equals(""+(100*factor)),"Terminal factor compounded"));
        }
        snapshot("terminal-start-stake-controls");
        tab("roulette");snapshot("terminal-roulette-integer-prizes");
        action("minimum remote controls",()->scale(4));snapshot("terminal-roulette-minimum-gui");
        action("restore remote scale",()->scale(2));tab("color_wheel");
        play("gui.teamecon.roulette.spin","color_wheel");snapshot("terminal-lucky-wheel-spinning");delay(76);snapshot("terminal-lucky-wheel-mapped-result");
        action("lucky wheel result at Auto scale",()->scale(0));snapshot("terminal-lucky-wheel-auto-result");
        action("restore scale after lucky wheel result",()->scale(2));
        tab("penguin");snapshot("terminal-penguin-clipped-viewport");
        action("finish terminal inspection",VisualQa::closeScreen);delay(5);
        action("buy scratch for scaled drag",()->server(p->{
            p.getInventory().clearContent();
            require(TeamEconomyMod.get().shop().buyTicket(p,"match",1).outcome()==com.evolt.teamecon.shop.ShopService.Outcome.OK,"Scratch fixture failed");
            p.getInventory().selected=0;p.inventoryMenu.sendAllDataToRemote();
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));
        }));delay(8);leftClickTicket();until("scaled scratch opened",()->mc().screen instanceof ScratchCardScreen);
        action("scratch Auto scale",()->scale(0));snapshot("scratch-auto-before");
        action("drag scratch in physical coordinates",VisualQa::scratchAll);
        until("scaled scratch settled",()->((ScratchCardScreen)mc().screen).getMenu().settledAmount()>=0);
        snapshot("scratch-auto-settled");action("close scaled scratch",VisualQa::closeScreen);
    }
    private static void planTeam(){
        action("create actual FTB party board fixture",()->server(p->{
            require(TeamUtil.isTeamsLoaded(),"Team QA requires -PwithFtbTeams");
            var teams=(dev.ftb.mods.ftbteams.data.TeamManagerImpl)dev.ftb.mods.ftbteams.api.FTBTeamsAPI.api().getManager();
            try { teams.createPartyTeam(p,"田野小队","Visual QA",dev.ftb.mods.ftblibrary.icon.Color4I.rgb(0x428c72)); }
            catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new IllegalStateException(e);}
            TeamEconomyMod.get().economy().manager().setBalance(TeamUtil.walletKey(p.getServer(),p.getUUID()),123456);
        }));
        action("show real transparent team board",()->{scale(2);mc().options.hideGui=false;camera(34,64.3,5.8,180,-2);});
        delay(28);until("real board received",TeamBoardHud::ready);snapshot("team-real-party-board");
        action("preview crowded team and negative earnings",()->{
            var data=new com.google.gson.JsonObject();data.addProperty("team","田野小队 · 七人以上预览");data.addProperty("balance",4235678);
            var members=new com.google.gson.JsonArray();String[] names={"LongPlayerName16","小麦农夫","矿工阿白","安静的钓鱼人","旅行者","Builder_07","离线队友","远方的朋友","Slime"};
            for(int i=0;i<names.length;i++){
                var member=new com.google.gson.JsonObject();member.addProperty("name",names[i]);
                member.addProperty("earned",i==0?12345678:i==3?-12049:(i+1)*2356);member.addProperty("online",i<6);members.add(member);
            }
            data.add("members",members);teamPreview=data.toString();TeamBoardHud.update(teamPreview);
        });
        until("crowded board first page",()->mc().level.getGameTime()/120%2==0);
        snapshot("team-multiple-members-preview");
        until("crowded board second page",()->mc().level.getGameTime()/120%2==1);
        snapshot("team-members-second-page");
        action("minimum transparent board preview",()->scale(4));
        until("minimum board first page",()->mc().level.getGameTime()/120%2==0);
        snapshot("team-minimum-gui-preview");
        action("restore live board",()->{teamPreview=null;scale(2);mc().options.hideGui=true;});delay(22);
    }
    private static void planHandbook(){
        action("check revised Patchouli resources",VisualQa::checkHandbook);
        action("open real handbook item",()->{
            scale(2);server(p->{p.getInventory().items.set(8,new ItemStack(ModRegistries.GUIDE_BOOK.get()));p.getInventory().selected=8;
                p.inventoryMenu.sendAllDataToRemote();p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(8));
                ModRegistries.GUIDE_BOOK.get().use(p.level(),p,InteractionHand.MAIN_HAND);});
        });
        until("real handbook opened",()->com.evolt.teamecon.casino.GuideBookItem.BOOK.equals(vazkii.patchouli.api.PatchouliAPI.get().getOpenBookGui()));
        snapshot("guide-revised-landing");
        for(String machine:List.of("hilo","penguin","color_wheel","roulette","slots","multiplier")) {
            guidePage(machine,2,false);
            guidePage(machine,4,false);
        }
        for(String entry:List.of("welcome:1","welcome:2","welcome:4","economy:2","economy:4","boxes:1","boxes:2","boxes:4","penguin:3","food:1","teams:1","terminal:1","hilo:1","credits:1")){
            String[] parts=entry.split(":");guidePage(parts[0],Integer.parseInt(parts[1]),false);
        }
        action("minimum handbook layout",()->scale(4));snapshot("guide-minimum-gui");
        action("load English revised handbook",()->{
            closeScreen();scale(2);mc().getLanguageManager().setSelected("en_us");mc().options.languageCode="en_us";languageReload=mc().reloadResourcePacks();
        });until("English resources loaded",()->languageReload.isDone()&&mc().getOverlay()==null);
        action("English revised handbook valid",VisualQa::checkHandbook);
        guidePage("welcome",1,true);guidePage("boxes",2,true);guidePage("food",1,true);guidePage("credits",1,true);
        for(String locale:List.of("zh_tw","de_de")){
            action("load handbook locale "+locale,()->{
                closeScreen();mc().getLanguageManager().setSelected(locale);mc().options.languageCode=locale;
                languageReload=mc().reloadResourcePacks();
            });until("handbook locale loaded "+locale,()->languageReload.isDone()&&mc().getOverlay()==null);
            action("localized handbook valid "+locale,VisualQa::checkHandbook);
            action("open localized handbook "+locale,()->vazkii.patchouli.api.PatchouliAPI.get().openBookEntry(
                    com.evolt.teamecon.casino.GuideBookItem.BOOK,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("teamecon","boxes"),1));
            delay(4);snapshot("guide-"+locale+"-boxes");
            action("locale uses translated text or English fallback "+locale,()->{
                String title=Component.translatable("gui.teamecon.boxes.title").getString();
                require(!title.equals("gui.teamecon.boxes.title"),"Raw translation key in "+locale);
                if(locale.equals("de_de"))require(title.equals("Mystery Box Counter"),"Missing English UI fallback: "+title);
            });
        }
        action("restore Chinese language",()->{closeScreen();mc().getLanguageManager().setSelected("zh_cn");mc().options.languageCode="zh_cn";languageReload=mc().reloadResourcePacks();});
        until("Chinese resources restored",()->languageReload.isDone()&&mc().getOverlay()==null);
    }
    private static void guidePage(String chapter,int page,boolean english){
        action("open revised guide "+chapter+" "+page+(english?" English":""),()->vazkii.patchouli.api.PatchouliAPI.get().openBookEntry(
                com.evolt.teamecon.casino.GuideBookItem.BOOK,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("teamecon",chapter),page));
        delay(4);snapshot("guide-"+(english?"en-":"")+chapter+"-"+page);
    }
    private static void checkHandbook(){
        var book=vazkii.patchouli.common.book.BookRegistry.INSTANCE.books.get(com.evolt.teamecon.casino.GuideBookItem.BOOK);
        require(book!=null,"Patchouli book not registered");
        require(!book.getContents().isErrored(),"Patchouli content error: "+book.getContents().getException());
        require(book.getContents().entries.size()==21&&book.getContents().categories.size()==3,"Missing guide chapters");
        for(var entry:book.getContents().entries.values())for(var page:entry.getPages()){
            if(page.sourceObject==null||!page.sourceObject.has("text"))continue;
            String text=page.sourceObject.get("text").getAsString();
            require(page.i18n(text).equals(text),"Book text was formatted incorrectly: "+entry.getId());
            require(!text.matches("(?is).*(返奖率|期望值|\\bRTP\\b|return.to.player|expected value).*"),"Handbook exposes hidden statistics: "+entry.getId());
            require(!text.contains("\n")&&!text.contains("\r"),"Handbook contains raw line feed glyphs: "+entry.getId());
        }
    }

    private static void startClip(String name) {
        action("record " + name, () -> {
            mc().getToasts().clear(); mc().gui.getChat().clearMessages(true);
            if (!System.getProperty("teamecon.qaVideoEncoder", "").isBlank())
                GameplayCapture.start(output().resolve("clips"), name);
        });
    }
    private static void endClip(){action("finish recorded clip",GameplayCapture::stop);}

    /** A staged creative showroom for actual gameplay footage, separate from regression groups. */
    private static void planShowcase(){
        action("prepare release showroom",()->{
            closeScreen();mc().options.hideGui=true;mc().options.fov().set(65);scale(2);
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc().getWindow().getWindow(),1600,900);
            server(p->{
                var level=p.serverLevel();
                for(int x=-10;x<=43;x++)for(int z=-8;z<=33;z++)
                    level.setBlock(new BlockPos(x,63,z),((x%5==4||z%7==6)?Blocks.SPRUCE_PLANKS:Blocks.SMOOTH_STONE).defaultBlockState(),3);
                for(int x=-4;x<=40;x++)for(int y=64;y<=69;y++)
                    level.setBlock(new BlockPos(x,y,-4),(y==69?Blocks.SPRUCE_PLANKS:Blocks.SMOOTH_QUARTZ).defaultBlockState(),3);
                for(int x=-3;x<=40;x+=5){
                    for(int y=64;y<=67;y++)level.setBlock(new BlockPos(x,y,-3),Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(),3);
                    level.setBlock(new BlockPos(x,68,-3),Blocks.SHROOMLIGHT.defaultBlockState(),3);
                }
                p.getInventory().clearContent();p.getInventory().selected=2;p.inventoryMenu.sendAllDataToRemote();
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(2));
                TeamEconomyMod.get().economy().manager().setBalance(TeamUtil.walletKey(p.getServer(),p.getUUID()),200);
            });
        });delay(30);
        action("overview camera",()->camera(18,65.4,24,180,3));delay(15);
        snapshot("release-showroom");startClip("01-overview");
        until("slow showroom pan",()->{
            mc().player.setYRot(169+stepTicks*.12F);return stepTicks>=180;
        });endClip();

        action("approach recycling counter",()->camera(32.5,64.3,4.3,180,-4));delay(12);
        action("prepare honest mixed-resource example",()->server(p->{
            p.getInventory().clearContent();
            ItemStack[] basket={new ItemStack(Items.IRON_INGOT,32),new ItemStack(Items.COPPER_INGOT,32),
                    new ItemStack(Items.GOLD_INGOT,8),new ItemStack(Items.COAL,64),new ItemStack(Items.OAK_LOG,64),
                    new ItemStack(Items.WHEAT,64),new ItemStack(Items.COBBLESTONE,64)};
            for(int i=0;i<basket.length;i++)p.getInventory().items.set(9+i,basket[i]);
            p.inventoryMenu.sendAllDataToRemote();
        }));delay(5);
        action("use documented starter shop command",()->server(p->p.getServer().getCommands()
                .performPrefixedCommand(p.createCommandSourceStack(),"teamecon shop")));
        until("command shop opened",()->mc().screen instanceof ShopScreen && ClientShopCache.items().size()>1000);
        action("show recycling tray",()->click("gui.teamecon.shop.tab.sell"));delay(5);
        startClip("02-recycling");delay(20);
        for(int i=0;i<7;i++){
            int slot=com.evolt.teamecon.shop.ShopMenu.SALE_SLOTS+i;
            action("deposit showcase stack "+i,()->mc().gameMode.handleInventoryMouseClick(
                    ((ShopScreen)mc().screen).getMenu().containerId,slot,0,ClickType.QUICK_MOVE,mc().player));delay(8);
        }
        delay(20);snapshot("release-recycling");delay(20);
        action("sell example supplies",()->click("gui.teamecon.shop.sell_stack"));
        until("example credited",()->ClientShopCache.balance()>=1200);delay(40);endClip();
        action("show actual first upgrade",()->click("gui.teamecon.shop.tab.levels"));delay(5);
        startClip("03-levels");delay(40);snapshot("release-upgrade");
        action("buy first upgrade",()->click("gui.teamecon.shop.upgrade"));
        until("first upgrade applied",()->ClientCasinoProgression.level()==2);delay(70);endClip();
        action("close showcase shop",VisualQa::closeScreen);

        action("prepare funded machine demonstration",()->server(p->{
            unlockMachines(p);p.getInventory().clearContent();p.inventoryMenu.sendAllDataToRemote();
            p.getInventory().selected=2;p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(2));
        }));delay(5);
        action("high-low camera",()->camera(26,64.2,4.8,180,-3));delay(12);
        startClip("04-hilo");delay(24);
        action("play one natural high-low round",()->worldClick(5,.6,1.39));delay(45);
        snapshot("release-hilo");delay(60);endClip();
        action("slime camera",()->camera(16.3,64.6,6.6,180,-1));delay(12);
        startClip("05-slime");delay(20);
        action("play one natural slime hop",()->worldClick(3,.6,1.39));delay(40);
        action("continue or begin a second hop",()->worldClick(3,.6,1.39));delay(40);
        action("collect surviving hop",()->{if(machine(3).hasRun())worldClick(3,2.5,1.39);});
        delay(50);snapshot("release-slime");endClip();

        action("buy a normal scratch ticket for filming",()->server(p->{
            p.getInventory().clearContent();
            require(TeamEconomyMod.get().shop().buyTicket(p,"match",1).outcome()==com.evolt.teamecon.shop.ShopService.Outcome.OK,"Showcase ticket purchase failed");
            p.getInventory().selected=0;p.inventoryMenu.sendAllDataToRemote();
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));
        }));delay(8);leftClickTicket();
        until("filmed scratch ticket opened",()->mc().screen instanceof ScratchCardScreen);
        startClip("06-scratch");delay(30);snapshot("release-scratch");
        for(int y=78;y<=190;y+=7){
            int row=y;action("scratch visible row "+y,()->{
                int left=(mc().screen.width-320)/2,top=(mc().screen.height-240)/2;
                mc().screen.mouseDragged((left+208)*screenScale(),(top+row)*screenScale(),0,188*screenScale(),0);
            });delay(5);
        }
        delay(50);endClip();action("close filmed ticket",VisualQa::closeScreen);

        action("box showcase camera",()->camera(35.5,64.3,4.2,180,-5));delay(10);
        action("open release box preview",()->server(p->{
            p.getInventory().clearContent();p.inventoryMenu.sendAllDataToRemote();
            com.evolt.teamecon.shop.ShopMenu.open(p,SHOP.east(3),false,"boxes");
        }));until("release box screen ready",()->mc().screen instanceof BlindBoxScreen && !ClientShopCache.boxes().isEmpty());
        action("show treasure prizes",()->click("shop.teamecon.pool.rare"));delay(6);
        startClip("07-boxes");snapshot("release-boxes");delay(70);
        action("open ten normal treasure boxes",()->{click("×10");balanceBefore=ClientShopCache.balance();click("gui.teamecon.boxes.open");});
        until("filmed boxes delivered",()->ClientShopCache.balance()==balanceBefore-5120&&!ClientShopCache.rewards().isEmpty());
        delay(70);endClip();action("close filmed boxes",VisualQa::closeScreen);

        openShop();action("show food pricing",()->{click("gui.teamecon.shop.category.food");edit("minecraft:cooked_beef");});delay(6);
        startClip("08-food");snapshot("release-food");delay(70);
        action("show potato pricing",()->edit("minecraft:potato"));delay(70);endClip();action("close filmed food shop",VisualQa::closeScreen);

        action("create a real party for the release board",()->server(p->{
            var teams=(dev.ftb.mods.ftbteams.data.TeamManagerImpl)dev.ftb.mods.ftbteams.api.FTBTeamsAPI.api().getManager();
            try{teams.createPartyTeam(p,"采集小队","",dev.ftb.mods.ftblibrary.icon.Color4I.rgb(0x428c72));}
            catch(com.mojang.brigadier.exceptions.CommandSyntaxException ex){throw new IllegalStateException(ex);}
            var wallet=TeamUtil.walletKey(p.getServer(),p.getUUID());
            TeamEconomyMod.get().economy().manager().setBalance(wallet,4235);
            p.getInventory().clearContent();p.inventoryMenu.sendAllDataToRemote();
        }));
        action("frame compact shared board",()->{mc().options.hideGui=false;camera(34,64.5,7,180,-2);});delay(30);
        until("party board ready for filming",TeamBoardHud::ready);
        startClip("09-team");snapshot("release-team");delay(140);endClip();
        action("open polished guide",()->{
            mc().options.hideGui=true;scale(3);vazkii.patchouli.api.PatchouliAPI.get().openBookEntry(
                    com.evolt.teamecon.casino.GuideBookItem.BOOK,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("teamecon","welcome"),2);
        });delay(8);
        startClip("10-guide");snapshot("release-guide");delay(70);
        action("show the actual machine recipe",()->vazkii.patchouli.api.PatchouliAPI.get().openBookEntry(
                com.evolt.teamecon.casino.GuideBookItem.BOOK,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("teamecon","penguin"),3));
        delay(70);endClip();
        action("release closing camera",()->{closeScreen();scale(2);camera(29,65.3,12.5,151,2);});delay(12);
        snapshot("release-closing");startClip("11-outro");delay(160);endClip();
        RESULTS.put("footage", "Minecraft framebuffer in a staged creative world; normal RNG, no forced jackpots");
        RESULTS.put("backdrop", "Quartz wall, stripped spruce pillars and shroomlights; no leaves");
    }
    /** Close, readable README captures in an actual white quartz showroom. */
    private static void planReadme(){
        action("build white quartz gallery",()->{
            closeScreen();mc().options.hideGui=true;mc().options.fov().set(50);scale(3);
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc().getWindow().getWindow(),1600,800);
            server(p->{
                var level=p.serverLevel();
                for(int x=-12;x<=48;x++)for(int z=-10;z<=30;z++){
                    var material=(z==4||z==12||x%5==4)?Blocks.QUARTZ_BRICKS:Blocks.SMOOTH_QUARTZ;
                    level.setBlock(new BlockPos(x,63,z),material.defaultBlockState(),3);
                }
                for(int x=-12;x<=48;x++)for(int y=64;y<=70;y++)
                    level.setBlock(new BlockPos(x,y,-4),Blocks.WHITE_CONCRETE.defaultBlockState(),3);
                for(int i=-2;i<11;i++){
                    int x=i*5-2;
                    for(int y=64;y<=69;y++)level.setBlock(new BlockPos(x,y,-3),Blocks.QUARTZ_PILLAR.defaultBlockState(),3);
                    for(int dx=1;dx<=4;dx++){
                        for(int y=64;y<=67;y++)level.setBlock(new BlockPos(x+dx,y,-3),Blocks.SMOOTH_QUARTZ.defaultBlockState(),3);
                        level.setBlock(new BlockPos(x+dx,68,-3),Blocks.SMOOTH_QUARTZ.defaultBlockState(),3);
                    }
                }
                for(int x=-12;x<=48;x++)for(int z=-4;z<=3;z++)
                    level.setBlock(new BlockPos(x,70,z),(z==2?Blocks.SEA_LANTERN:Blocks.SMOOTH_QUARTZ).defaultBlockState(),3);
                level.setDayTime(5500);level.setWeatherParameters(100000,0,false,false);
                p.getInventory().clearContent();p.getInventory().selected=2;p.inventoryMenu.sendAllDataToRemote();
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(2));
                unlockMachines(p);
            });
        });delay(60);
        action("frame three machines without distant empty space",()->camera(6.5,64,7.4,180,1));delay(80);
        snapshot("readme-hero");
        action("square close-up viewport",()->org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc().getWindow().getWindow(),1000,1000));delay(12);
        action("high-low front controls",()->camera(26,64,4.8,180,1));delay(20);
        action("play high-low with normal game RNG",()->worldClick(5,.6,1.39));delay(45);
        snapshot("readme-hilo");
        action("slime front board",()->camera(16.5,64.1,5.6,180,1));delay(20);
        action("start a real slime hop",()->worldClick(3,.6,1.39));delay(26);
        snapshot("readme-slime");
        action("low roulette close view",()->camera(6.5,64.15,4.1,180,18));delay(24);
        snapshot("readme-roulette");
        action("slot close view",()->camera(1,64,4.8,180,1));delay(20);
        snapshot("readme-slots");

        action("readable UI viewport",()->{
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc().getWindow().getWindow(),1280,960);scale(4);
            camera(32.5,64,4.8,180,1);
        });delay(20);
        action("prepare recycling example",()->server(p->{
            var wallet=TeamUtil.walletKey(p.getServer(),p.getUUID());TeamEconomyMod.get().economy().manager().setBalance(wallet,200);
            p.getInventory().clearContent();
            ItemStack[] basket={new ItemStack(Items.IRON_INGOT,32),new ItemStack(Items.COPPER_INGOT,32),new ItemStack(Items.GOLD_INGOT,8),
                    new ItemStack(Items.COAL,64),new ItemStack(Items.OAK_LOG,64),new ItemStack(Items.WHEAT,64),new ItemStack(Items.COBBLESTONE,64)};
            for(int i=0;i<basket.length;i++)p.getInventory().items.set(9+i,basket[i]);p.inventoryMenu.sendAllDataToRemote();
        }));delay(5);openShop();
        action("show sale tray",()->click("gui.teamecon.shop.tab.sell"));delay(5);
        for(int i=0;i<7;i++){
            final int slot=com.evolt.teamecon.shop.ShopMenu.SALE_SLOTS+i;
            action("deposit recycling stack "+i,()->mc().gameMode.handleInventoryMouseClick(
                    ((ShopScreen)mc().screen).getMenu().containerId,slot,0,ClickType.QUICK_MOVE,mc().player));delay(3);
        }
        delay(15);snapshot("readme-recycling");
        action("close recycling",VisualQa::closeScreen);
        action("set up real shared team",()->server(p->{
            var teams=(dev.ftb.mods.ftbteams.data.TeamManagerImpl)dev.ftb.mods.ftbteams.api.FTBTeamsAPI.api().getManager();
            try{teams.createPartyTeam(p,"白庭小队","",dev.ftb.mods.ftblibrary.icon.Color4I.rgb(0x91b9c9));}
            catch(com.mojang.brigadier.exceptions.CommandSyntaxException ex){throw new IllegalStateException(ex);}
            unlockMachines(p);var m=TeamEconomyMod.get().economy().manager();var wallet=TeamUtil.walletKey(p.getServer(),p.getUUID());
            m.setBalance(wallet,4235);p.getInventory().clearContent();p.inventoryMenu.sendAllDataToRemote();
        }));delay(10);openShop();
        action("show shared wallet and levels",()->click("gui.teamecon.shop.tab.levels"));delay(15);
        snapshot("readme-team");action("close shared shop",VisualQa::closeScreen);
        action("approach blind box",()->camera(35.5,64,4.8,180,1));delay(15);
        action("open real blind box catalog",()->server(p->com.evolt.teamecon.shop.ShopMenu.open(p,SHOP.east(3),false,"boxes")));
        until("readme boxes ready",()->mc().screen instanceof BlindBoxScreen&&!ClientShopCache.boxes().isEmpty());
        action("show rare prize preview",()->click("shop.teamecon.pool.rare"));delay(10);
        snapshot("readme-boxes");action("close box preview",VisualQa::closeScreen);

        action("open real handbook contents",()->{
            scale(4);vazkii.patchouli.api.PatchouliAPI.get().openBookGUI(com.evolt.teamecon.casino.GuideBookItem.BOOK);
        });delay(15);snapshot("readme-guide-contents");
        readmeBook("welcome",0,"readme-guide-start");
        readmeBook("penguin",0,"readme-guide-game");
        readmeBook("penguin",2,"readme-guide-rules");
        readmeBook("penguin",4,"readme-guide-recipe");
        action("close README handbook",VisualQa::closeScreen);
        RESULTS.put("backdrop","White concrete, smooth quartz, quartz pillars/bricks and sea lanterns; no leaves");
        RESULTS.put("capture","Real framebuffer, close camera, normal game RNG; no compositing or replacement backgrounds");
    }
    private static void readmeBook(String entry,int page,String filename){
        action("open handbook "+entry+" page "+page,()->vazkii.patchouli.api.PatchouliAPI.get().openBookEntry(
                com.evolt.teamecon.casino.GuideBookItem.BOOK,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("teamecon",entry),page));
        delay(15);snapshot(filename);
    }
    private static CasinoMachineBlockEntity machine(int index){return (CasinoMachineBlockEntity)mc().level.getBlockEntity(new BlockPos(index*5,65,0));}
    private static double iWidth(int index){return index==1||index==2||index==3?3:2;}
    private static void worldClick(int index,double x,double y){
        var pos=new BlockPos(index*5+(int)Math.floor(x),64+(int)Math.floor(y),0);
        var hit=new BlockHitResult(new Vec3(index*5+x,64+y,1),Direction.SOUTH,pos,false);
        mc().gameMode.useItemOn(mc().player,InteractionHand.MAIN_HAND,hit);
    }
    private static void selectSlot(int slot){server(p->{p.getInventory().selected=slot;p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(slot));});}
    private static void grant(ServerPlayer player,String id){
        var advancement=Objects.requireNonNull(player.getServer().getAdvancements().get(net.minecraft.resources.ResourceLocation.parse(id)));
        for(String criterion:advancement.value().criteria().keySet())player.getAdvancements().award(advancement,criterion);
    }
    private static void openShop(){ openShop(true); }
    private static void openShop(boolean browseItems){
        action("open storefront",()->server(p->{
            p.getInventory().selected=1;p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(1));
            p.level().getBlockState(SHOP).useWithoutItem(p.level(),p,new BlockHitResult(Vec3.atCenterOf(SHOP),Direction.SOUTH,SHOP,false));
        }));
        until("storefront catalogue ready",()->mc().screen instanceof ShopScreen shop&&ClientShopCache.containerId()==shop.getMenu().containerId&&ClientShopCache.items().size()>1000&&ClientCasinoProgression.ready());
        action("vending requests last-page preference",()->{
            var menu=((ShopScreen)mc().screen).getMenu();
            require(menu.initialTab().equals("remember"),"Vending machine forced a page");
        });
        if(browseItems)action("browse vending items",()->click("gui.teamecon.shop.tab.items"));
    }
    private static void openTerminal(){
        action("open wireless terminal",()->mc().gameMode.useItem(mc().player,InteractionHand.MAIN_HAND));
        until("terminal state synchronized",VisualQa::casinoReady);
    }
    private static void leftClickTicket(){
        action("left-click held ticket",()->{
            var input=new net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered(0,mc().options.keyAttack,InteractionHand.MAIN_HAND);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(input);
            require(input.isCanceled()&&!input.shouldSwingHand(),"Scratch click also attacked or broke a block");
        });
    }
    private static void scratchAll(){
        int left=(mc().screen.width-320)/2,top=(mc().screen.height-240)/2;
        for(int y=78;y<=182;y+=7)mc().screen.mouseDragged((left+208)*screenScale(),(top+y)*screenScale(),0,188*screenScale(),0);
    }

    private static boolean casinoReady() {
        return mc().screen instanceof CasinoScreen screen && CasinoScreenData.last() != null
                && CasinoScreenData.last().containerId() == screen.getMenu().containerId;
    }
    private static void tab(String name) { action("select " + name, () -> click("gui.teamecon.tab." + name)); delay(2); }
    private static void play(String key, String game) { action("press " + key, () -> press(key)); response(game); }
    private static void press(String key) { requestBefore = CasinoScreenData.last().requestId(); click(key); }
    private static void response(String game) {
        until("receive " + game + " response", () -> casinoReady() && CasinoScreenData.last().requestId() > requestBefore);
        action("accepted " + game + " action", () -> {
            var state = CasinoScreenData.last();
            require(state.game().equals(game) && !state.messageKey().contains("busy"), "Action rejected: " + state.messageKey());
            RESULTS.put(game + "-" + state.requestId(), state.messageKey() + " " + state.messageArgs() + " / " + state.extra());
        });
    }
    private static void cashOut() {
        action("cash out surviving run", () -> { if (CasinoScreenData.last().stake() > 0) press("gui.teamecon.cash_out"); });
        until("run fully settled", () -> CasinoScreenData.last().stake() == 0); delay(10);
    }
    private static void shopBuy() {
        final long[] revision = {0};
        action("purchase first shop result", () -> {
            revision[0] = ClientShopCache.revision();
            click("gui.teamecon.shop.buy");
        });
        until("shop purchase synchronized", () -> ClientShopCache.revision() > revision[0]); delay(4);
        action("shop purchase accepted", () -> {
            String key = ClientShopCache.messageKey();
            require(Set.of("shop.teamecon.item_received", "shop.teamecon.book_received", "shop.teamecon.box_reward",
                    "shop.teamecon.box_empty","scratch.teamecon.purchased").contains(key), "Shop rejected purchase: " + key);
            RESULTS.put("shop-" + PASSED.size(), key + " " + ClientShopCache.messageArgs());
        });
    }
    private static void click(String translationKey, Object... args) {
        String text = Component.translatable(translationKey,args).getString();
        Button button = mc().screen.children().stream().filter(e -> e instanceof Button b && b.getMessage().getString().equals(text))
                .map(e -> (Button) e).findFirst().orElseThrow(() -> new IllegalStateException("Missing button: " + text));
        require(button.active && button.visible, "Disabled button: " + text);
        double scale=screenScale(),x=(button.getX()+button.getWidth()/2D)*scale,y=(button.getY()+button.getHeight()/2D)*scale;
        require(mc().screen.mouseClicked(x,y,0),"Screen did not accept scaled click: "+text);
        if(mc().screen!=null)mc().screen.mouseReleased(x,y,0);
    }
    private static void edit(String value) {
        mc().screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .findFirst().orElseThrow().setValue(value);
    }
    private static void checkLayout() {
        if (!(mc().screen instanceof AbstractContainerScreen<?> screen)) return;
        if(screen instanceof CompactContainerScreen<?> compact){
            double scale=compact.panelScale(),w=mc().getWindow().getGuiScaledWidth(),h=mc().getWindow().getGuiScaledHeight();
            require(screen.getXSize()*scale<=w*.781&&screen.getYSize()*scale<=h*.781,"Panel fills the viewport");
            require(screen.getGuiLeft()*scale>=w*.10&&screen.getGuiTop()*scale>=h*.10,"Panel has insufficient margin");
            RESULTS.put("layout-"+PASSED.size(),screen.getClass().getSimpleName()+" "+(int)w+"x"+(int)h+" scale="+scale);
        }
        var widgets=screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).filter(w->w.visible).toList();
        for(int i=0;i<widgets.size();i++)for(int j=i+1;j<widgets.size();j++){
            var a=widgets.get(i);var b=widgets.get(j);
            require(a.getX()+a.getWidth()<=b.getX()||b.getX()+b.getWidth()<=a.getX()
                    ||a.getY()+a.getHeight()<=b.getY()||b.getY()+b.getHeight()<=a.getY(),
                    "Widgets overlap: "+a.getMessage().getString()+" / "+b.getMessage().getString());
        }
        for (var child : screen.children()) if (child instanceof AbstractWidget widget && widget.visible) {
            require(widget.getX() >= 0 && widget.getY() >= 0 && widget.getX() + widget.getWidth() <= screen.width
                    && widget.getY() + widget.getHeight() <= screen.height, "Widget outside viewport: " + widget.getMessage().getString());
            int left = screen.getGuiLeft(), top = screen.getGuiTop();
            for (var slot : screen.getMenu().slots) if(slot.isActive())require(widget.getX() + widget.getWidth() <= left + slot.x
                    || widget.getX() >= left + slot.x + 16 || widget.getY() + widget.getHeight() <= top + slot.y
                    || widget.getY() >= top + slot.y + 16, "Widget overlaps inventory: " + widget.getMessage().getString());
        }
    }

    @SubscribeEvent
    public static void rendered(RenderFrameEvent.Post event) {
        if (!finished) {
            try { GameplayCapture.frame(); }
            catch (Throwable ex) { fail(ex); }
        }
        if (pendingCapture == null || finished || mc().getOverlay() != null) return;
        try {
            Files.createDirectories(output());
            try (var image = Screenshot.takeScreenshot(mc().getMainRenderTarget())) {
                image.writeToFile(output().resolve(pendingCapture + ".png"));
            }
            CAPTURES.add(pendingCapture); pendingCapture = null;
        } catch (Throwable ex) { fail(ex); }
    }
    private static void finish() {
        GameplayCapture.stop();
        finished = true; writeReport("passed", "");
        TeamEconomyMod.LOGGER.info("VISUAL_QA_PASSED: {} checks, {} screenshots", PASSED.size(), CAPTURES.size());
        mc().stop();
    }
    private static void fail(Throwable error) {
        if (finished) return;
        finished = true;
        try { GameplayCapture.stop(); } catch (RuntimeException ex) { error.addSuppressed(ex); }
        String detail = (STEPS.isEmpty() ? "startup" : STEPS.getFirst().name()) + ": " + error;
        TeamEconomyMod.LOGGER.error("VISUAL_QA_FAILED: " + detail, error);
        writeReport("failed", detail); mc().stop();
    }
    private static void writeReport(String status, String detail) {
        try {
            Files.createDirectories(output());
            Files.writeString(output().resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                    "status", status, "detail", detail, "checks", PASSED, "screenshots", CAPTURES, "results", RESULTS,
                    "guiWidth", mc().getWindow().getGuiScaledWidth(), "guiHeight", mc().getWindow().getGuiScaledHeight())));
        } catch (Exception ex) { TeamEconomyMod.LOGGER.error("Could not write QA report", ex); }
    }
}
