package com.evolt.teamecon.forge;
import java.util.function.BiConsumer;
import java.util.function.Function;
public record StreamCodec<B,T>(BiConsumer<B,T> writer, Function<B,T> reader) {
    public static <B,T> StreamCodec<B,T> of(BiConsumer<B,T> writer, Function<B,T> reader) { return new StreamCodec<>(writer,reader); }
    public void encode(B buffer,T message) { writer.accept(buffer,message); }
    public T decode(B buffer) { return reader.apply(buffer); }
}
