package com.evolt.teamecon.team;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the team that owns a player wallet. FTB Teams is the source of truth:
 * party members share one balance, different parties are independent.
 * <p>
 * FTB Teams is an optional runtime dependency, so every call goes through reflection and no
 * FTB Teams class ever appears in a method signature or field type. A signature mentioning
 * the class would trigger class loading (and NoClassDefFoundError) even when the call is
 * guarded by try/catch. When FTB Teams is missing, or the player has no team yet, the player
 * UUID is used as the wallet key so the mod still works standalone.
 */
public final class TeamUtil {

    public static final String NO_TEAM_NAMESPACE = "solo";

    private static volatile Boolean apiAvailable;
    private static Method apiMethod;
    private static Method isManagerLoadedMethod;
    private static Method getManagerMethod;
    private static Method getPlayerTeamMethod;
    private static Method teamGetIdMethod;
    private static Method teamIsValidMethod;
    private static Method teamGetNameMethod;

    private TeamUtil() {
    }

    /**
     * Resolves the reflected FTB Teams API once. The result is cached for the rest of the
     * session because the mod list cannot change after startup.
     */
    private static boolean resolveApi() {
        Boolean cached = apiAvailable;
        if (cached != null) {
            return cached;
        }
        try {
            Class<?> apiClass = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
            apiMethod = apiClass.getMethod("api");
            Class<?> apiType = apiMethod.getReturnType();
            isManagerLoadedMethod = apiType.getMethod("isManagerLoaded");
            getManagerMethod = apiType.getMethod("getManager");
            Class<?> managerType = getManagerMethod.getReturnType();
            // The similarly named getPlayerTeamForPlayerID always returns the personal
            // record, even inside a party. Resolve its effective team for shared wallets.
            getPlayerTeamMethod = managerType.getMethod("getTeamForPlayerID", UUID.class);
            Class<?> teamClass = Class.forName("dev.ftb.mods.ftbteams.api.Team");
            teamGetIdMethod = teamClass.getMethod("getId");
            teamIsValidMethod = teamClass.getMethod("isValid");
            teamGetNameMethod = teamClass.getMethod("getName");
            apiAvailable = true;
            return true;
        } catch (Throwable t) {
            apiAvailable = false;
            return false;
        }
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (Throwable t) {
            return null;
        }
    }

    /** True when the FTB Teams manager is available on this (server) side. */
    public static boolean isTeamsLoaded() {
        if (!resolveApi()) {
            return false;
        }
        Object api = invoke(apiMethod, null);
        return api != null && Boolean.TRUE.equals(invoke(isManagerLoadedMethod, api));
    }

    /** The FTB Teams team of a player, or empty when FTB Teams is absent or the player is unteamed. */
    public static Optional<Object> teamOf(MinecraftServer server, UUID playerId) {
        if (!isTeamsLoaded()) {
            return Optional.empty();
        }
        Object api = invoke(apiMethod, null);
        Object manager = api == null ? null : invoke(getManagerMethod, api);
        Object optionalTeam = manager == null ? null : invoke(getPlayerTeamMethod, manager, playerId);
        if (!(optionalTeam instanceof Optional<?> optional)) {
            return Optional.empty();
        }
        if (optional.isEmpty()) {
            return Optional.empty();
        }
        Object team = optional.get();
        return Boolean.TRUE.equals(invoke(teamIsValidMethod, team)) ? Optional.of(team) : Optional.empty();
    }

    public static Optional<Object> teamOf(ServerPlayer player) {
        return teamOf(player.getServer(), player.getUUID());
    }

    /** Wallet key for the player: team id when available, otherwise the player id. */
    public static UUID walletKey(MinecraftServer server, UUID playerId) {
        Optional<Object> team = teamOf(server, playerId);
        if (team.isPresent()) {
            Object id = invoke(teamGetIdMethod, team.get());
            if (id instanceof UUID uuid) {
                return uuid;
            }
        }
        return playerId;
    }

    /** Display name used in messages and leaderboards. */
    public static String displayName(MinecraftServer server, UUID playerId) {
        return teamOf(server, playerId)
                .map(team -> {
                    Object name = invoke(teamGetNameMethod, team);
                    return name instanceof Component component ? component.getString() : NO_TEAM_NAMESPACE;
                })
                .orElse(NO_TEAM_NAMESPACE);
    }

    /** Includes offline party members without requiring FTB classes on standalone servers. */
    public static java.util.Set<UUID> members(ServerPlayer player) {
        java.util.Set<UUID> ids=new java.util.HashSet<>();ids.add(player.getUUID());
        teamOf(player).ifPresent(team->{
            try {
                Object value=Class.forName("dev.ftb.mods.ftbteams.api.Team").getMethod("getMembers").invoke(team);
                if(value instanceof java.util.Collection<?> list)for(Object id:list)if(id instanceof UUID uuid)ids.add(uuid);
            }catch(ReflectiveOperationException ignored){}
        });
        UUID wallet=walletKey(player.getServer(),player.getUUID());
        for(ServerPlayer p:player.getServer().getPlayerList().getPlayers())if(walletKey(p.getServer(),p.getUUID()).equals(wallet))ids.add(p.getUUID());
        return ids;
    }

    /** True when both players resolve to the same wallet. */
    public static boolean sameWallet(MinecraftServer server, UUID a, UUID b) {
        return walletKey(server, a).equals(walletKey(server, b));
    }

    private static volatile Boolean stagesAvailable;
    private static Method hasTeamStageMethod;

    /**
     * FTB Teams progression stage check, used to gate shop offers and services behind team
     * milestones. Reflective for the same reason every other FTB Teams call is: the mod is
     * optional at runtime. Explicit stage requirements fail closed when their provider is
     * unavailable; operators may explicitly disable useTeamStages or use advancement rules.
     */
    public static boolean hasStage(MinecraftServer server, UUID playerId, String stage) {
        if (stage == null || stage.isEmpty()) {
            return true;
        }
        if (!isTeamsLoaded()) {
            return false;
        }
        if (stagesAvailable == null) {
            try {
                Class<?> helper = Class.forName("dev.ftb.mods.ftbteams.api.TeamStagesHelper");
                hasTeamStageMethod = helper.getMethod("hasTeamStage", Player.class, String.class);
                stagesAvailable = true;
            } catch (Throwable t) {
                stagesAvailable = false;
            }
        }
        if (!Boolean.TRUE.equals(stagesAvailable) || hasTeamStageMethod == null) {
            return false;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return false;
        }
        Object result = invoke(hasTeamStageMethod, null, player, stage);
        return Boolean.TRUE.equals(result);
    }
}
