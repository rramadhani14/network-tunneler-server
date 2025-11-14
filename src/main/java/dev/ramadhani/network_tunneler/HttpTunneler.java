package dev.ramadhani.network_tunneler;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.AbstractVerticle;
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

public class HttpTunneler extends AbstractVerticle {
  Logger logger = LoggerFactory.getLogger(HttpTunneler.class);
  ServerWebSocket serverWebSocket;
  AsyncCache<String, HttpServerRequest> serverRequestCaffeine = Caffeine.newBuilder()
    .maximumSize(50)
    .evictionListener((k, r, cause) -> {
      if (r != null) {
        HttpServerRequest req = (HttpServerRequest) r;
        req.response()
          .putHeader("content-type", "application/json")
          .putHeader("content-type", "application/json")
          .end(JsonObject.of("message", "Request evicted").toBuffer());
      }
    })
    .expireAfterWrite(5, TimeUnit.MINUTES)
    .buildAsync();
  NetworkTransport networkTransport;


  @Override
  public void start() {
    this.networkTransport = (NetworkTransport) config().getValue("network-transport");
    HttpServer server = vertx.createHttpServer();
    server
      .requestHandler(this::handleIncomingHttpRequest)
      .webSocketHandler(this::handleWebsocketConnection)
      .listen(8888).onSuccess(http -> System.out.println("HTTP server started on port 8888"));
  }

  private void handleIncomingHttpRequest(HttpServerRequest req) {
    logger.info("Received http request");
    // Subscription exists
    if (this.serverWebSocket != null && !this.serverWebSocket.isClosed()) {
      req.body()
        .onSuccess(body -> {
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
        });
    }
    // Subscription doesn't exists
    else {
      req.response()
        .setStatusCode(400)
        .putHeader("content-type", "application/json")
        .end(JsonObject.of("message", "No subscription yet.").toBuffer());
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
