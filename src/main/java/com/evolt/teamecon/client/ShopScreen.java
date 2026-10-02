package com.evolt.teamecon.client;

import com.evolt.teamecon.gambling.CasinoProgression;
import com.evolt.teamecon.init.ModRegistries;
import com.evolt.teamecon.network.ModNetwork;
import com.evolt.teamecon.network.payloads.ShopActionPayload;
import com.evolt.teamecon.scratch.ScratchKind;
import com.evolt.teamecon.shop.PurchaseRules;
import com.evolt.teamecon.shop.ShopMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Dense vanilla catalogue, visible type categories and an explicit batch-sale deposit. */
public class ShopScreen extends CompactContainerScreen<ShopMenu> {
    private static final int GOLD=0xFFF0CC7D, WHITE=0xFFEDF1EB, DULL=0xFF97ABA8, GREEN=0xFF8AE0B3, RED=0xFFEFA58B, LINE=0xFF354B50;
    private enum Tab { ITEMS, SELL, MACHINES, CARDS, LEVELS }
    private enum Category { ALL, BLOCKS, TOOLS, WEAPONS, ARMOR, FOOD, MAGIC, MATERIALS }
    private record Row(Component name, ItemStack icon, String id, long price, ShopActionPayload.Kind kind,
                       boolean unlocked, String lockReason, String mod, Category category, SearchText searchText) {}
    private record Filter(String id, Component label) {}
    private Tab tab=Tab.ITEMS;
    private Category category=Category.ALL;
    private String mod="", query="", selectedId="", filterMode="", filterQuery="";
    private boolean unlockedOnly;
    private int page, amount=1, waiting, selectedLevel=2, columns, rows, detailX, detailWidth, gridTop;
    private long requestSequence, seenRevision=-1, seenProgression=-1;
    private EditBox search, ticketFactor;
    private Button previous, next, buy;
    private final List<Button> tiles=new ArrayList<>(), quantities=new ArrayList<>();
    private List<Row> catalogue=List.of(), filtered=List.of();
    private List<Filter> filters=List.of();
    private final Map<String,SearchText> searchKeys=new HashMap<>();
    private Row selected;

    public ShopScreen(ShopMenu menu, Inventory inventory, Component title) {
        super(menu,inventory,title);
        String initial = menu.initialTab().equals("remember") ? ShopTabPreferences.last(
                net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath().resolve("config"), inventory.player.getUUID()) : menu.initialTab();
        try { tab = Tab.valueOf(initial.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ignored) { }
    }
    private boolean ready() { return ClientShopCache.containerId()==menu.containerId && ClientCasinoProgression.ready(); }
    public int pageCapacity() { return columns*rows; }

    @Override protected void init() {
        imageWidth=440; imageHeight=280;
        super.init();
        detailWidth=imageWidth>=440?132:96; detailX=imageWidth-detailWidth-8;
        gridTop=tab==Tab.ITEMS?101:81;
        columns=Math.max(1,(detailX-16)/26); rows=Math.max(1,(imageHeight-gridTop-36)/26);
        tiles.clear(); quantities.clear(); previous=next=buy=null; search=ticketFactor=null;
        menu.showSales(tab==Tab.SELL);
        Tab[] shownTabs=Tab.values();
        int tabWidth=(imageWidth-16)/shownTabs.length;
        for(int i=0;i<shownTabs.length;i++) {
            Tab value=shownTabs[i];
            addRenderableWidget(new StoreButton(8+i*tabWidth,35,tabWidth-2,18,tr("tab."+value.name().toLowerCase(Locale.ROOT)),()->{
                tab=value; amount=1; page=0; selectedId=""; query=""; filterMode=""; mod=""; category=Category.ALL;
                if (menu.initialTab().equals("remember")) ShopTabPreferences.remember(minecraft.gameDirectory.toPath().resolve("config"),
                        minecraft.player.getUUID(), value.name().toLowerCase(Locale.ROOT));
                selectedLevel=Math.min(5,ClientCasinoProgression.level()+1); rebuildWidgets();
            },()->tab==value));
        }
        if(menu.isRemote())addRenderableWidget(new StoreButton(imageWidth/2-40,10,80,16,tr("terminal_home"),
                ()->send(ShopActionPayload.Kind.OPEN_TERMINAL,"",1),()->false));
        if(tab==Tab.SELL) {
            buy=addRenderableWidget(new StoreButton(188,158,Math.min(120,imageWidth-198),20,tr("sell_stack"),
                    ()->send(ShopActionPayload.Kind.SELL_STACK,"",1),()->true));
            buy.setTooltip(Tooltip.create(tr("sell_hint")));
        } else if(tab==Tab.LEVELS) {
            for(int i=1;i<=5;i++) tiles.add(addRenderableWidget(new LevelButton(i)));
            buy=addRenderableWidget(new StoreButton(detailX+6,imageHeight-48,detailWidth-12,20,tr("upgrade"),
                    ()->send(ShopActionPayload.Kind.UPGRADE_LEVEL,"",selectedLevel),()->true));
        } else {
            search=new EditBox(font,leftPos+10,topPos+60,detailX-76,16,tr("search"));
            search.setMaxLength(80); search.setHint(tr(filterMode.isEmpty()?"search":"filter_search"));
            search.setValue(filterMode.isEmpty()?query:filterQuery);
            search.setResponder(value->{if(filterMode.isEmpty())query=value;else filterQuery=value;page=0;rebuildRows();});
            addRenderableWidget(search);
            addRenderableWidget(new StoreButton(detailX-60,59,52,18,tr("available"),()->{
                unlockedOnly=!unlockedOnly;page=0;rebuildRows();
            },()->unlockedOnly));
            previous=addRenderableWidget(new StoreButton(8,imageHeight-35,22,16,Component.literal("‹"),()->{page--;rebuildRows();},()->false));
            next=addRenderableWidget(new StoreButton(detailX-30,imageHeight-35,22,16,Component.literal("›"),()->{page++;rebuildRows();},()->false));
            if(tab==Tab.ITEMS){
                for(int i=0;i<Category.values().length;i++)addRenderableWidget(new CategoryButton(Category.values()[i],i));
            }
            if(tab==Tab.CARDS){
                ticketFactor=new EditBox(font,leftPos+detailX+8,topPos+imageHeight-69,detailWidth-58,15,tr("factor_input"));
                ticketFactor.setMaxLength(5);ticketFactor.setFilter(v->v.matches("[0-9]*"));ticketFactor.setValue(String.valueOf(amount));
                ticketFactor.setResponder(v->{try{amount=v.isEmpty()?0:Integer.parseInt(v);}catch(NumberFormatException e){amount=0;}refreshButtons();});
                addRenderableWidget(ticketFactor);
                quantities.add(addRenderableWidget(new StoreButton(detailX+detailWidth-45,imageHeight-71,39,18,Component.literal("×10"),()->{
                    if(selected==null)return;
                    long cap=CasinoProgression.cardStakeLimit(ClientCasinoProgression.level())/selected.price;
                    amount=amount<1||amount*10>Math.min(10000,cap)?1:amount*10;
                    ticketFactor.setValue(String.valueOf(amount));refreshButtons();
                },()->false)));
            }else for(int i=0;i<3;i++) {
                int count=new int[]{1,16,64}[i],w=(detailWidth-14)/3;
                quantities.add(addRenderableWidget(new StoreButton(detailX+6+i*(w+1),imageHeight-71,w,17,Component.literal("×"+count),
                        ()->{amount=count;refreshButtons();},()->amount==count)));
            }
            buy=addRenderableWidget(new StoreButton(detailX+6,imageHeight-48,detailWidth-12,20,tr("buy"),()->{
                if(selected!=null)send(selected.kind,selected.id,quantity(selected));
            },()->true));
            buildCatalogue();rebuildRows();
        }
        refreshButtons();
    }
    private void toggleFilter(String mode) { filterMode=filterMode.equals(mode)?"":mode;filterQuery="";page=0;rebuildWidgets(); }
    private static boolean machine(String id) {return !CasinoProgression.gameForItem(id).isEmpty()||id.equals("teamecon:terminal")||id.equals("teamecon:shop_machine")||id.equals("teamecon:blind_box_machine");}
    private static Category category(ItemStack stack) {
        Item item=stack.getItem();
        if(stack.has(DataComponents.FOOD))return Category.FOOD;
        if(item instanceof ArmorItem)return Category.ARMOR;
        if(item instanceof SwordItem||item instanceof ProjectileWeaponItem||item instanceof TridentItem||item instanceof MaceItem)return Category.WEAPONS;
        if(item instanceof DiggerItem||item instanceof ShearsItem||item instanceof FishingRodItem||item instanceof FlintAndSteelItem)return Category.TOOLS;
        if(item instanceof PotionItem||item instanceof EnchantedBookItem||item==Items.EXPERIENCE_BOTTLE)return Category.MAGIC;
        return item instanceof BlockItem?Category.BLOCKS:Category.MATERIALS;
    }
    private static String modName(String id) {return net.neoforged.fml.ModList.get().getModContainerById(id).map(c->c.getModInfo().getDisplayName()).orElse(id);}
    private Row row(Component name,ItemStack icon,String id,long price,ShopActionPayload.Kind kind,boolean open,String reason,String namespace) {
        String label=name.getString();
        return new Row(name,icon,id,price,kind,open,reason,namespace,category(icon),
                searchKeys.computeIfAbsent(label+"\n"+id,key->SearchText.of(label,id)));
    }
    private void buildCatalogue() {
        List<Row> all=new ArrayList<>();
        if(ready())switch(tab) {
            case ITEMS,MACHINES->{
                for(var e:ClientShopCache.items()) {
                    if(e.itemKey().equals("minecraft:enchanted_book"))continue;
                    if(machine(e.itemKey())!=(tab==Tab.MACHINES))continue;
                    ResourceLocation id=ResourceLocation.tryParse(e.itemKey());if(id==null||!BuiltInRegistries.ITEM.containsKey(id))continue;
                    ItemStack icon=new ItemStack(BuiltInRegistries.ITEM.get(id));
                    all.add(row(icon.getHoverName(),icon,e.itemKey(),e.unitPrice(),ShopActionPayload.Kind.BUY_ITEM,e.unlocked(),e.lockReason(),id.getNamespace()));
                }
                all.sort(Comparator.comparingInt((Row r)->r.unlocked?0:1).thenComparing(Row::mod).thenComparing(Row::id));
                if(tab==Tab.MACHINES)all.sort(Comparator.comparingLong(Row::price));
            }
            case CARDS->{
                for(ScratchKind card:ScratchKind.values()) {
                    boolean open=ClientCasinoProgression.canBuy(card);
                    String reason=!ClientCasinoProgression.valid()?"casino_config":ClientCasinoProgression.level()<ClientCasinoProgression.rules().cardLevel(card)
                            ?"casino_level:"+ClientCasinoProgression.rules().cardLevel(card):"game_disabled";
                    ItemStack icon=new ItemStack(ModRegistries.SCRATCH_CARDS.get(card).get());
                    all.add(row(icon.getHoverName(),icon,card.id(),card.price(),ShopActionPayload.Kind.BUY_TICKET,open,open?"":reason,"teamecon"));
                }
            }
            default->{}
        }
        if(ready() && tab==Tab.ITEMS) {
                for(var e:ClientShopCache.enchants()) {
                    ResourceLocation id=ResourceLocation.tryParse(e.enchantmentId());if(id==null)continue;
                    Component name=Component.translatable("enchantment."+id.getNamespace()+"."+id.getPath()).append(" ").append(Component.translatable("enchantment.level."+e.level()));
                    all.add(row(name,new ItemStack(Items.ENCHANTED_BOOK),e.offerId(),e.price(),ShopActionPayload.Kind.ENCHANT,e.unlocked(),e.lockReason(),id.getNamespace()));
                }
        }
        catalogue=List.copyOf(all);
    }
    private void rebuildRows() {
        if(tab==Tab.LEVELS||tab==Tab.SELL)return;
        for(Button tile:tiles)removeWidget(tile);tiles.clear();
        if(!filterMode.isEmpty()) {
            List<Filter> options=new ArrayList<>();
            if(filterMode.equals("mod")) {
                options.add(new Filter("",tr("all_mods")));
                catalogue.stream().map(Row::mod).distinct().sorted().forEach(id->options.add(new Filter(id,Component.literal(modName(id)+" ("+id+")"))));
            }else for(Category c:Category.values())options.add(new Filter(c.name(),tr("category."+c.name().toLowerCase(Locale.ROOT))));
            String needle=filterQuery.toLowerCase(Locale.ROOT);
            filters=options.stream().filter(f->SearchText.of(f.label.getString(),f.id).matches(needle)).toList();
            int capacity=filterCapacity();page=Math.clamp(page,0,Math.max(0,(filters.size()-1)/capacity));
            for(int i=page*capacity;i<Math.min(filters.size(),(page+1)*capacity);i++) {
                Filter f=filters.get(i);
                tiles.add(addRenderableWidget(new StoreButton(8,gridTop+i%capacity*20,detailX-16,18,f.label,()->{
                    if(filterMode.equals("mod"))mod=f.id;else category=Category.valueOf(f.id);
                    filterMode="";page=0;selectedId="";rebuildWidgets();
                },()->false)));
            }
        }else {
            String needle=query.toLowerCase(Locale.ROOT).trim();
            filtered=catalogue.stream().filter(r->(!unlockedOnly||r.unlocked)&&(mod.isEmpty()||r.mod.equals(mod))
                    &&(category==Category.ALL||r.category==category)&&matches(r,needle)).toList();
            int capacity=pageCapacity();page=Math.clamp(page,0,Math.max(0,(filtered.size()-1)/capacity));
            int start=page*capacity,end=Math.min(start+capacity,filtered.size());
            selected=filtered.subList(start,end).stream().filter(r->r.id.equals(selectedId)).findFirst().orElse(start<end?filtered.get(start):null);
            selectedId=selected==null?"":selected.id;
            for(int i=start;i<end;i++)tiles.add(addRenderableWidget(new ProductButton(filtered.get(i),i-start)));
        }
        refreshButtons();
    }
    private boolean matches(Row r,String needle) {
        if(needle.startsWith("@"))return r.mod.contains(needle.substring(1))||modName(r.mod).toLowerCase(Locale.ROOT).contains(needle.substring(1));
        if(needle.startsWith("#"))return r.icon.getTags().anyMatch(t->t.location().toString().contains(needle.substring(1)));
        return r.searchText.matches(needle);
    }
    private int filterCapacity(){return Math.max(1,(imageHeight-gridTop-36)/20);}
    private int quantity(Row r){return r.kind==ShopActionPayload.Kind.BUY_TICKET||r.kind==ShopActionPayload.Kind.BUY_ITEM&&tab==Tab.ITEMS?amount:1;}
    private long cost(Row r){return r.kind==ShopActionPayload.Kind.BUY_TICKET?com.evolt.teamecon.economy.MoneyMath.payout(r.price,quantity(r)):com.evolt.teamecon.economy.MoneyMath.total(r.price,quantity(r));}
    private List<Component> details(Row r) {
        List<Component> lines=new ArrayList<>();String game=CasinoProgression.gameForItem(r.id);
        if(!game.isEmpty()&&ClientCasinoProgression.ready()) {
            var info=ClientCasinoProgression.game(game);
            lines.add(tr("machine_level",ClientCasinoProgression.rules().profile(game).requiredLevel()));
            lines.add(tr("bet_limit",CasinoScreen.compact(info.maxBet())));lines.add(tr("maximum",decimal(info.maxPayout())));
        }else if(r.kind==ShopActionPayload.Kind.BUY_TICKET) {
            var card=ScratchKind.byId(r.id);
            lines.add(tr("maximum",card.multipliers()[card.multipliers().length-1]));lines.add(Component.translatable("scratch.teamecon.automatic"));lines.add(tr("card_limit",CasinoProgression.cardStakeLimit(ClientCasinoProgression.level())));
        }else if(r.id.equals("teamecon:terminal"))lines.add(tr("terminal_detail"));
        else lines.add(tr(r.kind==ShopActionPayload.Kind.ENCHANT?"books_hint":r.kind==ShopActionPayload.Kind.BUY_BOX?"boxes_hint":"delivery"));
        return lines;
    }
    private Component tooltip(Row r) {
        var text=r.name.copy().append("\n").append(Component.literal(modName(r.mod))).append("\n").append(tr("total",cost(r)));
        if(!r.unlocked)text.append("\n").append(PurchaseRules.describe(r.lockReason));
        for(Component line:details(r))text.append("\n").append(line);
        if(r.kind==ShopActionPayload.Kind.BUY_TICKET)text.append("\n").append(Component.translatable("scratch.teamecon.rule."+r.id));
        return text;
    }
    private void refreshButtons() {
        boolean available=ready()&&waiting==0;
        if(buy!=null) {
            if(tab==Tab.SELL)buy.active=available&&!menu.sale().isEmpty()&&ClientShopCache.saleQuote()>=0;
            else if(tab==Tab.LEVELS) {
                long price=ready()?ClientCasinoProgression.rules().levelCost(selectedLevel):0;
                buy.active=available&&ClientCasinoProgression.valid()&&selectedLevel==ClientCasinoProgression.level()+1&&price>0&&ClientShopCache.balance()>=price;
                buy.setMessage(tr(selectedLevel<=ClientCasinoProgression.level()?"owned":"upgrade"));buy.setTooltip(Tooltip.create(levelTooltip(selectedLevel)));
            }else {
                buy.active=available&&filterMode.isEmpty()&&selected!=null&&selected.unlocked&&quantity(selected)>0&&cost(selected)>0&&ClientShopCache.balance()>=cost(selected)&&(selected.kind!=ShopActionPayload.Kind.BUY_TICKET||amount<=10000&&cost(selected)<=CasinoProgression.cardStakeLimit(ClientCasinoProgression.level()));
                buy.setMessage(tr(selected!=null&&!selected.unlocked?"locked":"buy"));buy.setTooltip(selected==null?null:Tooltip.create(tooltip(selected)));
            }
        }
        for(Button b:quantities){b.visible=(tab==Tab.ITEMS||tab==Tab.CARDS)&&selected!=null&&(selected.kind==ShopActionPayload.Kind.BUY_ITEM||selected.kind==ShopActionPayload.Kind.BUY_TICKET)&&selected.unlocked&&filterMode.isEmpty();b.active=available;}
        if(ticketFactor!=null){ticketFactor.visible=filterMode.isEmpty();ticketFactor.active=available;}
        if(previous!=null)previous.active=page>0;
        if(next!=null)next.active=(page+1)*(filterMode.isEmpty()?pageCapacity():filterCapacity())<(filterMode.isEmpty()?filtered.size():filters.size());
    }
    private void send(ShopActionPayload.Kind kind,String id,int count) {
        if(!ready()||waiting>0)return;waiting=100;
        ClientPayloadSender.sendToServer(new ShopActionPayload(menu.containerId,++requestSequence,kind,id,count));refreshButtons();
    }
    @Override public void containerTick() {
        super.containerTick();if(waiting>0)waiting--;
        if(ready()&&(seenRevision!=ClientShopCache.revision()||seenProgression!=ClientCasinoProgression.revision())) {
            seenRevision=ClientShopCache.revision();seenProgression=ClientCasinoProgression.revision();waiting=0;buildCatalogue();rebuildRows();
        }
        refreshButtons();
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my) {
        int x=leftPos,y=topPos;
        g.fill(x,y,x+imageWidth,y+imageHeight,LINE);g.fill(x+1,y+1,x+imageWidth-1,y+imageHeight-1,0xFF101B22);
        g.fillGradient(x+2,y+2,x+imageWidth-2,y+32,0xFF263B40,0xFF19292F);g.fill(x+8,y+8,x+10,y+26,GOLD);
        g.fill(x+8,y+imageHeight-16,x+imageWidth-8,y+imageHeight-15,LINE);
        if(tab==Tab.SELL) {
            g.fill(x+182,y+60,x+imageWidth-8,y+imageHeight-20,0xFF172A30);
            for(var slot:menu.slots){g.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,LINE);g.fill(x+slot.x,y+slot.y,x+slot.x+16,y+slot.y+16,0xFF0B151E);}
        }else {
            g.fill(x+detailX,y+59,x+imageWidth-8,y+imageHeight-20,LINE);
            g.fillGradient(x+detailX+1,y+60,x+imageWidth-9,y+imageHeight-21,0xFF22363B,0xFF17272E);
        }
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my) {
        g.drawString(font,tr("title"),15,8,WHITE,false);fit(g,tr("wallet",ready()?ClientShopCache.walletName():"…"),15,21,imageWidth-140,DULL);
        String balance=CasinoScreen.compact(ready()?ClientShopCache.balance():0),level="Lv."+ClientCasinoProgression.level();
        g.drawString(font,balance,imageWidth-12-font.width(balance),8,GOLD,false);g.drawString(font,level,imageWidth-12-font.width(level),21,GREEN,false);
        if(tab==Tab.SELL)renderSale(g);else if(tab==Tab.LEVELS)renderUpgrade(g);
        else {
            int size=filterMode.isEmpty()?filtered.size():filters.size(),capacity=filterMode.isEmpty()?pageCapacity():filterCapacity();
            if(size==0)centered(g,(ready()?tr("empty"):Component.translatable("gui.teamecon.waiting")).getString(),detailX/2,130,DULL);
            fitCentered(g,(page+1)+"/"+Math.max(1,(size+capacity-1)/capacity)+" · "+size+" · "+capacity+tr("per_page").getString(),33,imageHeight-31,detailX-66,DULL);
            if(selected!=null)renderDetail(g,selected);
        }
        Component message=ready()?ModNetwork.formatMessage(ClientShopCache.messageKey(),ClientShopCache.messageArgs()):Component.empty();
        fit(g,message.getString().isEmpty()?tr("footer"):message,9,imageHeight-11,imageWidth-18,DULL);
    }
    private void renderSale(GuiGraphics g) {
        g.drawString(font,tr("sale_slot"),12,57,GREEN,false);
        g.drawString(font,playerInventoryTitle,12,128,WHITE,false);
        int w=imageWidth-200;
        fit(g,tr("sell_title"),190,66,w,GOLD);
        paragraph(g,tr("sell_instructions"),190,82,w,4,DULL);
        long quote=ClientShopCache.saleQuote();paragraph(g,quote>=0?tr("sale_quote",quote):tr("sale_empty"),190,129,w,2,WHITE);
        paragraph(g,tr("sell_hint_short"),190,185,w,Math.max(1,(imageHeight-207)/10),DULL);
    }
    private void renderDetail(GuiGraphics g,Row r) {
        int x=detailX+6,w=detailWidth-12;
        g.pose().pushPose();g.pose().translate(detailX+detailWidth/2F-12,65,0);g.pose().scale(1.5F,1.5F,1);g.renderItem(r.icon,0,0);g.pose().popPose();
        paragraph(g,r.name,x,94,w,2,WHITE);
        fit(g,tr(tab==Tab.CARDS?"card_stake":"price",CasinoScreen.compact(tab==Tab.CARDS?cost(r):r.price)),x,118,w,GOLD);
        int bottom=tab==Tab.ITEMS||tab==Tab.CARDS?imageHeight-78:imageHeight-52;
        if(!r.unlocked)paragraph(g,PurchaseRules.describe(r.lockReason),x,132,w,Math.max(1,(bottom-132)/10),RED);
        else if(tab==Tab.ITEMS){fit(g,tr("total",CasinoScreen.compact(cost(r))),x,133,w,WHITE);if(bottom>165)paragraph(g,Component.literal(modName(r.mod)),x,149,w,2,DULL);}
        else {int y=132;for(Component line:details(r))y+=paragraph(g,line,x,y,w,Math.max(0,(bottom-y)/10),DULL)*10;}
    }
    private Component levelTooltip(int level) {
        var text=tr("level_name",level,tr("level."+level)).copy();if(!ready())return text;
        text.append("\n").append(tr("total",ClientCasinoProgression.rules().levelCost(level)));
        for(String game:CasinoProgression.GAMES)if(ClientCasinoProgression.rules().profile(game).requiredLevel()==level) {
            var info=ClientCasinoProgression.game(game);text.append("\n").append(Component.translatable("container.teamecon."+game)).append(" · ")
                    .append(tr("bet_limit",info.maxBet())).append(" · ").append(tr("maximum",decimal(info.maxPayout())));
        }
        for(ScratchKind card:ScratchKind.values())if(ClientCasinoProgression.rules().cardLevel(card)==level)text.append("\n").append(Component.translatable("item.teamecon.scratch_card_"+card.id()));
        if(ClientCasinoProgression.rules().terminal().requiredLevel()==level)text.append("\n").append(tr("terminal_detail"));
        return text;
    }
    private void renderUpgrade(GuiGraphics g) {
        int x=detailX+6,w=detailWidth-12;g.renderItem(new ItemStack(Items.EXPERIENCE_BOTTLE),detailX+detailWidth/2-8,68);
        centered(g,"Lv."+selectedLevel,detailX+detailWidth/2,94,GREEN);fit(g,tr("level."+selectedLevel),x,110,w,WHITE);
        fit(g,tr("price",CasinoScreen.compact(ready()?ClientCasinoProgression.rules().levelCost(selectedLevel):0)),x,127,w,GOLD);
        paragraph(g,tr("shared_levels"),x,143,w,Math.max(1,(imageHeight-196)/10),DULL);
    }
    private final class CategoryButton extends StoreButton {
        private final Category value;
        CategoryButton(Category value,int i){
            super(8+i*(detailX-16)/8,80,(detailX-16)/8-2,18,tr("category."+value.name().toLowerCase(Locale.ROOT)),()->{
                category=value;page=0;selectedId="";filterMode="";rebuildWidgets();
            },()->category==value);
            this.value=value;setTooltip(Tooltip.create(getMessage()));
        }
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
            g.fill(getX(),getY(),getX()+width,getY()+height,category==value?GOLD:isHoveredOrFocused()?GREEN:LINE);
            g.fill(getX()+1,getY()+1,getX()+width-1,getY()+height-1,category==value?0xFF34544E:0xFF213740);
            Item item=switch(value){case ALL->Items.CHEST;case BLOCKS->Items.BRICKS;case TOOLS->Items.IRON_PICKAXE;case WEAPONS->Items.IRON_SWORD;case ARMOR->Items.IRON_CHESTPLATE;case FOOD->Items.APPLE;case MAGIC->Items.ENCHANTED_BOOK;case MATERIALS->Items.IRON_INGOT;};
            g.renderItem(new ItemStack(item),getX()+(width-16)/2,getY()+1);
        }
    }
    private final class ProductButton extends Button {
        final Row row;
        ProductButton(Row r,int index) {
            super(leftPos+8+index%columns*26,topPos+gridTop+index/columns*26,24,24,r.name,b->{selected=r;selectedId=r.id;refreshButtons();},DEFAULT_NARRATION);
            row=r;setTooltip(Tooltip.create(tooltip(r)));
        }
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial) {
            boolean chosen=row.id.equals(selectedId);
            g.fill(getX(),getY(),getX()+width,getY()+height,chosen?GOLD:isHoveredOrFocused()?0xFF78968D:LINE);
            g.fill(getX()+1,getY()+1,getX()+width-1,getY()+height-1,chosen?0xFF314B46:0xFF192C32);g.renderItem(row.icon,getX()+4,getY()+1);
            if(!row.unlocked)g.fill(getX()+19,getY()+2,getX()+22,getY()+5,RED);
            g.pose().pushPose();g.pose().translate(getX()+12,getY()+18,0);g.pose().scale(.625F,.625F,1);
            g.drawString(font,font.plainSubstrByWidth(CasinoScreen.compact(row.price),34),-font.width(font.plainSubstrByWidth(CasinoScreen.compact(row.price),34))/2,0,row.unlocked?GOLD:DULL,false);g.pose().popPose();
        }
    }
    private final class LevelButton extends StoreButton {
        LevelButton(int level) {
            super(8,62+(level-1)*27,detailX-16,24,tr("level_name",level,tr("level."+level)),()->{selectedLevel=level;refreshButtons();},()->selectedLevel==level);
            setTooltip(Tooltip.create(levelTooltip(level)));
        }
    }
    private class StoreButton extends Button {
        private final BooleanSupplier chosen;
        StoreButton(int x,int y,int w,int h,Component label,Runnable action,BooleanSupplier chosen) {
            super(leftPos+x,topPos+y,w,h,label,b->action.run(),DEFAULT_NARRATION);this.chosen=chosen;
        }
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial) {
            g.fill(getX(),getY(),getX()+width,getY()+height,chosen.getAsBoolean()?GOLD:isHoveredOrFocused()?0xFF78968D:LINE);
            g.fill(getX()+1,getY()+1,getX()+width-1,getY()+height-1,active?0xFF273E42:0xFF17272D);
            fitCentered(g,getMessage().getString(),getX()+3,getY()+(height-8)/2,width-6,active?WHITE:DULL);
        }
    }
    @Override protected boolean scrollPanel(double x,double y,double dx,double dy) {
        if(previous!=null&&x>=leftPos+8&&x<leftPos+detailX&&y>=topPos+gridTop&&y<topPos+imageHeight-18){page+=dy<0?1:-1;rebuildRows();return true;}
        return super.scrollPanel(x,y,dx,dy);
    }
    @Override public boolean keyPressed(int key,int scan,int mods) {
        if(search!=null&&search.isFocused()&&minecraft.options.keyInventory.matches(key,scan))return true;
        if(key==256&&!filterMode.isEmpty()){filterMode="";page=0;rebuildWidgets();return true;}
        return super.keyPressed(key,scan,mods);
    }
    @Override protected void renderPanel(GuiGraphics g,int mx,int my,float partial) {
        super.renderPanel(g,mx,my,partial);renderTooltip(g,mx,my);
        if(ready()&&my>=topPos+imageHeight-16&&my<topPos+imageHeight&&mx>=leftPos&&mx<=leftPos+imageWidth)
            g.renderTooltip(font,ModNetwork.formatMessage(ClientShopCache.messageKey(),ClientShopCache.messageArgs()),mx,my);
    }
    private void fit(GuiGraphics g,Component text,int x,int y,int w,int color){g.drawString(font,ellipsis(text.getString(),w),x,y,color,false);}
    private String ellipsis(String text,int w){return font.width(text)<=w?text:font.plainSubstrByWidth(text,Math.max(0,w-8))+"…";}
    private void fitCentered(GuiGraphics g,String text,int x,int y,int w,int color){centered(g,ellipsis(text,w),x+w/2,y,color);}
    private void centered(GuiGraphics g,String text,int x,int y,int color){g.drawString(font,text,x-font.width(text)/2,y,color,false);}
    private int paragraph(GuiGraphics g,Component text,int x,int y,int w,int max,int color) {
        var lines=font.split(text,w);int n=Math.min(max,lines.size());for(int i=0;i<n;i++)g.drawString(font,lines.get(i),x,y+i*10,color,false);return n;
    }
    public static String returnText(double min,double max){return String.format(Locale.ROOT,Math.abs(min-max)<.00001?"%.1f%%":"%.1f–%.1f%%",min*100,max*100);}
    private static String decimal(double value){return String.format(Locale.ROOT,"%.2f",value);}
    private static Component tr(String key,Object...args){return Component.translatable("gui.teamecon.shop."+key,args);}
}
