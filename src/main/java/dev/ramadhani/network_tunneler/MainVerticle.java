package dev.ramadhani.network_tunneler;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.ext.web.Router;

public class MainVerticle extends VerticleBase {

  @Override
  public Future<?> start() {
//    EventBus eb = vertx.eventBus();
    vertx.deployVerticle("dev.ramadhani.network_tunneler.LocalTesterVerticle");
    return vertx.deployVerticle("dev.ramadhani.network_tunneler.WsTunneler");
  }
}
