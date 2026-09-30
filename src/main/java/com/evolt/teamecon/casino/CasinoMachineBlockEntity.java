package com.evolt.teamecon.casino;

import com.evolt.teamecon.init.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.evolt.teamecon.gambling.PendingMachineGame;
import com.evolt.teamecon.gambling.GamblingService;
import java.util.UUID;

/** Visual state is timestamped in game ticks, so every viewer sees the same animation. */
public class CasinoMachineBlockEntity extends BlockEntity implements MenuProvider {
    private long animationStart = -1000;
    private int duration = 1;
    private boolean won;
    private String[] reels = {"cherry", "lemon", "orange"};
    private long bet = 100, balance, cashOut, lastControlTick = -1000;
    private long betBase = 100;
    private int betFactor = 1;
    private UUID operator;
    private String operatorName = "", choice = "", result = "";
    private double multiplier = 1;
    private double chartPeak = 1;
    private boolean hasRun;
    private int runRounds;
    private double chance,returnFactor;

    public CasinoMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.CASINO_MACHINE_ENTITY.value(), pos, state);
        if (machineId().equals("hilo")) bet = betBase = 20;
    }

    public String machineId() {
        return getBlockState().getBlock() instanceof CasinoMachineBlock machine ? machine.game().id() : "";
    }

    public UUID operator() { return operator; }
    public String operatorName() { return operatorName; }
    public long bet() { return bet; }
    public long betBase() { return betBase; }
    public int betFactor() { return betFactor; }
    public void selectFactor(net.minecraft.server.level.ServerPlayer player, long amount, int factor) {
        bet = amount;
        betFactor = factor;
        select(player, amount, choice);
    }
    public long balance() { return balance; }
    public long cashOut() { return cashOut; }
    public double multiplier() { return multiplier; }
    public double chartPeak() { return chartPeak; }
    public boolean hasRun() { return hasRun; }
    public int runRounds() { return runRounds; }
    public double chance(){return chance;}
    public double returnFactor(){return returnFactor;}
    public String choice() { return choice; }
    public String result() { return spinTicks() > 0 ? "" : result; }
    public boolean controlAllowed() {
        if (level == null || level.getGameTime() - lastControlTick < 3) return false;
        lastControlTick = level.getGameTime(); return true;
    }
    public void select(net.minecraft.server.level.ServerPlayer player, long bet, String choice) {
        this.operator = player.getUUID(); this.operatorName = player.getName().getString();
        if (this.bet != bet) { betBase = bet; betFactor = 1; }
        this.bet = bet; this.choice = choice; refreshPlayer(player);
        var mod=com.evolt.teamecon.TeamEconomyMod.get();
        var tier=machineId().equals("multiplier")?mod.gambling().tier(machineId(),choice):null;
        if(tier!=null){chance=tier.successChance();returnFactor=1+tier.gain();}
        else if(machineId().equals("hilo")){chance=.5;returnFactor=mod.gambling().hiLoPayout();}
        else if(machineId().equals("roulette")){chance=choice.startsWith("number:")?1D/37:18D/37;returnFactor=(choice.startsWith("number:")?32:2)*mod.casinoProgression().profile("roulette").payoutScale();}
        sync();
    }
    public void refreshPlayer(net.minecraft.server.level.ServerPlayer player) {
        var mod = com.evolt.teamecon.TeamEconomyMod.get();
        long next = mod.economy().manager().getBalance(com.evolt.teamecon.team.TeamUtil.walletKey(player.getServer(), player.getUUID()));
        var run = mod.gambling().sessionOf(com.evolt.teamecon.gambling.SessionKey.machine(player.getUUID(),level.dimension().location().toString(),worldPosition));
        boolean active = run != null && run.atMachine(level.dimension().location().toString(),worldPosition);
        long value = active ? run.cashOutValue() : 0;
        double mult = active ? run.multiplier() : multiplier;
        int rounds = active ? run.rounds() : runRounds;
        boolean changed = balance != next || hasRun != active || cashOut != value || multiplier != mult || runRounds != rounds;
        balance = next; hasRun = active; cashOut = value; multiplier = mult;
        if(active&&machineId().equals("multiplier"))chartPeak=Math.max(1,mult);
        runRounds = rounds;
        if(machineId().equals("penguin")){
            var tier=com.evolt.teamecon.gambling.PenguinGame.next(active?run.rounds():0,active?run.multiplier():1,
                    mod.gambling().maxMultiplier("penguin"),mod.casinoProgression().profile("penguin").chanceScale());
            chance=tier.successChance();returnFactor=active?Math.min(run.maxMultiplier(),run.multiplier()*(1+tier.gain())):1+tier.gain();
        }
        if (changed) sync();
    }
    public void startLive() {
        animationStart=-1000;duration=1;result="";multiplier=1;chartPeak=1;cashOut=0;won=false;runRounds=0;
        reels=new String[]{"mult","1.00",""};sync();
    }
    public void startExternal(PendingMachineGame game, int ticks) {
        String[] symbols = switch (game.game()) {
            case "slots", "penguin", "color_wheel" -> game.extra().split(",");
            case "multiplier" -> new String[]{"mult", String.format(java.util.Locale.ROOT,"%.2f",game.multiplier()),""};
            default -> new String[]{game.game(),game.extra(),""};
        };
        if (symbols.length == 3 && game.kind() != PendingMachineGame.Kind.CASH_OUT) reels = symbols;
        operator = game.player(); operatorName = game.playerName(); bet = game.stake();
        animationStart = level.getGameTime(); duration = ticks; won = game.won(); result = "";
        sync(); level.playSound(null,worldPosition,SoundEvents.LEVER_CLICK,SoundSource.BLOCKS,.65F,.8F);
    }
    public void settled(GamblingService.Result result) {
        if(machineId().equals("multiplier"))chartPeak=Math.max(chartPeak,Math.max(multiplier,result.multiplier()));
        this.result = result.status() == GamblingService.Status.ROUND_WON ? "run:" + String.format(java.util.Locale.ROOT,"%.2f",result.multiplier())
                : result.status() == GamblingService.Status.BUSTED ? "lost" : "paid:" + result.payout();
        won = result.payout() > 0 || result.status() == GamblingService.Status.ROUND_WON;
        if(result.status()!=GamblingService.Status.ROUND_WON){hasRun=false;cashOut=0;multiplier=result.multiplier();}
        sync();
        level.playSound(null,worldPosition,won?SoundEvents.PLAYER_LEVELUP:SoundEvents.NOTE_BLOCK_BASS.value(),SoundSource.BLOCKS,.5F,won?1.6F:.7F);
    }
    public void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
    }

    @Override public Component getDisplayName() { return Component.translatable("container.teamecon." + machineId()); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new CasinoMenu(id, inventory, machineId(), getBlockPos());
    }

    public static int animationDuration(String game) {
        return switch (game) {
            case "slots" -> 48;
            case "roulette" -> 64;
            case "hilo", "scratch" -> 30;
            case "penguin" -> 14;
            default -> 20;
        };
    }

    public void showSpin(String[] symbols, boolean won) {
        if (level == null || level.isClientSide) return;
        if (symbols != null && symbols.length == 3) reels = symbols.clone();
        animationStart = level.getGameTime();
        duration = animationDuration(machineId());
        this.won = won;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, .45F, 1.2F);
    }

    public float progress(float partialTick) {
        if (level == null) return 1;
        return Math.clamp((level.getGameTime() - animationStart + partialTick) / duration, 0F, 1F);
    }
    public int spinTicks() {
        return level == null ? 0 : (int) Math.max(0, duration - (level.getGameTime() - animationStart));
    }
    public int winTicks() {
        if (!won || level == null) return 0;
        long elapsed = level.getGameTime() - animationStart - duration;
        return elapsed < 0 ? 0 : (int) Math.max(0, 30 - elapsed);
    }
    public boolean won() { return won; }
    public String[] reels() { return reels.clone(); }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putLong("animationStart", animationStart);
        tag.putInt("duration", duration);
        tag.putBoolean("won", won);
        for (int i = 0; i < 3; i++) tag.putString("reel" + i, reels[i]);
        tag.putLong("bet",bet); tag.putLong("balance",balance); tag.putLong("cashOut",cashOut);
        tag.putLong("betBase",betBase); tag.putInt("betFactor",betFactor);
        if(operator!=null)tag.putUUID("operator",operator);
        tag.putString("operatorName",operatorName);tag.putString("choice",choice);tag.putString("result",result);
        tag.putDouble("runMultiplier",multiplier);tag.putBoolean("hasRun",hasRun);
        tag.putDouble("chartPeak",chartPeak);
        tag.putInt("runRounds",runRounds);
        tag.putDouble("chance",chance);tag.putDouble("returnFactor",returnFactor);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        animationStart = tag.contains("animationStart") ? tag.getLong("animationStart") : -1000;
        duration = Math.clamp(tag.getInt("duration"), 1, 240);
        won = tag.getBoolean("won");
        for (int i = 0; i < 3; i++)
            if (tag.contains("reel" + i)) reels[i] = tag.getString("reel" + i);
        bet=tag.contains("bet")?Math.max(1,tag.getLong("bet")):100;
        betBase=tag.contains("betBase")?Math.max(1,tag.getLong("betBase")):bet;
        betFactor=tag.contains("betFactor")?Math.clamp(tag.getInt("betFactor"),1,1_000_000):1;
        if (MachineBetMenu.multiplied(betBase,betFactor,com.evolt.teamecon.economy.MoneyMath.MAX_MONEY)!=bet) { betBase=bet;betFactor=1; }
        balance=tag.getLong("balance");cashOut=tag.getLong("cashOut");
        operator=tag.hasUUID("operator")?tag.getUUID("operator"):null;operatorName=tag.getString("operatorName");
        choice=tag.getString("choice");result=tag.getString("result");
        multiplier=tag.contains("runMultiplier")?tag.getDouble("runMultiplier"):1;hasRun=tag.getBoolean("hasRun");
        chartPeak=tag.contains("chartPeak")?Math.clamp(tag.getDouble("chartPeak"),1,1_000_000):Math.max(1,multiplier);
        runRounds=Math.max(0,tag.getInt("runRounds"));
        chance=tag.getDouble("chance");returnFactor=tag.getDouble("returnFactor");
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, provider);
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
