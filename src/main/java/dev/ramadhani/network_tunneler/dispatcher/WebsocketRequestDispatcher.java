package dev.ramadhani.network_tunneler.dispatcher;

import com.github.benmanes.caffeine.cache.RemovalListener;
import dev.ramadhani.network_tunneler.subscription_registry.WebsocketSubscriptionRegistry;
import dev.ramadhani.network_tunneler.transport.NetworkTransport;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

@Getter
public class WebsocketRequestDispatcher<T> implements RequestDispatcher<HttpServer, T> {
    private static final Logger logger = LoggerFactory.getLogger(WebsocketRequestDispatcher.class);
    private final WebsocketSubscriptionRegistry<T> registry;
    private final Vertx vertx;
    @Setter
    private String SUBSCRIPTION_PATH = "/tunneler/ws";
    private HttpServer httpServer;
    private Function<T, Future<String>> requestSerializer;
    private BiConsumer<T, JsonObject> responseHandler;
    private RemovalListener<String, T> removalListener;

    public WebsocketRequestDispatcher(WebsocketSubscriptionRegistry<T> registry, Vertx vertx) {
        this.registry = registry;
        this.vertx = vertx;
    }

    @Override
    public void registerHandlers(HttpServer server, Function<T, Future<String>> requestSerializer, BiConsumer<T, JsonObject> responseHandler, RemovalListener<String, T> removalListener) {
        this.httpServer = server;
        this.requestSerializer = requestSerializer;
        this.responseHandler = responseHandler;
        this.removalListener = removalListener;
        httpServer.webSocketHandler(this::handleWebsocketConnection);
    }

    @Override
    public void dispatch(String id, String type, T request) {
        registry.getSubscription(id).handleIncomingRequest(type, request);
    }

    @Override
    public void processResponse() {

    }

    private void handleWebsocketConnection(ServerWebSocket serverWebSocket) {
        logger.info("Received subscription request");
        if (serverWebSocket.path().equals(SUBSCRIPTION_PATH)) {
            String id = UUID.randomUUID().toString();
            NetworkTransport<T> networkTransport = registry.register(id, serverWebSocket, requestSerializer, responseHandler, removalListener, vertx);
            networkTransport.handleDispatcherConfiguration("config", JsonObject.of("path", id).toString());
        }
    }
}
