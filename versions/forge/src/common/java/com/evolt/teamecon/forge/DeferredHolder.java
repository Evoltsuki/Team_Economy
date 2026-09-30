package com.evolt.teamecon.forge;
import java.util.function.Supplier;
public class DeferredHolder<R,T extends R> implements Supplier<T> {
    private final Supplier<T> entry;
    public DeferredHolder(Supplier<T> entry){this.entry=entry;}
    public T get(){return entry.get();}
    public T value(){return get();}
}
