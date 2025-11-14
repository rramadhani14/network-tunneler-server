package dev.ramadhani.network_tunneler;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;

import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class WsNetworkTransport implements NetworkTransport {
  Logger logger = LoggerFactory.getLogger(WsNetworkTransport.class);

  ServerWebSocket serverWebSocket;
  AsyncCache<String, HttpServerRequest> serverRequestCaffeine;
  Vertx vertx;

  public WsNetworkTransport(
    HttpServer httpServer,
    RemovalListener<String, HttpServerRequest> removalListener,
    Vertx vertx
  ) {
    this.serverRequestCaffeine = Caffeine.newBuilder()
      .evictionListener(removalListener)
      .expireAfterWrite(5, TimeUnit.MINUTES)
      .maximumSize(50)
      .buildAsync();
    httpServer.webSocketHandler(this::handleWebsocketConnection);
  }

  @Override
  public void handleIncomingHttpRequest(HttpServerRequest req) {
    if (this.serverWebSocket != null && !this.serverWebSocket.isClosed()) {
      String id = UUID.randomUUID().toString();
      this.serverWebSocket.write(
        JsonObject.of(
          "jsonrpc", "2.0",
          "id", id,
          "method", "http",
          "params", List.of(req.headers().entries().stream().map(it -> it.getKey() + ": " + it.getValue()).collect(Collectors.joining("\n")), body.toString(Charset.defaultCharset())
          )).toBuffer()
      );
      logger.info("Putting in cache id: " + id);
      this.serverRequestCaffeine.put(id, CompletableFuture.completedFuture(req));
    } else {
      throw new RuntimeException("No subscription registered/subscription connection closed");
    }
  }


  @Override
  public void handleSubscriberResponse(Buffer buffer) {

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
      CompletableFuture<HttpServerRequest> reqFuture = serverRequestCaffeine.getIfPresent(id);
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
                Integer status = payload.getInteger("status");
                String headers = payload.getString("headers");
                String body = payload.getString("body");
                Map<String, String> headersMap = new HashMap<>();
                if(headers != null) {
                  headersMap = Arrays.stream(headers.split("\n")).map(item -> item.split(": ")).collect(Collectors.toMap(it -> it[0], it -> it[1]));
                }
                logger.info("Sending response back to subscriber");
                HttpServerResponse res = req.response();
                res.setStatusCode(status);
                res.headers().setAll(headersMap);
                res.end(body);
                serverRequestCaffeine
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
