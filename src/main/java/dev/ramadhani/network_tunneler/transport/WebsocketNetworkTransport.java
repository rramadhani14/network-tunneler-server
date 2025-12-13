package dev.ramadhani.network_tunneler.transport;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import dev.ramadhani.network_tunneler.helper.TriFunction;
import dev.ramadhani.network_tunneler.protocol.JsonRpcHelper;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.WriteStream;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

@NoArgsConstructor
@Getter
@Setter
public class WebsocketNetworkTransport<T> implements NetworkTransport<T> {
    private static final Logger logger = LoggerFactory.getLogger(WebsocketNetworkTransport.class);

    private ServerWebSocket serverWebSocket;
    private AsyncCache<String, T> requests;
    private BiConsumer<T, String> channelProcessSubscriberResponse;
    private TriFunction<T, WriteStream<Buffer>, Handler<Void>, Runnable> streamingRequestSerializer;


    public WebsocketNetworkTransport(ServerWebSocket serverWebSocket, Vertx vertx) {
        super();
        this.serverWebSocket = serverWebSocket;
        vertx.setPeriodic(5000, l -> {
            if (this.serverWebSocket != null && !this.serverWebSocket.isClosed()) {
                serverWebSocket.writePing(Buffer.buffer("ping"));
            }
        });
        serverWebSocket.handler(this::processSubscriberResponse);
        serverWebSocket.pongHandler(pong -> logger.info("Pong received"));
    }

    @Override
    public void registerTransport(TriFunction<T, WriteStream<Buffer>, Handler<Void>, Runnable> streamingRequestSerializer, BiConsumer<T, String> channelProcessSubscriberResponse, RemovalListener<String, T> removalListener) {
        this.channelProcessSubscriberResponse = channelProcessSubscriberResponse;
        this.requests = Caffeine.newBuilder()
                .evictionListener(removalListener)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(500)
                .buildAsync();
        this.streamingRequestSerializer = streamingRequestSerializer;
    }

    @Override
    public void handleDispatcherConfiguration(String type, String serializedConfig) {
        if (this.serverWebSocket != null && !this.serverWebSocket.isClosed()) {
                JsonObject jsonRpcPayload = JsonRpcHelper.createTunnelerJsonRpcPayload("0", type, serializedConfig, null, null);
                this.serverWebSocket.write(jsonRpcPayload.toBuffer());
        } else {
            throw new RuntimeException("Subscription connection closed already");
        }
    }

    @Override
    public void handleIncomingRequest(String type, T req) {
        if (this.serverWebSocket != null && !this.serverWebSocket.isClosed()) {
            String id = UUID.randomUUID().toString();
            WriteStream<Buffer> stream = new ReqWebsocketPiper(id, serverWebSocket, type, (v) -> {
                logger.info("Request forwarded");
                logger.info("Putting request in cache id: {}", id);
                this.requests.put(id, CompletableFuture.completedFuture(req));
            });
            this.streamingRequestSerializer.apply(req, stream, endHandler -> {

            }).run();
        } else {
            throw new RuntimeException("Subscription connection closed already");
        }
    }


    private void processSubscriberResponse(Buffer buffer) {
        logger.info("Received response");
        try {
            JsonObject jsonRpcResponse = buffer.toJsonObject();
            String version = jsonRpcResponse.getString("jsonrpc");
            if (!"2.0".equals(version)) {
                throw new IllegalArgumentException("Invalid version: " + version);
            }
            String id = jsonRpcResponse.getString("id");
            logger.info("Response id: {}", id);
            CompletableFuture<T> reqFuture = requests.getIfPresent(id);
            logger.info("req future: {}", reqFuture);
            if (reqFuture != null) {
                reqFuture.thenAccept(
                        req -> {
                            logger.info("Request received: {}", req.toString());
                            try {
                                String payload = jsonRpcResponse.getString("result");
                                if (payload == null) {
                                    JsonObject error = jsonRpcResponse.getJsonObject("error");
                                    logger.error("Error: {}", error);
                                    payload = error.getString("data");
                                }
                                if (payload != null) {
                                    this.channelProcessSubscriberResponse.accept(req, payload);
                                    requests
                                            .synchronous()
                                            .invalidate(id);
                                }
                            } catch (Exception e) {
                                logger.error(e.getMessage());
                            }

                        }
                );
            }
        } catch (Exception e) {
            logger.error("Message cannot be handled!");
            logger.error(e.getMessage());
        }
    }
}
