package dev.ramadhani.network_tunneler.dispatcher;

import com.github.benmanes.caffeine.cache.RemovalListener;
import dev.ramadhani.network_tunneler.helper.TriFunction;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.streams.WriteStream;

import java.util.function.BiConsumer;

public interface RequestDispatcher<T, U> {
    default void registerHandlers(T server, TriFunction<U, WriteStream<Buffer>, Handler<Void>, Runnable> streamingRequestSerializer, BiConsumer<U, String> responseHandler, RemovalListener<String, U> removalListener) {
        throw new RuntimeException("Not implemented");
    }

    void dispatch(String id, String type, U request);
    // TODO: Not sure if needed, must check later
    void processResponse();
}
