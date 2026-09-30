package com.evolt.teamecon.forge;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import java.util.function.Supplier;
import java.util.function.Function;

/** Small registration adapter; Forge owns every registry object and its lifecycle. */
public class DeferredRegister<R> {
    protected final net.minecraftforge.registries.DeferredRegister<R> delegate;
    protected DeferredRegister(ResourceKey<? extends Registry<R>> key,String mod){delegate=net.minecraftforge.registries.DeferredRegister.create(key,mod);}
    public static <R> DeferredRegister<R> create(ResourceKey<? extends Registry<R>> key,String mod){return new DeferredRegister<>(key,mod);}
    public <T extends R> DeferredHolder<R,T> register(String name,Supplier<T> factory){return new DeferredHolder<>(delegate.register(name,factory));}
    public void register(IEventBus bus){delegate.register(bus);}
    public static Items createItems(String mod){return new Items(mod);}
    public static Blocks createBlocks(String mod){return new Blocks(mod);}
    public static final class Items extends DeferredRegister<Item> {
        private Items(String mod){super(Registries.ITEM,mod);}
        public <T extends Item> DeferredItem<T> registerItem(String name,Function<Item.Properties,T> factory){return registerItem(name,factory,new Item.Properties());}
        public <T extends Item> DeferredItem<T> registerItem(String name,Function<Item.Properties,T> factory,Item.Properties props){return new DeferredItem<>(delegate.register(name,()->factory.apply(props)));}
        public DeferredItem<BlockItem> registerSimpleBlockItem(String name,Supplier<? extends Block> block){return registerSimpleBlockItem(name,block,new Item.Properties());}
        public DeferredItem<BlockItem> registerSimpleBlockItem(String name,Supplier<? extends Block> block,Item.Properties props){return registerItem(name,p->new BlockItem(block.get(),p),props);}
    }
    public static final class Blocks extends DeferredRegister<Block> {
        private Blocks(String mod){super(Registries.BLOCK,mod);}
        public <T extends Block> DeferredBlock<T> registerBlock(String name,Function<BlockBehaviour.Properties,T> factory,BlockBehaviour.Properties props){return new DeferredBlock<>(delegate.register(name,()->factory.apply(props)));}
    }
}
