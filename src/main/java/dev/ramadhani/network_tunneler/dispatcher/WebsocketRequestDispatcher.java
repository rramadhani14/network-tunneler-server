package dev.ramadhani.network_tunneler.dispatcher;

import com.github.benmanes.caffeine.cache.RemovalListener;
import dev.ramadhani.network_tunneler.helper.TriFunction;
import dev.ramadhani.network_tunneler.subscription_registry.WebsocketSubscriptionRegistry;
import dev.ramadhani.network_tunneler.transport.NetworkTransport;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.WriteStream;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private TriFunction<T, WriteStream<Buffer>, Handler<Void>, Runnable> streamingRequestSerializer;
    private BiConsumer<T, String> responseHandler;
    private RemovalListener<String, T> removalListener;

    public WebsocketRequestDispatcher(WebsocketSubscriptionRegistry<T> registry, Vertx vertx) {
        this.registry = registry;
        this.vertx = vertx;
    }

    @Override
    public void registerHandlers(HttpServer server, TriFunction<T, WriteStream<Buffer>, Handler<Void>, Runnable> streamingRequestSerializer, BiConsumer<T, String> responseHandler, RemovalListener<String, T> removalListener) {
        this.httpServer = server;
        this.streamingRequestSerializer = streamingRequestSerializer;
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
            NetworkTransport<T> networkTransport = registry.register(id, serverWebSocket, streamingRequestSerializer, responseHandler, removalListener, vertx);
            networkTransport.handleDispatcherConfiguration("config", JsonObject.of("path", id).toString());
        }
    }
}
