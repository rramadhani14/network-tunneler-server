package dev.ramadhani.network_tunneler.subscription_registry;

import com.github.benmanes.caffeine.cache.RemovalListener;
import dev.ramadhani.network_tunneler.helper.TriFunction;
import dev.ramadhani.network_tunneler.transport.NetworkTransport;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.WriteStream;

import java.util.function.BiConsumer;

public interface SubscriptionRegistry<T, U> {
    NetworkTransport<U> register(String id, T subscriptionRequest, TriFunction<U, WriteStream<Buffer>, Handler<Void>, Runnable> streamingRequestSerializer, BiConsumer<U, String> responseHandler, RemovalListener<String, U> removalListener, Vertx vertx);
    NetworkTransport<U> getSubscription(String id);
}
