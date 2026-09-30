package com.evolt.teamecon.forge;
import net.minecraft.world.level.block.Block;
import java.util.function.Supplier;
public final class DeferredBlock<T extends Block> extends DeferredHolder<Block,T> {
    public DeferredBlock(Supplier<T> entry){super(entry);}
}
