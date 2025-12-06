package dev.ramadhani.network_tunneler.subscription_registry;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import dev.ramadhani.network_tunneler.transport.WebsocketNetworkTransport;
import dev.ramadhani.network_tunneler.tunneler.HttpTunneler;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;

@RequiredArgsConstructor
public class WebsocketSubscriptionRegistry<T> implements SubscriptionRegistry<ServerWebSocket, T> {
    private static final Logger logger = LoggerFactory.getLogger(WebsocketSubscriptionRegistry.class);

    private final AsyncCache<String, WebsocketNetworkTransport<T>> registry = Caffeine.newBuilder()
                .expireAfterAccess(5,TimeUnit.MINUTES)
                .maximumSize(500)
                .buildAsync();

    @Override
    public WebsocketNetworkTransport<T> register(String id, ServerWebSocket subscriptionRequest, Function<T, Future<String>> requestSerializer, BiConsumer<T, JsonObject> responseHandler, RemovalListener<String, T> removalListener, Vertx vertx) {
        WebsocketNetworkTransport<T> networkTransport = new WebsocketNetworkTransport<>(subscriptionRequest, vertx);
        networkTransport.registerTransport(requestSerializer, responseHandler, removalListener);
        registry.put(id, CompletableFuture.completedFuture(networkTransport));
        return networkTransport;
    }

    @Override
    public WebsocketNetworkTransport<T> getSubscription(String id) {
        return registry.synchronous().getIfPresent(id);
    }

    //TODO: Provide async API for getSubscription
}
