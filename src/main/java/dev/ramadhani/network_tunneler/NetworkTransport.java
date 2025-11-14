package dev.ramadhani.network_tunneler;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.ServerWebSocket;

public interface NetworkTransport {
  void handleIncomingHttpRequest(HttpServerRequest req);
  void handleSubscriberResponse(Buffer buffer);
}
