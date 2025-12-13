package dev.ramadhani.network_tunneler.helper;


import io.vertx.core.streams.WriteStream;

@FunctionalInterface
public interface TriFunction<T, U, V, W> {
    W apply(T t, U u, V v);
}
