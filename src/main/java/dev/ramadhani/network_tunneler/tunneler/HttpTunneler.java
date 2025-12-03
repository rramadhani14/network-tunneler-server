package dev.ramadhani.network_tunneler.tunneler;

import dev.ramadhani.network_tunneler.transport.NetworkTransport;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.*;
import java.util.stream.Collectors;

@NoArgsConstructor
@Getter
public class HttpTunneler extends AbstractVerticle {
  private final Logger logger = LoggerFactory.getLogger(HttpTunneler.class);
  static final String TYPE = "http";
  private NetworkTransport<HttpServerRequest> networkTransport;
  private HttpServer server;
  private final int port = 3000;

  public HttpTunneler(NetworkTransport<HttpServerRequest> networkTransport) {
    this.networkTransport = networkTransport;
  }

  @Override
  public void start() {
    server = vertx.createHttpServer();
    this.networkTransport.registerTransport(server, this::requestSerializer, this::transportSubscriberResponseHandler, this::removalListener, vertx);
    server
      .requestHandler(this::processIncomingHttpRequest)
      .listen(port).onSuccess(http -> System.out.println("HTTP server started on port " + port));
  }

  private void processIncomingHttpRequest(HttpServerRequest req) {
    logger.info("Received http request");
    this.networkTransport.handleIncomingRequest(TYPE, req);
  }

  private void transportSubscriberResponseHandler(HttpServerRequest req, JsonObject payload) {
      String response = payload.getString("response");
      // Get status from start line
      List<String> lines = response.lines().toList();
      String startLine = lines.get(0);
      int status = Integer.parseInt(startLine.split(" ")[1]);

      // Get headers
      int headerSeparator = lines.indexOf("");
      List<String> headers = lines.subList(1, headerSeparator);

      // Get body
      String body = String.join("", lines.subList(headerSeparator + 1, lines.size()));
      Map<String, String> headersMap = headers.stream().map(item -> item.split(":")).collect(Collectors.toMap(it -> it[0].trim(), it -> it[1].trim()));

      logger.info("Sending response back to subscriber");
      HttpServerResponse res = req.response();
      res.setStatusCode(status);
      res.headers().setAll(headersMap);
      res.end(body);
}
  private void removalListener(String key, HttpServerRequest r, Object cause) {
      if (r != null) {
        r.response()
          .putHeader("content-type", "application/json")
          .end(JsonObject.of("message", "Request evicted").toBuffer());
      }
  }

  private Future<String> requestSerializer(HttpServerRequest req) { // JSON RPC force this to be blocking
    return req.body()
      .map(buffer -> {
        String methodName = req.method().name();
        String path = req.path();
        String httpVersion = req.version().alpnName().toUpperCase();
        String headers = req.headers().entries().stream().map(entry -> entry.getKey() + ": " + entry.getValue()).collect(Collectors.joining("\n"));
        String body = buffer.toString();
        return methodName + " " + path + " " + httpVersion + "\n" + headers + "\n\n" + body;
      });
  }
}
