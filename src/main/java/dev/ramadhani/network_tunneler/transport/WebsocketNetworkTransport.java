package dev.ramadhani.network_tunneler.transport;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import dev.ramadhani.network_tunneler.protocol.JsonRpcHelper;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;


@NoArgsConstructor
@Getter
@Setter
public class WebsocketNetworkTransport<T> implements NetworkTransport<T> {
  Logger logger = LoggerFactory.getLogger(WebsocketNetworkTransport.class);

  private ServerWebSocket serverWebSocket;
  private AsyncCache<String, T> requests;
  private Vertx vertx;
  private BiConsumer<T, JsonObject> channelProcessSubscriberResponse;
  private Function<T, Future<String>> requestSerializer;



  @Override
  public void registerTransport(HttpServer httpServer, Function<T, Future<String>> requestSerializer, BiConsumer<T, JsonObject> channelProcessSubscriberResponse, RemovalListener<String, T> removalListener, Vertx vertx) {
    httpServer.webSocketHandler(this::handleWebsocketConnection);
    this.channelProcessSubscriberResponse = channelProcessSubscriberResponse;
    this.requests = Caffeine.newBuilder()
      .evictionListener(removalListener)
      .expireAfterWrite(5, TimeUnit.MINUTES)
      .maximumSize(50)
      .buildAsync();
    this.requestSerializer = requestSerializer;
    this.vertx = vertx;
  }

  @Override
  public void handleIncomingRequest(String type, T req) {
    if (this.serverWebSocket != null && !this.serverWebSocket.isClosed()) {
      this.requestSerializer.apply(req).onSuccess(serializedRequest -> {
        String id = UUID.randomUUID().toString();
        JsonObject jsonRpcPayload = JsonRpcHelper.createTunnelerJsonRpcPayload(id, type, serializedRequest);
        this.serverWebSocket.write(jsonRpcPayload.toBuffer());
        logger.info("Putting in cache id: " + id);
        this.requests.put(id, CompletableFuture.completedFuture(req));
      });
    } else {
      throw new RuntimeException("No subscription registered/subscription connection closed");
    }
  }


  private void handleWebsocketConnection(ServerWebSocket serverWebSocket) {
    logger.info("Received subscription request");
    if (!serverWebSocket.path().equals("/ws")) {
      serverWebSocket.close((short) 400, "Websocket endpoint not supported!");
      return;
    }
    // Client start subscription
    handleWebsocketSubscription(serverWebSocket);
  }

  private void handleWebsocketSubscription(ServerWebSocket serverWebSocket) {
    this.serverWebSocket = serverWebSocket;
    vertx.setPeriodic(5000, l -> {
      if (this.serverWebSocket != null && !this.serverWebSocket.isClosed()) {
        serverWebSocket.writePing(Buffer.buffer("ping"));
      } else {
        this.serverWebSocket = null;
      }
    });
    serverWebSocket.handler(this::processSubscriberResponse);
    serverWebSocket.pongHandler(pong -> logger.info("Pong received"));
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
      logger.info("Response id: " + id);
      CompletableFuture<T> reqFuture = requests.getIfPresent(id);
      logger.info("req future : " + reqFuture);
      if (reqFuture != null) {
        reqFuture.thenAccept(
          req -> {
            logger.info("Request received: " + req.toString());
            try {
              JsonObject payload = jsonRpcResponse.getJsonObject("result");
              if (payload == null) {
                payload = jsonRpcResponse.getJsonObject("error");
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
