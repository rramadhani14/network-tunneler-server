package dev.ramadhani.network_tunneler.subscription_registry;

import com.github.benmanes.caffeine.cache.RemovalListener;
import dev.ramadhani.network_tunneler.transport.NetworkTransport;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import java.util.function.BiConsumer;
import java.util.function.Function;

public interface SubscriptionRegistry<T, U> {
    NetworkTransport<U> register(String id, T subscriptionRequest, Function<U, Future<String>> requestSerializer, BiConsumer<U, JsonObject> responseHandler, RemovalListener<String, U> removalListener, Vertx vertx);
    NetworkTransport<U> getSubscription(String id);
}
