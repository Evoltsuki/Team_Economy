"""Generate Forge sources from the shared implementation plus explicit platform adapters.

Generated files stay under build/. Never edit them: change shared sources, these
mechanical API translations, or versions/forge/src instead. Version stays in gradle.properties.
"""
from pathlib import Path
import argparse
import json
import re
import shutil
import gzip
import struct

ROOT = Path(__file__).resolve().parents[1]
COMPAT = 'com.evolt.teamecon.forge'

def remove_method(s, marker):
    start = s.index(marker)
    end = s.index('{', start)
    depth = 1
    i = end + 1
    while depth:
        depth += (s[i] == '{') - (s[i] == '}')
        i += 1
    return s[:start] + s[i:]

def translate(s, name, mc):
    old = mc == '1.20.1'
    s = s.replace('net.neoforged.fml.common.EventBusSubscriber', 'net.minecraftforge.fml.common.Mod.EventBusSubscriber')
    s = s.replace('net.neoforged.neoforge', 'net.minecraftforge').replace('net.neoforged.', 'net.minecraftforge.')
    s = s.replace('net.minecraftforge.bus.api', 'net.minecraftforge.eventbus.api')
    s = s.replace('net.minecraftforge.common.NeoForge', 'net.minecraftforge.common.MinecraftForge').replace('NeoForge.EVENT_BUS', 'MinecraftForge.EVENT_BUS')
    s = s.replace('ModConfigSpec', 'ForgeConfigSpec')
    for typ in ('DeferredRegister','DeferredHolder','DeferredItem','DeferredBlock'):
        s = s.replace('net.minecraftforge.registries.'+typ, COMPAT+'.'+typ)
    s = s.replace('net.minecraftforge.common.extensions.IMenuTypeExtension', 'net.minecraftforge.common.extensions.IForgeMenuType')
    s = s.replace('net.minecraft.network.RegistryFriendlyByteBuf', 'net.minecraft.network.FriendlyByteBuf').replace('RegistryFriendlyByteBuf','FriendlyByteBuf')
    s = s.replace('net.minecraft.network.protocol.common.custom.CustomPacketPayload', COMPAT+'.CustomPacketPayload')
    s = s.replace('net.minecraft.network.codec.StreamCodec', COMPAT+'.StreamCodec')
    s = s.replace('net.minecraftforge.network.PacketDistributor', COMPAT+'.ForgeNetwork')
    s = s.replace('PacketDistributor.sendToPlayer', 'ForgeNetwork.sendToPlayer').replace('PacketDistributor.sendToAllPlayers', 'ForgeNetwork.sendToAllPlayers')
    s = s.replace('net.minecraftforge.network.event.RegisterPayloadHandlersEvent', COMPAT+'.PayloadRegistrar')
    s = s.replace('RegisterPayloadHandlersEvent', 'PayloadRegistrar')
    s = s.replace('net.minecraftforge.network.handling.IPayloadContext', COMPAT+'.PayloadContext').replace('IPayloadContext','PayloadContext')
    s = re.sub(r'@SubscribeEvent\s+public static void register\(PayloadRegistrar', 'public static void register(PayloadRegistrar', s)
    if '/payloads/' in name:
        cls = Path(name).stem
        s = re.sub(r'new Type<>\((ResourceLocation\.[^;]+)\);', r'new Type<>(\1, '+cls+'.class);', s)
    if name.endswith('ClientPayloadSender.java'):
        s = s.replace('import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;','')
        s = s.replace('minecraft.getConnection().send(new ServerboundCustomPayloadPacket(payload));', COMPAT+'.ForgeNetwork.sendToServer(payload);')
    s = s.replace('net.minecraftforge.event.tick.ServerTickEvent', 'net.minecraftforge.event.TickEvent')
    s = s.replace('ServerTickEvent.Post event)', 'TickEvent.ServerTickEvent event)')
    s = re.sub(r'(TickEvent.ServerTickEvent event\)\s*\{)', r'\1\n        if(event.phase != TickEvent.Phase.END) return;', s)
    s=s.replace('net.minecraftforge.client.event.ClientTickEvent','net.minecraftforge.event.TickEvent').replace('net.minecraftforge.client.event.RenderFrameEvent','net.minecraftforge.event.TickEvent')
    s=s.replace('ClientTickEvent.Post event)', 'TickEvent.ClientTickEvent event)').replace('RenderFrameEvent.Post event)', 'TickEvent.RenderTickEvent event)')
    s=re.sub(r'(TickEvent.(?:ClientTickEvent|RenderTickEvent) event\)\s*\{)',r'\1\n        if(event.phase != TickEvent.Phase.END) return;',s)
    s = s.replace('AdvancementEvent.AdvancementProgressEvent', 'AdvancementEvent.AdvancementEarnEvent')
    if name.endswith('ItemPriceLabelHandler.java'):
        s=s.replace('''        if (!(event.getContainerScreen() instanceof AbstractContainerScreen<?> screen)) {
            return;
        }''','''        AbstractContainerScreen<?> screen = event.getContainerScreen();''')
    if old:
        s = s.replace('RenderGuiEvent','RenderGuiOverlayEvent')
        s = re.sub(r'(RenderGuiOverlayEvent.Post event\)\s*\{)', r'\1\n        if (!event.getOverlay().id().getPath().equals("hotbar")) return;', s)
    elif name.endswith(('MachineHud.java','TeamBoardHud.java')):
        s=s.replace('import net.minecraftforge.client.event.RenderGuiEvent;', '')
        s=s.replace('@SubscribeEvent public static void', 'public static void').replace('RenderGuiEvent.Post event','net.minecraft.client.gui.GuiGraphics graphics')
        s=s.replace('event.getGuiGraphics()', 'graphics')
    if name.endswith('ShopMachineRenderer.java'):
        s=remove_method(s,'    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox')
    if name.endswith('ShopMachineBlockEntity.java'):
        i=s.rfind('}')
        s=s[:i]+'''    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(getBlockPos()).expandTowards(0,1,0).inflate(.15);
    }
'''+s[i:]
    if name.endswith('TeamEconomyMod.java'):
        s = s.replace('        modEventBus.addListener(ModNetwork::register);', '').replace('        modEventBus.addListener(com.evolt.teamecon.network.ShopNetwork::register);', '')
        s = s.replace('public TeamEconomyMod(IEventBus modEventBus, ModContainer modContainer) {', '''public TeamEconomyMod() {
        IEventBus modEventBus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();''')
        s = s.replace('modContainer.registerConfig(', 'net.minecraftforge.fml.ModLoadingContext.get().registerConfig(')
        s = s.replace('modEventBus.addListener(ModConfigEvent.Loading.class, this::onConfigLoad);','modEventBus.addListener(this::onConfigLoad);')
        s = s.replace('modEventBus.addListener(ModConfigEvent.Reloading.class, this::onConfigReload);','modEventBus.addListener(this::onConfigReload);')
        s = s.replace('ModRegistries.register(modEventBus);', 'ModRegistries.register(modEventBus);\n        '+COMPAT+'.ForgeNetwork.init();')
    # Forge menu opening must use NetworkHooks so the extra data reaches the client.
    s = re.sub(r'\b(player|p)\.openMenu\(', COMPAT+r'.Menus.open(\1,', s)
    if name.endswith('ClientModEvents.java'):
        s = s.replace('import net.minecraftforge.client.event.RegisterMenuScreensEvent;', '')
        s = s.replace('value = Dist.CLIENT)', 'value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)')
        s = s.replace('event.enqueueWork(()->{', 'event.enqueueWork(()->{\n            registerScreens();', 1)
        if not old: s=s.replace('registerScreens();', 'registerScreens();\n            '+COMPAT+'.HudLayer.register();',1)
        s = s.replace('@SubscribeEvent\n    public static void registerScreens(RegisterMenuScreensEvent event)', 'public static void registerScreens()')
        s = s.replace('event.register(ModRegistries.', 'net.minecraft.client.gui.screens.MenuScreens.register(ModRegistries.')
        s = s.replace('event.<com.evolt.teamecon.shop.ShopMenu,', 'net.minecraft.client.gui.screens.MenuScreens.<com.evolt.teamecon.shop.ShopMenu,')
    if old:
        s=s.replace('"allay", "armadillo",', '"allay",').replace('"minecraft:sweeping_edge"','"minecraft:sweeping"')
        s = s.replace('Math.clamp(', COMPAT+'.CompatMath.clamp(').replace('.getFirst()', '.get(0)')
        s = s.replace('plan.getLast()', 'plan.get(plan.size()-1)').replace('isSameItemSameComponents(', 'isSameItemSameTags(')
        s = s.replace('ResourceLocation.fromNamespaceAndPath(', 'new ResourceLocation(').replace('ResourceLocation.parse(', 'new ResourceLocation(')
        s = s.replace('net.minecraft.core.HolderLookup.Provider', 'net.minecraft.core.RegistryAccess').replace('HolderLookup.Provider', 'net.minecraft.core.RegistryAccess')
        s = s.replace('TooltipContext context', 'net.minecraft.world.level.Level context')
        s = s.replace('getAdvancements().get(', 'getAdvancements().getAdvancement(')
        s=s.replace('advancement.value().criteria()', 'advancement.getCriteria()')
        s=s.replace('.value().createWorldDimensions(), new TitleScreen())', '.value().createWorldDimensions())')
        s = s.replace('net.minecraft.world.ItemInteractionResult', COMPAT+'.UnusedItemInteractionResult').replace('ItemInteractionResult', 'UnusedItemInteractionResult')
        s = s.replace('UnusedUnused', 'Unused')
        # The 1.20.1 block use method handles both empty and occupied hands.
        match=re.search(r'\s*@Override\s+(?:public|protected) UnusedItemInteractionResult useItemOn',s)
        if match: s=remove_method(s,match.group())
        s = s.replace('import '+COMPAT+'.UnusedItemInteractionResult;', '')
        s = re.sub(r'useWithoutItem\(BlockState state,\s*Level level,\s*BlockPos pos,\s*Player player,\s*BlockHitResult hit\)', 'use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)', s)
        s = s.replace('protected InteractionResult use(', 'public InteractionResult use(')
        s = s.replace('useWithoutItem(state,level,pos,player,hit)', 'use(state,level,pos,player,net.minecraft.world.InteractionHand.MAIN_HAND,hit)')
        # Block codecs arrived after 1.20.1; registered instances retain their identity.
        s = re.sub(r'\s*@Override\s+protected MapCodec<[^\n]+?codec\(\)\s*\{[^}]+}', '', s)
        s = re.sub(r'\s*public static final MapCodec<.*?;', '', s, flags=re.S) if 'public static final MapCodec<' in s else s
        for method in ('updateShape','getShape','onRemove','getCollisionShape','getRenderShape','rotate','mirror'):
            s = re.sub(r'protected ([^\n]+\b'+method+r'\()', r'public \1', s)
        if 'playerWillDestroy' in s:
            s=s.replace('public BlockState playerWillDestroy','public void playerWillDestroy').replace('return super.playerWillDestroy(level,pos,state,player);','super.playerWillDestroy(level,pos,state,player); return;').replace('        return state;\n    }\n\n    @Override\n    public boolean propagates', '    }\n\n    @Override\n    public boolean propagates')
        if name.endswith('TeamEconomyManager.java'):
            s=s.replace('new SavedData.Factory<>(TeamEconomyManager::new, TeamEconomyManager::load, null),', 'TeamEconomyManager::load, TeamEconomyManager::new,')
            s=s.replace('load(CompoundTag tag, net.minecraft.core.RegistryAccess provider)', 'load(CompoundTag tag)').replace('save(CompoundTag tag, net.minecraft.core.RegistryAccess provider)', 'save(CompoundTag tag)')
        if name.endswith(('CasinoMachineBlockEntity.java','ShopMachineBlockEntity.java')):
            s=s.replace('CompoundTag tag, net.minecraft.core.RegistryAccess provider', 'CompoundTag tag')
            s=s.replace('loadAdditional(CompoundTag tag)', 'load(CompoundTag tag)').replace('super.loadAdditional(tag, provider)', 'super.load(tag)')
            s=s.replace('protected void load(', 'public void load(')
            s=s.replace('super.saveAdditional(tag, provider)', 'super.saveAdditional(tag)')
            s=s.replace('getUpdateTag(net.minecraft.core.RegistryAccess provider)', 'getUpdateTag()').replace('saveAdditional(tag, provider)', 'saveAdditional(tag)')
        if name.endswith('ScratchCardItem.java'):
            s=s.replace('import net.minecraft.core.component.DataComponents;', '').replace('import net.minecraft.world.item.component.CustomData;', '')
            s=s.replace('stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag()', 'stack.getOrCreateTag()').replace('stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag))', 'stack.setTag(tag)')
        if name.endswith('RecipePricer.java'):
            s=s.replace('import net.minecraft.world.item.crafting.RecipeHolder;', '')
            s=s.replace('for (RecipeHolder<?> holder : recipeManager.getRecipes()) {\n            Recipe<?> recipe = holder.value();','for (Recipe<?> recipe : recipeManager.getRecipes()) {').replace('holder.id().toString()', 'recipe.getId().toString()')
        if name.endswith('ShopService.java'):
            s=s.replace('import net.minecraft.core.component.DataComponents;', '').replace('import net.minecraft.world.item.enchantment.ItemEnchantments;', '')
            s=s.replace('''        ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        stored.upgrade(enchantment, level);
        book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());''','''        net.minecraft.world.item.EnchantedBookItem.addEnchantment(book,
                new net.minecraft.world.item.enchantment.EnchantmentInstance(enchantment.value(),level));''')
            s=s.replace('server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)\n                .get(ResourceKey.create(Registries.ENCHANTMENT, id))', 'net.minecraft.core.registries.BuiltInRegistries.ENCHANTMENT.getHolder(ResourceKey.create(Registries.ENCHANTMENT, id))')
        if name.endswith('ShopScreen.java'):
            s=s.replace('import net.minecraft.core.component.DataComponents;', '').replace('stack.has(DataComponents.FOOD)', 'stack.isEdible()')
            s=s.replace('||item instanceof MaceItem','')
        if name.endswith('SlimeHopVisuals.java'):
            s=s.replace('light,OverlayTexture.NO_OVERLAY);','light,OverlayTexture.NO_OVERLAY,1F,1F,1F,1F);')
        if name.endswith('MachineRenderUtil.java'):
            s=s.replace('v.addVertex(', 'v.vertex(').replace('.setColor(', '.color(').replace('.setUv(', '.uv(').replace('.setOverlay(', '.overlayCoords(').replace('.setLight(', '.uv2(').replace('.setNormal(p.last(),nx,ny,nz);', '.normal(p.last().normal(),nx,ny,nz).endVertex();')
        if name.endswith('CompactContainerScreen.java'):
            s=remove_method(s,'            @Override public boolean containsPointInScissor')
            s=s.replace('scaled.pose().mulPose(graphics.pose().last().pose());','scaled.pose().mulPoseMatrix(graphics.pose().last().pose());')
            s=s.replace('mouseScrolled(double x, double y, double dx, double dy)', 'mouseScrolled(double x, double y, double dy)').replace('return scrollPanel(x / panelScale, y / panelScale, dx, dy);', 'return scrollPanel(x / panelScale, y / panelScale, 0, dy);').replace('super.mouseScrolled(x, y, dx, dy)', 'super.mouseScrolled(x, y, dy)')
    return s

def generate(mc,qa=False):
    dest = ROOT / 'build' / ('forge-'+mc) / 'generated'
    # Delete only generated outputs, after checking the resolved target is in build/.
    if not dest.resolve().is_relative_to((ROOT/'build').resolve()): raise ValueError(dest)
    if dest.exists(): shutil.rmtree(dest)
    for p in (ROOT/'src/main/java').rglob('*.java'):
        rel=p.relative_to(ROOT/'src/main/java')
        if 'gametest' in rel.parts and p.name not in ('ConcurrentMachinesTest.java', 'AdminUnlockTest.java', 'ServerConfigurationTest.java', 'MarketFeedbackTest.java'): continue
        source=p.read_text(encoding='utf-8')
        if p.name == 'ServerConfigurationTest.java' and mc == '1.21.1':
            # Forge 52 has no FakePlayerFactory; use the existing inert connection fixture.
            source = source.replace('import net.neoforged.neoforge.common.util.FakePlayerFactory;', '')
            source = re.sub(r'FakePlayerFactory.get\(h.getLevel\(\), new GameProfile\(UUID.randomUUID\(\), ("[^"]+")\)\)',
                            r'PortSmokeTest.player(h, \1)', source)
        if p.name in ('ConcurrentMachinesTest.java', 'AdminUnlockTest.java', 'MarketFeedbackTest.java'):
            source=source.replace('ProgressionTest.player(', 'PortSmokeTest.player(')
            if mc=='1.20.1': source=source.replace(',h.getLevel().registryAccess()', '')
        if p.name == 'MarketFeedbackTest.java' and mc == '1.20.1':
            source=source.replace('.useWithoutItem(h.getLevel(),p,new BlockHitResult(',
                                  '.use(h.getLevel(),p,net.minecraft.world.InteractionHand.MAIN_HAND,new BlockHitResult(')
        out=dest/'java'/rel;out.parent.mkdir(parents=True,exist_ok=True)
        out.write_text(translate(source, rel.as_posix(), mc),encoding='utf-8')
    for layer in ('common', mc):
        src=ROOT/'versions/forge/src'/layer
        if src.exists(): shutil.copytree(src,dest,dirs_exist_ok=True)
    if qa:
        for p in (ROOT/'src/portqa/java').rglob('*.java'):
            rel=p.relative_to(ROOT/'src/portqa/java');out=dest/'java'/rel
            out.parent.mkdir(parents=True,exist_ok=True)
            out.write_text(translate(p.read_text(encoding='utf-8'),rel.as_posix(),mc),encoding='utf-8')
    shutil.copytree(ROOT/'src/main/resources',dest/'resources',dirs_exist_ok=True)
    resources=dest/'resources'
    if mc=='1.20.1':
        for path in list((resources/'data').rglob('*')):
            if path.is_dir() and path.name in ('recipe','loot_table','advancement','item','block'):
                if path.name in ('item','block') and path.parent.name!='tags': continue
                target=path.with_name(path.name+'s')
                if not path.resolve().is_relative_to(dest.resolve()): raise ValueError(path)
                shutil.copytree(path,target,dirs_exist_ok=True)
                shutil.rmtree(path)
        for path in (resources/'data').rglob('*.json'):
            data=json.loads(path.read_text(encoding='utf-8'))
            if 'recipes' in path.parts and isinstance(data.get('result'),dict) and 'id' in data['result']:
                data['result']['item']=data['result'].pop('id')
            def legacy_predicates(node):
                if isinstance(node,dict):
                    for key,value in node.items():
                        if key=='items' and isinstance(value,str): node[key]=[value]
                        else: legacy_predicates(value)
                elif isinstance(node,list):
                    for value in node: legacy_predicates(value)
            legacy_predicates(data)
            path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    pack=resources/'pack.mcmeta'
    data={'pack': {'description': 'Team Economy resources', 'pack_format': 15 if mc=='1.20.1' else 34}}
    pack.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    version=next(x.split('=',1)[1] for x in (ROOT/'gradle.properties').read_text(encoding='utf-8').splitlines() if x.startswith('mod_version='))
    license_id=next(x.split('=',1)[1].strip() for x in (ROOT/'gradle.properties').read_text(encoding='utf-8').splitlines() if x.startswith('mod_license='))
    major='47' if mc=='1.20.1' else '52'
    metadata=f'''modLoader="javafml"
loaderVersion="[{major},)"
license="{license_id}"
[[mods]]
modId="teamecon"
version="{version}"
displayName="Team Economy"
authors="洁柔厨"
logoFile="teamecon-logo.png"
description="Team wallets, recycling, shops and six physical game machines."
[[dependencies.teamecon]]
modId="forge"
mandatory=true
versionRange="[{major},)"
ordering="NONE"
side="BOTH"
[[dependencies.teamecon]]
modId="minecraft"
mandatory=true
versionRange="[{mc}]"
ordering="NONE"
side="BOTH"
[[dependencies.teamecon]]
modId="ftbteams"
mandatory=false
versionRange="*"
ordering="AFTER"
side="BOTH"
[[dependencies.teamecon]]
modId="patchouli"
mandatory=false
versionRange="*"
ordering="AFTER"
side="BOTH"
'''
    (resources/'META-INF').mkdir(exist_ok=True)
    (resources/'META-INF/mods.toml').write_text(metadata,encoding='utf-8')
    # Use packaged binary structures on both Forge versions. Forge 47 resolves
    # the namespace separately and prefixes class names unless disabled;
    # Forge 52 accepts a fully qualified template directly.
    for path in (dest/'java/com/evolt/teamecon/gametest').glob('*.java'):
        source = path.read_text(encoding='utf-8')
        if mc == '1.20.1':
            source = source.replace('@GameTestHolder("teamecon")',
                                    '@GameTestHolder("teamecon")\n@net.minecraftforge.gametest.PrefixGameTestTemplate(false)')
            source = source.replace('template="empty"', 'template="qa_empty"')
        else:
            source = source.replace('template="empty"', 'template="teamecon:qa_empty"')
        path.write_text(source, encoding='utf-8')
    def utf(text):
        data=text.encode();return struct.pack('>H',len(data))+data
    def tag(kind,name,value): return bytes([kind])+utf(name)+value
    compound=(tag(9,'size',bytes([3])+struct.pack('>iiii',3,42,8,16))+
              tag(9,'palette',bytes([10])+struct.pack('>i',1)+tag(8,'Name',utf('minecraft:air'))+b'\0')+
              tag(9,'blocks',bytes([10])+struct.pack('>i',0))+
              tag(9,'entities',bytes([10])+struct.pack('>i',0))+
              tag(3,'DataVersion',struct.pack('>i',3465 if mc=='1.20.1' else 3955))+b'\0')
    template=resources/'data/teamecon'/('structures' if mc=='1.20.1' else 'structure')/'qa_empty.nbt'
    template.parent.mkdir(parents=True,exist_ok=True);template.write_bytes(gzip.compress(bytes([10])+utf('')+compound,mtime=0))
    print(f'Generated Forge {mc} sources and resources')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--mc',choices=['1.20.1','1.21.1'],required=True)
    parser.add_argument('--qa',action='store_true');args=parser.parse_args()
    generate(args.mc,args.qa)
