package dev.ramadhani.network_tunneler.dispatcher;

import com.github.benmanes.caffeine.cache.RemovalListener;
import io.vertx.core.Future;

import java.util.function.BiConsumer;
import java.util.function.Function;

public interface RequestDispatcher<T, U> {
    void registerHandlers(T server, Function<U, Future<String>> requestSerializer, BiConsumer<U, String> responseHandler, RemovalListener<String, U> removalListener);
    void dispatch(String id, String type, U request);
    // TODO: Not sure if needed, must check later
    void processResponse();
}
