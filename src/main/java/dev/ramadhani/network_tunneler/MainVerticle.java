package dev.ramadhani.network_tunneler;

import dev.ramadhani.network_tunneler.dispatcher.WebsocketRequestDispatcher;
import dev.ramadhani.network_tunneler.subscription_registry.WebsocketSubscriptionRegistry;
import dev.ramadhani.network_tunneler.transport.WebsocketNetworkTransport;
import dev.ramadhani.network_tunneler.tunneler.HttpTunneler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpServerRequest;

public class MainVerticle extends VerticleBase {

    @Override
    public Future<?> start() {
        WebsocketSubscriptionRegistry<HttpServerRequest> registry = new WebsocketSubscriptionRegistry<>();
        WebsocketRequestDispatcher<HttpServerRequest> requestDispatcher = new WebsocketRequestDispatcher<>(registry, vertx);
        AbstractVerticle httpTunneler = new HttpTunneler(vertx.createHttpServer(), requestDispatcher);
        return vertx.deployVerticle(httpTunneler);
    }
}
