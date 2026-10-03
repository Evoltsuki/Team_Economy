package com.evolt.teamecon.client;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/** The player and administrator views use the same configured or translated label. */
public final class BoxNames {
    private BoxNames() {}
    public static Component display(String id,String name){
        if(!name.isEmpty())return Component.literal(name);
        String key="shop.teamecon.pool."+id;
        return I18n.exists(key)?Component.translatable(key):Component.literal(id);
    }
}
