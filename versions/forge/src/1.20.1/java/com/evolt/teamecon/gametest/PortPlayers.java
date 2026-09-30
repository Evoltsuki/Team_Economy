package com.evolt.teamecon.gametest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
final class PortPlayers {
    static ServerPlayer create(GameTestHelper h,String name){return FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),name));}
}
