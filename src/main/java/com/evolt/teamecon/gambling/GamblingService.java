package com.evolt.teamecon.gambling;

import com.evolt.teamecon.config.TeConfig;
import com.evolt.teamecon.economy.MoneyMath;
import com.evolt.teamecon.economy.TeamEconomyManager;
import com.evolt.teamecon.economy.Transaction;
import com.evolt.teamecon.economy.TxType;
import net.minecraft.server.MinecraftServer;

import java.util.Random;
import java.util.UUID;

/** All stakes, random draws and settlements run on the server thread. */
public final class GamblingService {
    public enum Status {
        OK, BUSY, TOO_SMALL, TOO_BIG, NO_FUNDS, DISABLED, BAD_CHOICE, ALREADY_PLAYING,
        NO_SESSION, WRONG_GAME, TEAM_CHANGED, LEVEL_LOCKED, LIMIT, ROUND_WON, BUSTED, CASHED_OUT
    }

    public record Result(Status status, long payout, double multiplier, String extra) {
        public boolean accepted() {
            return status == Status.OK || status == Status.ROUND_WON
                    || status == Status.BUSTED || status == Status.CASHED_OUT;
        }
    }

    private final TeamEconomyManager manager;
    private final MinecraftServer server;
    private final CasinoProgression progression;
    private final RiskTiers tiers = new RiskTiers();
    private final ScratchPools scratch = new ScratchPools();
    private final SlotsGame slots = new SlotsGame();
    private final Random random = new Random();

    public GamblingService(MinecraftServer server, TeamEconomyManager manager) {
        this(server, manager, new CasinoProgression());
    }

    public GamblingService(MinecraftServer server, TeamEconomyManager manager, CasinoProgression progression) {
        this.server = server;
        this.manager = manager;
        this.progression = progression;
    }

    public void loadConfigs(java.nio.file.Path configDir) {
        // Base tables are safe on their own; the cap applies again to the effective,
        // progression-adjusted odds below (including configured chance/payout scales).
        tiers.load(configDir, 1D);
        scratch.load(configDir);
        slots.load(configDir, 1D);
    }

    public BetSession sessionOf(UUID player) { return manager.bet(player); }
    public BetSession sessionOf(SessionKey key) { return manager.bet(key); }
    public RiskTiers tiers() { return tiers; }
    public ScratchPools scratch() { return scratch; }
    public SlotsGame slots() { return slots; }
    public CasinoProgression progression() { return progression; }
    public long minBet() { return Math.max(1L, TeConfig.GAMBLING.minBet.get()); }
    public long maxBet() { return Math.max(minBet(), TeConfig.GAMBLING.maxBet.get()); }
    public long maxBet(String game) {
        var p = progression.profile(game);
        return p == null ? 0 : Math.min(maxBet(), p.maxBet());
    }
    public double hiLoPayout() { return 2 * progression.profile("hilo").payoutScale(); }
    public double maxMultiplier() { return TeConfig.GAMBLING.maxMultiplier.get(); }
    public double maxMultiplier(String game) {
        var p = progression.profile(game);
        return p == null ? 1 : Math.min(maxMultiplier(), p.maxRunMultiplier());
    }
    public double expectedCap() { return TeConfig.GAMBLING.expectedValueCap.get(); }

    public java.util.List<RiskTier> effectiveTiers(String game) {
        var p = progression.profile(game);
        if (p == null || game.equals("multiplier")) return java.util.List.of();
        return (game.equals("penguin") ? PenguinGame.jumps() : tiers.all()).stream()
                .map(t -> new RiskTier(t.name(), t.successChance() * p.chanceScale(), t.gain()))
                .filter(t -> t.expectedFactor() <= expectedCap() + 1e-9).toList();
    }
    public RiskTier tier(String game, String choice) {
        return effectiveTiers(game).stream().filter(t -> t.name().equals(choice)).findFirst().orElse(null);
    }
    private RiskTier jumpTier(BetSession session) {
        return PenguinGame.next(session.rounds(), session.multiplier(), session.maxMultiplier(),
                progression.profile("penguin").chanceScale());
    }
    public long now() { return server.overworld().getGameTime(); }
    public double crashReturn() {
        var p = progression.profile("multiplier");
        return .96D * p.chanceScale() * p.payoutScale();
    }
    public record Odds(double minReturn, double maxReturn, double maxPayout) {}
    /** Gross theoretical return before integer rounding; run games quote one step, not a whole streak. */
    public Odds odds(String game) {
        var p = progression.profile(game);
        if (p == null) return new Odds(0, 0, 0);
        double ev, max;
        switch (game) {
            case "multiplier" -> { return new Odds(crashReturn(), crashReturn(), maxMultiplier(game)); }
            case "penguin" -> {
                var choices = effectiveTiers(game);
                return new Odds(choices.stream().mapToDouble(RiskTier::expectedFactor).min().orElse(0),
                        choices.stream().mapToDouble(RiskTier::expectedFactor).max().orElse(0), maxMultiplier(game));
            }
            case "hilo" -> { ev = .5 * hiLoPayout(); max = hiLoPayout(); }
            case "roulette" -> { return new Odds(RouletteGame.STRAIGHT_EXPECTED_FACTOR * p.payoutScale(),
                    RouletteGame.EXPECTED_FACTOR * p.payoutScale(), 32 * p.payoutScale()); }
            case "color_wheel" -> { ev = ColorWheelGame.EXPECTED_FACTOR * p.payoutScale(); max = 10 * p.payoutScale(); }
            case "slots" -> {
                ev = slots.expectedPayout(slots.reel().stream().mapToInt(SlotsGame.Symbol::weight).sum()) * p.payoutScale();
                max = slots.payouts().stream().mapToDouble(SlotsGame.Payout::multiplier).max().orElse(0) * p.payoutScale();
            }
            default -> { return new Odds(0, 0, 0); }
        }
        return new Odds(ev, ev, max);
    }
    public boolean enabled(String game) {
        return progression.valid() && maxBet(game) >= minBet() && odds(game).maxReturn() > 0
                && odds(game).maxReturn() <= expectedCap() + 1e-9;
    }

    private Result fail(Status status) { return new Result(status, 0, 0, ""); }

    public long maxBet(String game, UUID wallet) {
        return Math.min(maxBet(), progression.scaledMaxBet(game, manager.casinoLevel(wallet)));
    }

    public Status checkBet(UUID wallet, long stake) {
        if (stake < minBet()) return Status.TOO_SMALL;
        if (stake > maxBet()) return Status.TOO_BIG;
        return manager.canAfford(wallet, stake) ? Status.OK : Status.NO_FUNDS;
    }

    public Status checkBet(UUID wallet, long stake, String game) {
        if (!progression.gameAccess(manager, wallet, game).unlocked()) return Status.LEVEL_LOCKED;
        if (!enabled(game)) return Status.DISABLED;
        if (stake > maxBet(game, wallet)) return Status.TOO_BIG;
        return checkBet(wallet, stake);
    }

    private void deduct(UUID wallet, UUID player, String name, long amount, String source) {
        if (!manager.debit(wallet, amount)) throw new IllegalStateException("Unfunded stake");
        manager.appendTransaction(new Transaction(manager.nextTxId(), wallet, player, name,
                TxType.GAMBLE_LOSS, source, 1, amount, -amount, System.currentTimeMillis()));
    }

    private long award(UUID wallet, UUID player, String name, long amount, String source) {
        if (amount <= 0) return 0;
        long credited = manager.credit(wallet, amount);
        manager.appendTransaction(new Transaction(manager.nextTxId(), wallet, player, name,
                TxType.GAMBLE_WIN, source, 1, credited, credited, System.currentTimeMillis()));
        return credited;
    }

    public Result startBet(UUID player, UUID wallet, String name, long stake, String game) {
        return startBet(SessionKey.terminal(player), wallet, name, stake, game);
    }

    private Result startBet(SessionKey key, UUID wallet, String name, long stake, String game) {
        UUID player = key.player();
        if (manager.pendingMachine(key) != null) return fail(Status.BUSY);
        if (!"multiplier".equals(game) && !"penguin".equals(game)) return fail(Status.BAD_CHOICE);
        if (sessionOf(key) != null && sessionOf(key).isActive()) return fail(Status.ALREADY_PLAYING);
        Status status = checkBet(wallet, stake, game);
        if (status != Status.OK) return fail(status);
        deduct(wallet, player, name, stake, game);
        BetSession session = new BetSession(stake, wallet, game, maxMultiplier(game));
        if (game.equals("multiplier")) {
            session.startCrash(now(), CrashGame.crashAfter(random.nextDouble(), crashReturn(), session.maxMultiplier()));
            if (session.advanceCrash(now())) return new Result(Status.BUSTED, 0, 0, "crash");
        }
        if (key.pos() != null) session.bindMachine(key.dimension(), key.pos());
        manager.putBet(player, session);
        return new Result(Status.OK, 0, 1, "start,0,1");
    }

    public Result playRound(UUID player, UUID wallet, String game, String choice) {
        if (!game.equals("penguin")) return fail(Status.BAD_CHOICE);
        BetSession session = sessionOf(player);
        if (session == null || !session.isActive()) return fail(Status.NO_SESSION);
        if (!session.game().equals(game)) return fail(Status.WRONG_GAME);
        if (session.worldBound()) return fail(Status.BUSY);
        if (!session.wallet().equals(wallet)) return fail(Status.TEAM_CHANGED);
        if (!progression.gameAccess(manager, wallet, game).unlocked()) return fail(Status.LEVEL_LOCKED);
        if (!enabled(game)) return fail(Status.DISABLED);
        if (session.atLimit()) return fail(Status.LIMIT);
        RiskTier tier = choice.equals("jump") ? jumpTier(session) : null;
        if (tier == null || !tier.isValid()) return fail(Status.BAD_CHOICE);
        if (tier.expectedFactor() > expectedCap() + 1e-9) return fail(Status.DISABLED);
        boolean won = random.nextDouble() < tier.successChance();
        if (won) session.applySuccess(tier);
        else session.applyFailure(tier);
        int rounds = session.rounds();
        double multiplier = session.multiplier();
        if (!won) manager.removeBet(player);
        else manager.setDirty();
        return new Result(won ? Status.ROUND_WON : Status.BUSTED, 0, multiplier,
                choice + "," + rounds + "," + (won ? 1 : 0));
    }

    /** Always pays the wallet that funded the stake, even after leaving a team. */
    public Result cashOut(UUID player, UUID currentWallet, String name, String game) {
        return cashOut(SessionKey.terminal(player), currentWallet, name, game);
    }

    public Result cashOut(SessionKey key, UUID currentWallet, String name, String game) {
        if (manager.pendingMachine(key) != null) return fail(Status.BUSY);
        BetSession session = sessionOf(key);
        if (session == null || !session.isActive()) return fail(Status.NO_SESSION);
        if (!session.game().equals(game)) return fail(Status.WRONG_GAME);
        if (session.advanceCrash(now())) {
            manager.removeBet(key);
            return new Result(Status.BUSTED, 0, 0, "crash");
        }
        long payout = session.close();
        manager.removeBet(key);
        long credited = award(session.wallet(), key.player(), name, payout, game);
        return new Result(Status.CASHED_OUT, credited, session.multiplier(),
                session.wallet().equals(currentWallet) ? "" : "original_wallet");
    }

    public Result startCrashMachine(net.minecraft.server.level.ServerPlayer player, net.minecraft.core.BlockPos pos, long stake) {
        String dimension = player.level().dimension().location().toString();
        if (manager.machineBusy(dimension, pos)) return fail(Status.BUSY);
        if(manager.activeBets().values().stream().anyMatch(run->run.atMachine(dimension,pos)))return fail(Status.BUSY);
        UUID wallet = com.evolt.teamecon.team.TeamUtil.walletKey(server, player.getUUID());
        return startBet(SessionKey.machine(player.getUUID(), dimension, pos), wallet, player.getName().getString(), stake, "multiplier");
    }

    /** Advances live rounds even when their machine is unloaded or their player is offline. */
    public Result tickCrash(UUID player, String name, long tick) {
        return tickCrash(SessionKey.terminal(player), name, tick);
    }

    public Result tickCrash(SessionKey key, String name, long tick) {
        BetSession session = sessionOf(key);
        if (session == null || !session.liveCrash()) return fail(Status.NO_SESSION);
        if (session.advanceCrash(tick)) {
            manager.removeBet(key);
            return new Result(Status.BUSTED, 0, 0, "crash");
        }
        manager.setDirty();
        if (session.atLimit()) return cashOut(key, session.wallet(), name, "multiplier");
        return new Result(Status.OK, 0, session.multiplier(), "");
    }

    public Result playInstant(UUID player, UUID wallet, String name, long stake, String game, String choice) {
        return instant(player, wallet, name, stake, game, choice, true);
    }

    private Result instant(UUID player, UUID wallet, String name, long stake, String game, String choice, boolean settle) {
        if (!progression.gameAccess(manager, wallet, game).unlocked()) return fail(Status.LEVEL_LOCKED);
        if (!enabled(game)) return fail(Status.DISABLED);
        // Invalid/disabled games never take a stake.
        switch (game) {
            case "slots" -> { if (!slots.ready()) return fail(Status.DISABLED); }
            case "scratch" -> { if (!scratch.ready()) return fail(Status.DISABLED); }
            case "hilo" -> {
                if (!"high".equals(choice) && !"low".equals(choice)) return fail(Status.BAD_CHOICE);
                if (hiLoPayout() / 2 > expectedCap() + 1e-9) return fail(Status.DISABLED);
            }
            case "roulette" -> {
                if (!RouletteGame.validChoice(choice)) return fail(Status.BAD_CHOICE);
                if (odds(game).maxReturn() > expectedCap() + 1e-9) return fail(Status.DISABLED);
            }
            case "color_wheel" -> { if (odds(game).maxReturn() > expectedCap() + 1e-9) return fail(Status.DISABLED); }
            default -> { return fail(Status.BAD_CHOICE); }
        }
        Status status = checkBet(wallet, stake, game);
        if (status != Status.OK) return fail(status);
        deduct(wallet, player, name, stake, game);
        long payout;
        double multiplier;
        String extra;
        switch (game) {
            case "slots" -> {
                SlotsGame.SpinResult spin = slots.spin(random, stake);
                multiplier = slots.payoutFor(spin.symbols().toArray(String[]::new)) * progression.profile(game).payoutScale();
                payout = MoneyMath.payout(stake, multiplier);
                extra = String.join(",", spin.symbols());
            }
            case "scratch" -> {
                ScratchPools.Entry entry = scratch.draw(random);
                multiplier = entry.payoutMultiplier();
                payout = MoneyMath.payout(stake, multiplier);
                extra = entry.label() + "," + multiplier;
            }
            case "hilo" -> {
                int roll = random.nextInt(HiLoRules.SIDES) + 1;
                boolean won = HiLoRules.wins(roll, choice);
                multiplier = won ? hiLoPayout() : 0;
                payout = MoneyMath.payout(stake, multiplier);
                extra = String.valueOf(roll);
            }
            case "color_wheel" -> {
                int pocket = random.nextInt(ColorWheelGame.POCKETS);
                var color = ColorWheelGame.pocket(pocket);
                multiplier = color.multiplier() * progression.profile(game).payoutScale();
                payout = MoneyMath.payout(stake, multiplier);
                extra = pocket + "," + color.id() + "," + multiplier;
            }
            default -> {
                int roll = random.nextInt(RouletteGame.POCKETS);
                multiplier = RouletteGame.multiplier(choice, roll) * progression.profile(game).payoutScale();
                payout = MoneyMath.payout(stake, multiplier);
                extra = String.valueOf(roll);
            }
        }
        long credited = settle ? award(wallet, player, name, payout, game) : payout;
        return new Result(Status.OK, credited, multiplier, extra);
    }

    /** Reserves funds now, then exposes the result and settles only when the world animation finishes. */
    public PendingMachineGame beginMachine(net.minecraft.server.level.ServerPlayer player, net.minecraft.core.BlockPos pos,
            String game, String choice, long stake, boolean cashOut, int duration) {
        UUID id = player.getUUID();
        UUID wallet = com.evolt.teamecon.team.TeamUtil.walletKey(player.getServer(), id);
        String dimension = player.level().dimension().location().toString();
        SessionKey key = SessionKey.machine(id, dimension, pos);
        if (manager.machineBusy(dimension, pos)) return null;
        if(manager.activeBets().entrySet().stream().anyMatch(e->!e.getKey().equals(key)&&e.getValue().atMachine(dimension,pos)))return null;
        BetSession existing = sessionOf(key);
        if (existing != null && (!game.equals(existing.game()) || !game.equals("penguin"))) return null;
        if (!cashOut && (!progression.gameAccess(manager, wallet, game).unlocked() || !enabled(game))) return null;
        PendingMachineGame.Kind kind;
        Result result;
        RiskTier tier = null;
        int round = -1;
        if (cashOut) {
            if(game.equals("multiplier"))return null;
            BetSession session = sessionOf(key);
            if (session == null || !session.game().equals(game)) return null;
            if(session.worldBound()&&!session.atMachine(dimension,pos))return null;
            wallet = session.wallet(); stake = session.stake();
            long payout = session.close(); manager.removeBet(key);
            result = new Result(Status.CASHED_OUT, payout, session.multiplier(), "");
            kind = PendingMachineGame.Kind.CASH_OUT;
        } else if (game.equals("multiplier")) {
            return null; // Live crash rounds have no animation-delayed settlement.
        } else if (game.equals("penguin")) {
            if (!choice.equals("jump")) return null;
            BetSession session = sessionOf(key);
            if (session == null) {
                if (!startBet(key, wallet, player.getName().getString(), stake, game).accepted()) return null;
                session = sessionOf(key);
            }
            if (!session.game().equals(game) || !session.wallet().equals(wallet) || session.atLimit()) return null;
            if(session.worldBound()&&!session.atMachine(dimension,pos))return null;
            tier = jumpTier(session);
            if (!tier.isValid() || tier.expectedFactor() > expectedCap() + 1e-9) return null;
            session.bindMachine(dimension,pos);manager.setDirty();
            stake = session.stake(); round = session.rounds();
            boolean won = random.nextDouble() < tier.successChance();
            double multiplier = won ? Math.min(session.maxMultiplier(), tier.applySuccess(session.multiplier())) : 0;
            result = new Result(won ? Status.ROUND_WON : Status.BUSTED, 0, multiplier, choice + "," + (round + 1) + "," + (won ? 1 : 0));
            kind = PendingMachineGame.Kind.ROUND;
        } else {
            result = instant(id, wallet, player.getName().getString(), stake, game, choice, false);
            if (!result.accepted()) return null;
            kind = PendingMachineGame.Kind.INSTANT;
        }
        PendingMachineGame pending = new PendingMachineGame(id, wallet, player.getName().getString(), game, dimension, pos,
                player.getServer().overworld().getGameTime() + duration, kind, stake, result.payout(), result.multiplier(),
                result.extra(), result.payout() > 0 || result.status() == Status.ROUND_WON, tier, round);
        manager.putPendingMachine(pending);
        return pending;
    }

    /** Called on the server thread; removing the durable instruction makes completion idempotent. */
    public Result settleMachine(SessionKey key, long now) {
        PendingMachineGame pending = manager.pendingMachine(key);
        if (pending == null || now < pending.dueTick()) return fail(Status.BUSY);
        manager.removePendingMachine(key);
        if (pending.kind() == PendingMachineGame.Kind.ROUND) {
            BetSession session = sessionOf(key);
            if (session == null || session.rounds() != pending.expectedRound() || !session.wallet().equals(pending.wallet())
                    || !session.game().equals(pending.game())) return fail(Status.NO_SESSION);
            if (pending.won()) { session.applySuccess(pending.tier()); manager.setDirty(); }
            else { session.applyFailure(pending.tier()); manager.removeBet(key); }
            return new Result(pending.won() ? Status.ROUND_WON : Status.BUSTED, 0, session.multiplier(), pending.extra());
        }
        long credited = award(pending.wallet(), key.player(), pending.playerName(), pending.payout(), pending.game());
        return new Result(pending.kind() == PendingMachineGame.Kind.CASH_OUT ? Status.CASHED_OUT : Status.OK,
                credited, pending.multiplier(), pending.extra());
    }
}
