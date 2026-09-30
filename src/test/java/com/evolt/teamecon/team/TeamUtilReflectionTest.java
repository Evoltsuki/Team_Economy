package com.evolt.teamecon.team;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * FTB Teams is compileOnly, so it is absent from the test runtime classpath. These calls must
 * therefore degrade to the solo wallet instead of throwing NoClassDefFoundError, which is the
 * crash players hit when the modpack does not include FTB Teams.
 */
class TeamUtilReflectionTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void teamsNotLoadedOnBareRuntime() {
        assertFalse(TeamUtil.isTeamsLoaded());
    }

    @Test
    void fallsBackToPlayerWallet() {
        assertEquals(PLAYER, TeamUtil.walletKey(null, PLAYER));
    }

    @Test
    void soloDisplayName() {
        assertEquals(TeamUtil.NO_TEAM_NAMESPACE, TeamUtil.displayName(null, PLAYER));
    }
}