package com.evolt.teamecon.client;

import com.evolt.teamecon.casino.CasinoMachineBlockEntity;
import com.evolt.teamecon.casino.CasinoMenu;
import com.evolt.teamecon.casino.GameType;
import com.evolt.teamecon.gambling.PenguinGame;
import com.evolt.teamecon.gambling.RouletteGame;
import com.evolt.teamecon.network.ModNetwork;
import com.evolt.teamecon.network.payloads.CasinoActionPayload;
import com.evolt.teamecon.network.payloads.CasinoSyncPayload;
import com.evolt.teamecon.scratch.ScratchKind;
import com.evolt.teamecon.init.ModRegistries;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.Predicate;

/** Remote games with start, stake, then secondary controls in descending screen order. */
public class CasinoScreen extends CompactContainerScreen<CasinoMenu> {
    private static final int ACCENT = UiTheme.ACCENT, TEXT = UiTheme.TEXT, MUTED = UiTheme.MUTED, POSITIVE = UiTheme.POSITIVE, NEGATIVE = UiTheme.NEGATIVE;
    private GameType tab;
    private boolean home;
    private int cardFactor=1;
    private long bet = 1, requestSequence, seenRequest = -1;
    private long betBase = 1;
    private int stakeFactor = 1;
    private boolean applyingFactor;
    private long progressionRevision = -1;
    private int pendingTicks, animationTicks, animationDuration = 1, oddsScroll, ticks;
    private String animatedGame = "", builtOdds = "", rouletteChoice = "red";
    private ScratchKind selectedCard=ScratchKind.MATCH;
    private final Map<GameType, String> results = new EnumMap<>(GameType.class);
    private final boolean[] scratched = new boolean[32];
    private boolean cardAvailable, lastPenguinWon = true;
    private EditBox betField, numberField, factorField;
    private Button multiplyButton;
    private boolean hadCrashRun, crashLost;
    private double crashChartPeak=1;
    private final List<BoundButton> actions = new ArrayList<>();
    private record BoundButton(Button button, Predicate<CasinoSyncPayload> enabled) {}

    public CasinoScreen(CasinoMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 320; imageHeight = 240;
        titleLabelX = 8; titleLabelY = 8; inventoryLabelX = 8; inventoryLabelY = 154;
        home=menu.isRemote();
        tab = GameType.byId(menu.game());
        if (tab == null) tab = GameType.SLOTS;
    }

    private CasinoSyncPayload state() {
        CasinoSyncPayload s = CasinoScreenData.last();
        return s != null && s.containerId() == menu.containerId ? s : null;
    }

    @Override protected void init() {
        imageWidth=home?400:320;imageHeight=home?248:240;
        super.init();
        menu.showInventory(!home);
        numberField = null;
        betField = null;
        factorField=null;multiplyButton=null;
        actions.clear();
        builtOdds = state() == null ? "" : state().tierOdds();
        progressionRevision = ClientCasinoProgression.revision();
        if(home){buildHome();return;}
        if (menu.isRemote()) {
            addRenderableWidget(UiButton.of(tr("terminal.back"),b->{home=true;rebuildWidgets();}).bounds(leftPos+278,topPos+23,32,16).build());
            GameType[] tabs = {GameType.SLOTS, GameType.ROULETTE, GameType.COLOR_WHEEL, GameType.SCRATCH, GameType.PENGUIN, GameType.MULTIPLIER, GameType.HILO};
            for (int i = 0; i < tabs.length; i++) {
                GameType next = tabs[i];
                Button button = addRenderableWidget(UiButton.of(tr("tab." + next.id()), b -> {
                    tab = next; oddsScroll = 0; setBet(bet); rebuildWidgets();
                }).bounds(leftPos + 12 + i * 38, topPos + 23, 37, 16).selected(()->next==tab).build());
            }
        }
        if(tab!=GameType.SCRATCH) {
        betField = UiTheme.input(font, leftPos + 236, topPos + 64, 40, 16, tr("bet"));
        betField.setMaxLength(10);
        betField.setFilter(value -> value.matches("[0-9]*"));
        betField.setValue(String.valueOf(bet));
        betField.setResponder(value -> { try { bet = value.isEmpty() ? 0 : Long.parseLong(value); } catch (NumberFormatException ex) { bet = 0; }
            if(!applyingFactor){betBase=bet;stakeFactor=1;if(factorField!=null)factorField.setValue("1");} });
        addRenderableWidget(betField);
        addRenderableWidget(UiButton.of(tr("max"), b -> setBet(state() == null ? bet : Math.min(state().balance(), betLimit())))
                .bounds(leftPos + 280, topPos + 63, 30, 18).build());
        long[] chips = {10, 100, 1000};
        for (int i = 0; i < chips.length; i++) {
            long chip = chips[i];
            Component label = chip == -1 ? Component.literal("½") : chip == 0 ? tr("clear") : Component.literal("+" + chip);
            addRenderableWidget(UiButton.of(label, b -> setBet(chip > 0 ? bet + chip : chip == -1 ? bet / 2 : 0))
                    .bounds(leftPos + 192 + i * 40, topPos + 83, 38, 13).build());
        }
        factorField=UiTheme.input(font,leftPos+194,topPos+98,52,13,tr("bet_factor"));
        factorField.setMaxLength(7);factorField.setFilter(v->v.matches("[0-9]*"));factorField.setValue(String.valueOf(stakeFactor));addRenderableWidget(factorField);
        multiplyButton=addRenderableWidget(UiButton.of(tr("bet_multiply"),b->{
            int factor=(int)numeric(factorField.getValue());
            long value=com.evolt.teamecon.casino.MachineBetMenu.multiplied(betBase,factor,betLimit());
            if(value>0){applyingFactor=true;setBet(value);applyingFactor=false;stakeFactor=factor;}
        }).bounds(leftPos+252,topPos+98,58,16).build());
        }
        Button exchange = action(tr("deposit"), 213, 115, 97, 18,
                b -> send(CasinoActionPayload.Action.DEPOSIT, "", 0),
                s -> !menu.deposit().getItem(0).isEmpty());
        exchange.setTooltip(Tooltip.create(tr("deposit_hint")));
        buildGameButtons();
        refreshButtons();
    }

    private Button action(Component label, int x, int y, int w, int h,
                          Button.OnPress press, Predicate<CasinoSyncPayload> enabled) {
        Button b = addRenderableWidget(UiButton.of(label, press).bounds(leftPos + x, topPos + y, w, h).build());
        actions.add(new BoundButton(b, enabled));
        return b;
    }

    private void buildHome(){
        int gap=6,tw=(imageWidth-30)/4;
        String[] services={"shop","sell","levels"},targets={"items","sell","levels"};
        net.minecraft.world.item.Item[] icons={net.minecraft.world.item.Items.CHEST,net.minecraft.world.item.Items.HOPPER,net.minecraft.world.item.Items.EXPERIENCE_BOTTLE};
        int serviceWidth=(imageWidth-28)/3;
        for(int i=0;i<3;i++){String target=targets[i];addRenderableWidget(new HomeTile(12+i*(serviceWidth+2),73,serviceWidth,43,tr("terminal."+services[i]),new ItemStack(icons[i]),()->send(CasinoActionPayload.Action.OPEN_SHOP,target,0)));}
        GameType[] games={GameType.HILO,GameType.PENGUIN,GameType.COLOR_WHEEL,GameType.ROULETTE,GameType.SLOTS,GameType.MULTIPLIER,GameType.SCRATCH};
        net.minecraft.world.item.Item[] machines={ModRegistries.HILO_TABLE_ITEM.get(),ModRegistries.PENGUIN_MACHINE_ITEM.get(),ModRegistries.COLOR_WHEEL_TABLE_ITEM.get(),ModRegistries.ROULETTE_TABLE_ITEM.get(),ModRegistries.SLOT_MACHINE_ITEM.get(),ModRegistries.MULTIPLIER_MACHINE_ITEM.get(),ModRegistries.SCRATCH_CARDS.get(ScratchKind.MATCH).get()};
        for(int i=0;i<games.length;i++){GameType game=games[i];addRenderableWidget(new HomeTile(12+i%4*(tw+2),135+i/4*45,tw,41,tr("tab."+game.id()),new ItemStack(machines[i]),()->{home=false;tab=game;rebuildWidgets();}));}
    }
    private void drawHome(GuiGraphics g){
        g.drawString(font,tr("terminal.home"),12,7,TEXT,false);
        String level="Lv."+ClientCasinoProgression.level();g.drawString(font,level,imageWidth-14-font.width(level),7,POSITIVE,false);
        g.drawString(font,tr("terminal.subtitle"),12,29,MUTED,false);
        CasinoSyncPayload s=state();
        g.drawString(font,tr("balance",s==null?"…":compact(s.balance())),12,47,ACCENT,false);
        if(s!=null&&s.stake()>0)fit(g,tr("terminal.live",tr("tab."+s.sessionGame()),s.cashOut()).getString(),145,47,imageWidth-158,POSITIVE);
        g.drawString(font,tr("terminal.games"),12,121,MUTED,false);
        fit(g,tr("terminal.hint").getString(),12,imageHeight-17,imageWidth-24,MUTED);
    }
    private final class HomeTile extends Button {
        private final ItemStack icon;
        HomeTile(int x,int y,int w,int h,Component title,ItemStack icon,Runnable press){super(leftPos+x,topPos+y,w,h,title,b->press.run(),DEFAULT_NARRATION);this.icon=icon;}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
            UiTheme.button(g,getX(),getY(),width,height,active,isHoveredOrFocused(),false);
            // Machine items contain raised 3D geometry. Clip the preview to its own
            // strip, then place the caption above item depth so no glyph is hidden.
            g.enableScissor(getX()+3,getY()+2,getX()+width-3,getY()+height-16);
            g.renderItem(icon,getX()+7,getY()+5);
            g.disableScissor();
            g.pose().pushPose();g.pose().translate(0,0,300);
            g.drawString(font,font.plainSubstrByWidth(getMessage().getString(),width-12),getX()+7,getY()+height-13,TEXT,false);
            g.flush();g.pose().popPose();
        }
    }
    private boolean funded(CasinoSyncPayload s) {
        return ClientCasinoProgression.canPlay(tab.id()) && bet >= s.minBet() && bet <= betLimit() && bet <= s.balance();
    }
    private long betLimit() { return ClientCasinoProgression.game(tab.id()).maxBet(); }
    private boolean running(CasinoSyncPayload s) { return s.stake() > 0 && tab.id().equals(s.sessionGame()); }
    private boolean canRound(CasinoSyncPayload s) { return ClientCasinoProgression.canPlay(tab.id()) && running(s) && s.multiplier() < s.multiplierLimit() && s.rounds() < 1000; }

    private void buildGameButtons() {
        switch (tab) {
            case MULTIPLIER, PENGUIN -> {
                if(tab==GameType.PENGUIN)action(tr("jump_next"),13,115,166,18,
                        b->send(CasinoActionPayload.Action.PLAY,"jump",0),this::canRound);
                action(tr("place_bet"), 192, 43, 56, 18, b -> send(CasinoActionPayload.Action.PLAY, "start", bet),
                        s -> funded(s) && s.stake() == 0);
                action(tr("cash_out"), 252, 43, 58, 18, b -> send(CasinoActionPayload.Action.CASH_OUT, "", 0), this::running);
            }
            case SLOTS -> action(tr("spin"), 192, 43, 118, 18,
                    b -> send(CasinoActionPayload.Action.PLAY, "", bet), s -> funded(s) && !s.slotsOdds().isEmpty());
            case COLOR_WHEEL -> action(tr("roulette.spin"),192,43,118,18,
                    b->send(CasinoActionPayload.Action.PLAY,"",bet),this::funded);
            case SCRATCH -> {
                for(int i=0;i<ScratchKind.values().length;i++){
                    ScratchKind kind=ScratchKind.values()[i];
                    Button select=addRenderableWidget(new TicketButton(kind,leftPos+20+i%2*80,topPos+44+i/2*17));
                    select.setTooltip(Tooltip.create(Component.translatable("item.teamecon.scratch_card_"+kind.id())
                            .append("\n").append(Component.translatable("scratch.teamecon.price",kind.price()))
                            .append("\n").append(Component.translatable("scratch.teamecon.rule."+kind.id()))));
                }
                addRenderableWidget(UiButton.of(Component.translatable("scratch.teamecon.factor",cardFactor),b->{cardFactor=cardFactor*10>Math.min(10000,com.evolt.teamecon.gambling.CasinoProgression.cardStakeLimit(ClientCasinoProgression.level())/selectedCard.price())?1:cardFactor*10;rebuildWidgets();}).bounds(leftPos+230,topPos+67,78,16).build());
                action(Component.translatable("scratch.teamecon.buy",selectedCard.price()*cardFactor),20,115,159,18,
                        b->send(CasinoActionPayload.Action.BUY_CARD,selectedCard.id(),cardFactor),
                        s->menu.isRemote()&&s.balance()>=selectedCard.price()*cardFactor&&selectedCard.price()*cardFactor<=com.evolt.teamecon.gambling.CasinoProgression.cardStakeLimit(ClientCasinoProgression.level())&&ClientCasinoProgression.canBuy(selectedCard));
            }
            case HILO -> {
                action(tr("bet_high"), 192, 43, 56, 18, b -> send(CasinoActionPayload.Action.PLAY, "high", bet),
                        s -> funded(s) && s.hiloPayout() / 2 <= s.expectedCap() + 1e-9);
                action(tr("bet_low"), 252, 43, 58, 18, b -> send(CasinoActionPayload.Action.PLAY, "low", bet),
                        s -> funded(s) && s.hiloPayout() / 2 <= s.expectedCap() + 1e-9);
            }
            case ROULETTE -> {
                String[] choices = {"red", "black", "number:0"};
                for (int i = 0; i < choices.length; i++) {
                    String choice = choices[i];
                    addRenderableWidget(UiButton.of(tr("roulette." + (choice.equals("number:0")?"green":choice)).copy().append("  ×"+decimal((choice.equals("number:0")?32:2)*(ClientCasinoProgression.ready()?ClientCasinoProgression.rules().profile("roulette").payoutScale():1)).replaceAll("\\.?0+$","")), b -> { rouletteChoice = choice; })
                            .bounds(leftPos + 92, topPos + 82 + i * 17, 87, 15).selected(()->rouletteChoice.equals(choice)).build());
                }
                numberField = UiTheme.input(font, leftPos + 14, topPos + 116, 31, 14, tr("roulette.number"));
                numberField.setMaxLength(2); numberField.setFilter(v -> v.matches("[0-9]*")); numberField.setValue("0");
                addRenderableWidget(numberField);
                addRenderableWidget(UiButton.of(tr("roulette.number"), b -> {
                    int value = (int) numeric(numberField.getValue());
                    if (value >= 0 && value <= 36) rouletteChoice = "number:" + value;
                }).bounds(leftPos + 49, topPos + 114, 38, 18).build());
                action(tr("roulette.spin"), 192, 43, 118, 18, b -> send(CasinoActionPayload.Action.PLAY, rouletteChoice, bet),
                        s -> funded(s));
            }
        }
    }

    private void setBet(long value) {
        CasinoSyncPayload s = state();
        bet = Math.max(s==null?1:s.minBet(), Math.min(value, ClientCasinoProgression.ready() ? betLimit() : 1));
        if(!applyingFactor){betBase=bet;stakeFactor=1;}
        if (betField != null) betField.setValue(String.valueOf(bet));
    }

    private void send(CasinoActionPayload.Action action, String choice, long amount) {
        if (state() == null || pendingTicks > 0 || (animationTicks > 0 && action!=CasinoActionPayload.Action.CASH_OUT)) return;
        pendingTicks = 100;
        ClientPayloadSender.sendToServer(new CasinoActionPayload(menu.containerId, ++requestSequence, action, tab.id(), choice, amount));
        refreshButtons();
    }

    @Override public void containerTick() {
        super.containerTick(); ticks++;
        if (pendingTicks > 0) pendingTicks--;
        if (animationTicks > 0) animationTicks--;
        CasinoSyncPayload s = state();
        if(s!=null){
            boolean live=s.stake()>0&&s.sessionGame().equals("multiplier");
            if(live){crashLost=false;crashChartPeak=Math.max(1,s.multiplier());}
            else if(hadCrashRun||s.game().equals("multiplier")&&s.messageKey().contains("busted"))crashLost=s.messageKey().contains("busted");
            if(!live&&!hadCrashRun&&s.game().equals("multiplier")&&s.messageKey().contains("busted")&&s.requestId()!=seenRequest)crashChartPeak=1;
            hadCrashRun=live;
        }
        if (s != null && s.requestId() != seenRequest) {
            boolean first = seenRequest < 0;
            seenRequest = s.requestId();
            if (s.requestId() >= requestSequence) pendingTicks = 0;
            if (first && bet == 1) setBet(s.minBet());
            GameType played = GameType.byId(s.game());
            if (played != null && !s.extra().isEmpty() && !s.extra().equals("original_wallet")) {
                results.put(played, s.extra());
                if (played!=GameType.MULTIPLIER && !s.extra().startsWith("start") && !s.messageKey().contains("cashed_out")) {
                    animatedGame = played.id();
                    animationDuration = CasinoMachineBlockEntity.animationDuration(animatedGame);
                    animationTicks = animationDuration;
                }
                if (played == GameType.SCRATCH) { Arrays.fill(scratched, false); cardAvailable = true; }
                if (played == GameType.PENGUIN) lastPenguinWon = !s.messageKey().contains("fell");
            }
        }
        if (s != null && tab == GameType.MULTIPLIER && !builtOdds.equals(s.tierOdds())) rebuildWidgets();
        if (progressionRevision != ClientCasinoProgression.revision()) { setBet(Math.max(bet, s == null ? 1 : s.minBet())); rebuildWidgets(); }
        refreshButtons();
    }

    private void refreshButtons() {
        CasinoSyncPayload s = state();
        for (BoundButton entry : actions)
            entry.button.active = s != null && pendingTicks == 0 && animationTicks == 0 && entry.enabled.test(s);
        if(multiplyButton!=null){
            multiplyButton.active=s!=null&&s.stake()==0&&com.evolt.teamecon.casino.MachineBetMenu.multiplied(betBase,(int)numeric(factorField.getValue()),betLimit())>0;
            multiplyButton.setTooltip(Tooltip.create(Component.translatable("machine.teamecon.bet_input.base",betBase)));
        }
    }

    @Override protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        UiTheme.panel(g,leftPos,topPos,imageWidth,imageHeight);
        if(!home){
            UiTheme.section(g,leftPos+8,topPos+41,176,95);
            UiTheme.section(g,leftPos+189,topPos+41,123,95);
            UiTheme.section(g,leftPos+179,topPos+152,133,84);
            for(var slot:menu.slots)if(slot.isActive())UiTheme.slot(g,leftPos+slot.x,topPos+slot.y);
        }
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        if(home){drawHome(g);return;}
        g.drawString(font, title, 8, 8, ACCENT, false);
        g.drawString(font, playerInventoryTitle, 8, 154, MUTED, false);
        if(tab!=GameType.SCRATCH)fit(g, tr("bet").getString(), 192, 68, 40, MUTED);
        if (!menu.isRemote()) g.drawString(font, tr("machine_hint"), 12, 26, MUTED, false);
        CasinoSyncPayload s = state();
        if (s == null) { g.drawString(font, tr("waiting"), 10, 140, MUTED, false); return; }
        String balance = tr("balance", compact(s.balance())).getString();
        g.drawString(font, balance, 311 - font.width(balance), 8, ACCENT, false);
        drawGame(g, s);
        Component message = ModNetwork.formatMessage(s.messageKey(), s.messageArgs());
        if (message.getString().isEmpty() && s.stake() > 0) message = tr("active_run", tr("tab." + s.sessionGame()), s.cashOut());
        int color = s.messageKey().contains("won") || s.messageKey().contains("win") || s.messageKey().contains("landed") || s.messageKey().contains("cashed") ? POSITIVE
                : s.messageKey().contains("busted") || s.messageKey().contains("fell") || s.messageKey().contains("no_funds") ? NEGATIVE : TEXT;
        fit(g, message.getString(), 10, 140, 300, color);
        g.drawString(font, tr("odds_header"), 182, 154, ACCENT, false);
        List<Component> odds = odds(s);
        oddsScroll = Math.clamp(oddsScroll, 0, Math.max(0, odds.size() - 8));
        for (int i = oddsScroll; i < odds.size() && i < oddsScroll + 8; i++)
            fit(g, odds.get(i).getString(), 182, 165 + (i - oddsScroll) * 9, 128, MUTED);
    }

    private float progress() {
        return tab.id().equals(animatedGame) && animationTicks > 0 ? 1F - animationTicks / (float) animationDuration : 1F;
    }

    private void drawGame(GuiGraphics g, CasinoSyncPayload s) {
        String result = results.getOrDefault(tab, "");
        boolean moving = animationTicks > 0 && tab.id().equals(animatedGame);
        float progress = progress();
        switch (tab) {
            case SLOTS -> {
                String[] finalSymbols = result.split(",");
                for (int i = 0; i < 3; i++) {
                    int x = 15 + i * 55;
                    UiTheme.section(g,x,47,50,53);
                    float stop = .6F + i * .2F;
                    String symbol = moving && progress < stop ? CasinoVisuals.SYMBOLS[(ticks / 2 + i * 2) % 6]
                            : finalSymbols.length == 3 ? finalSymbols[i] : CasinoVisuals.SYMBOLS[i];
                    CasinoVisuals.item(g, new ItemStack(CasinoVisuals.symbolItem(symbol)), x + 9, 53, 2);
                    UiTheme.centered(g,font, font.plainSubstrByWidth(CasinoVisuals.label("symbol", symbol).getString(), 48), x + 25, 88, TEXT);
                }
            }
            case MULTIPLIER -> {
                String value = "×" + decimal(running(s) ? s.multiplier() : crashLost?0:1);
                g.pose().pushPose(); g.pose().translate(95, 45, 0); g.pose().scale(1.8F, 1.8F, 1);
                UiTheme.centered(g,font, value, 0, 0, crashLost?NEGATIVE:POSITIVE); g.pose().popPose();
                CasinoVisuals.crashCurve(g,15,66,162,28,crashChartPeak,crashLost,running(s)||crashChartPeak>1||crashLost,crashLost?NEGATIVE:POSITIVE);
                fit(g,tr(running(s)?"crash_value":crashLost?"crash_zero":"crash_ready",s.cashOut()).getString(),15,99,162,MUTED);
            }
            case PENGUIN -> {
                CasinoVisuals.penguin(g, 13, 40, progress, lastPenguinWon, moving);
                fit(g, tr("run_value", running(s) ? s.rounds() : 0, running(s) ? s.cashOut() : 0).getString(), 15, 99, 162, MUTED);
            }
            case HILO -> {
                int roll = moving ? (ticks * 17) % 100 + 1 : (int) numeric(result);
                UiTheme.centered(g,font, tr("hilo_range"), 95, 45, MUTED);
                g.pose().pushPose(); g.pose().translate(95, 63, 0); g.pose().scale(3, 3, 1);
                UiTheme.centered(g,font, roll == 0 ? "--" : String.valueOf(roll), 0, 0, ACCENT); g.pose().popPose();
                UiTheme.centered(g,font, tr("last_roll", roll == 0 ? "--" : roll), 95, 101, MUTED);
            }
            case ROULETTE -> {
                int roll = (int) numeric(result);
                CasinoVisuals.wheel(g, 49, 78, roll, progress);
                UiTheme.section(g,38,73,23,12);
                UiTheme.centered(g,font, result.isEmpty() || moving ? "?" : String.valueOf(roll), 49, 75, TEXT);
            }
            case COLOR_WHEEL -> {
                int pocket=result.isEmpty()?0:(int)numeric(result.split(",")[0]);
                CasinoVisuals.prizeWheel(g,95,76,pocket,progress);
                if(!moving&&!result.isEmpty()){
                    // The wheel reaches y=108. Keep the full result line below it,
                    // even when a translated label or multiplier is wider than usual.
                    g.fill(13,113,179,132,UiTheme.SECTION);
                    fit(g,tr("color_result",com.evolt.teamecon.gambling.ColorWheelGame.pocket(pocket).multiplier()
                            *ClientCasinoProgression.rules().profile("color_wheel").payoutScale()).getString(),18,118,156,ACCENT);
                }
            }
            case SCRATCH -> {
                CasinoVisuals.item(g,new ItemStack(ModRegistries.SCRATCH_CARDS.get(selectedCard).get()),193,44,2F);
                fit(g,Component.translatable("scratch.teamecon.price",selectedCard.price()*cardFactor).getString(),230,47,79,ACCENT);
                int[] prizes=selectedCard.multipliers();
                fit(g,"MAX ×"+prizes[prizes.length-1],230,61,79,MUTED);
                var rules=font.split(Component.translatable("scratch.teamecon.rule."+selectedCard.id()),114);
                for(int i=0;i<Math.min(3,rules.size());i++)g.drawString(font,rules.get(i),194,85+i*9,TEXT,false);
            }
        }
    }

    private final class TicketButton extends Button {
        private final ScratchKind kind;
        TicketButton(ScratchKind kind,int x,int y){
            super(x,y,78,16,Component.translatable("scratch.teamecon.short."+kind.id()),
                    b->{selectedCard=kind;cardFactor=1;oddsScroll=0;rebuildWidgets();},DEFAULT_NARRATION);
            this.kind=kind;
        }
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
            UiTheme.button(g,getX(),getY(),width,height,active,isHoveredOrFocused(),selectedCard==kind);
            CasinoVisuals.item(g,new ItemStack(ModRegistries.SCRATCH_CARDS.get(kind).get()),getX()+2,getY()+1,.875F);
            g.drawString(font,font.plainSubstrByWidth(Component.translatable("item.teamecon.scratch_card_"+kind.id()).getString(),57),getX()+19,getY()+4,TEXT,false);
        }
    }
    private List<Component> odds(CasinoSyncPayload s) {
        List<Component> out = new ArrayList<>();
        if (tab != GameType.SCRATCH && ClientCasinoProgression.ready()) {
            var profile = ClientCasinoProgression.rules().profile(tab.id());
            if (profile != null) {
                var info = ClientCasinoProgression.game(tab.id());
                out.add(Component.translatable("gui.teamecon.shop.machine_level", profile.requiredLevel()));
                out.add(Component.translatable("gui.teamecon.shop.bet_limit", info.maxBet()));
            }
        }
        switch (tab) {
            case MULTIPLIER -> {
                out.add(tr("odds.crash"));out.add(tr("odds.crash_offline"));out.add(tr("odds.bust"));
                out.add(tr("odds.limit",compact((long)ClientCasinoProgression.game(tab.id()).maxPayout())));
            }
            case PENGUIN -> {
                if(ClientCasinoProgression.ready()){
                    var jump=PenguinGame.next(running(s)?s.rounds():0,running(s)?s.multiplier():1,ClientCasinoProgression.game("penguin").maxPayout(),
                            ClientCasinoProgression.rules().profile("penguin").chanceScale());
                    out.add(tr("odds.next_layer",running(s)?s.rounds()+1:1,decimal((running(s)?s.multiplier():1)*(1+jump.gain()))));
                }
                out.add(tr("odds.penguin")); out.add(tr("odds.bust")); out.add(tr("odds.limit", compact((long) ClientCasinoProgression.game(tab.id()).maxPayout())));
            }
            case SLOTS -> { for (String[] r : rows(s.slotsOdds())) out.add(tr("odds.slots.short", r[1], CasinoVisuals.label("symbol", r[0]), decimal(numeric(r[2])))); }
            case SCRATCH -> {
                for(int multiplier:selectedCard.multipliers())out.add(Component.literal("×"+multiplier));
                out.add(Component.translatable("scratch.teamecon.automatic"));
            }
            case HILO -> { out.add(tr("odds.hilo.high")); out.add(tr("odds.hilo.low")); out.add(tr("odds.hilo.chance", decimal(s.hiloPayout()))); }
            case COLOR_WHEEL -> {
                for(var color:com.evolt.teamecon.gambling.ColorWheelGame.COLORS)out.add(CasinoVisuals.label("color",color.id()).copy().append(" ×"+decimal(color.multiplier()*ClientCasinoProgression.rules().profile("color_wheel").payoutScale())));
            }
            case ROULETTE -> {
                double scale = ClientCasinoProgression.ready() ? ClientCasinoProgression.rules().profile("roulette").payoutScale() : 0;
                out.add(tr("roulette.selected", rouletteChoice.startsWith("number:") ? rouletteChoice.substring(7) : tr("roulette." + rouletteChoice)));
                out.add(tr("odds.roulette.outside", decimal(2 * scale))); out.add(tr("odds.roulette.straight", decimal(32 * scale)));
                out.add(tr("odds.roulette.zero"));
            }
        }
        out.add(tr("odds.includes_stake"));
        return out;
    }

    private int scratchCount() { int n = 0; for (boolean value : scratched) if (value) n++; return n; }
    private boolean scratchAt(double mouseX, double mouseY) {
        if (tab != GameType.SCRATCH || !cardAvailable) return false;
        double x = mouseX - leftPos - 24, y = mouseY - topPos - 51;
        if (x < 0 || x >= 144 || y < 0 || y >= 52) return false;
        for (int i = 0; i < scratched.length; i++)
            if (Math.abs(x - (i % 8 * 18 + 9)) <= 20 && Math.abs(y - (i / 8 * 13 + 6)) <= 14) scratched[i] = true;
        return true;
    }
    @Override protected boolean clickPanel(double x, double y, int button) {
        return button == 0 && scratchAt(x, y) || super.clickPanel(x, y, button);
    }
    @Override protected boolean dragPanel(double x, double y, int button, double dx, double dy) {
        return button == 0 && scratchAt(x, y) || super.dragPanel(x, y, button, dx, dy);
    }
    @Override protected boolean scrollPanel(double x, double y, double dx, double dy) {
        if (x >= leftPos + 178 && x < leftPos + 312 && y >= topPos + 153 && y <= topPos + 238) {
            oddsScroll = Math.max(0, oddsScroll - (int) Math.signum(dy)); return true;
        }
        return super.scrollPanel(x, y, dx, dy);
    }
    @Override protected void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.renderPanel(g, mouseX, mouseY, partial);
        renderTooltip(g, mouseX, mouseY);
        CasinoSyncPayload s = state();
        if (s != null && mouseY >= topPos + 137 && mouseY < topPos + 151 && mouseX >= leftPos + 8 && mouseX < leftPos + 312)
            g.renderTooltip(font, ModNetwork.formatMessage(s.messageKey(), s.messageArgs()), mouseX, mouseY);
        if (s != null && mouseY >= topPos + 4 && mouseY < topPos + 20 && mouseX > leftPos + 180 && mouseX < leftPos + 313)
            g.renderTooltip(font, tr("wallet_detail", s.walletName(), s.balance(), s.minBet(), betLimit()), mouseX, mouseY);
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if ((betField != null && betField.isFocused() || numberField != null && numberField.isFocused() || factorField!=null&&factorField.isFocused())
                && minecraft.options.keyInventory.matches(key, scan)) return true;
        return super.keyPressed(key, scan, mods);
    }

    private void fit(GuiGraphics g, String text, int x, int y, int width, int color) {
        g.drawString(font, font.width(text) <= width ? text : font.plainSubstrByWidth(text, width - 8) + "…", x, y, color, false);
    }
    private static List<String[]> rows(String text) {
        List<String[]> result = new ArrayList<>();
        if (text != null && !text.isEmpty()) for (String row : text.split(";")) {
            String[] parts = row.split(","); if (parts.length == 3) result.add(parts);
        }
        return result;
    }
    private static double numeric(String text) {
        try { return Double.parseDouble(text); } catch (NumberFormatException ex) { return 0; }
    }
    public static String compact(long value) {
        if (value >= 1_000_000_000) return String.format(Locale.ROOT, "%.1fB", value / 1_000_000_000D);
        if (value >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", value / 1_000_000D);
        if (value >= 10000) return String.format(Locale.ROOT, "%.1fk", value / 1000D);
        return Long.toString(value);
    }
    private static String decimal(double value) { return String.format(Locale.ROOT, "%.2f", value); }
    private static String percent(double value) { return String.format(Locale.ROOT, value * 100 < 1 ? "%.1f" : "%.0f", value * 100); }
    private static Component tr(String key, Object... args) { return Component.translatable("gui.teamecon." + key, args); }
}
