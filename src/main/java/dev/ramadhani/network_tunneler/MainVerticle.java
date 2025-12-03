package dev.ramadhani.network_tunneler;

import dev.ramadhani.network_tunneler.transport.WebsocketNetworkTransport;
import dev.ramadhani.network_tunneler.tunneler.HttpTunneler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpServerRequest;

public class MainVerticle extends VerticleBase {

  @Override
  public Future<?> start() {
    WebsocketNetworkTransport<HttpServerRequest> ws = new WebsocketNetworkTransport<>();
    AbstractVerticle httpTunneler = new HttpTunneler(ws);
    return vertx.deployVerticle(httpTunneler);
  }
}
