package com.evolt.teamecon.forge;
import net.minecraft.world.item.Item;
import java.util.function.Supplier;
public final class DeferredItem<T extends Item> extends DeferredHolder<Item,T> {
    public DeferredItem(Supplier<T> entry){super(entry);}
}
