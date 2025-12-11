package dev.ramadhani.network_tunneler;

import dev.ramadhani.network_tunneler.dispatcher.WebsocketRequestDispatcher;
import dev.ramadhani.network_tunneler.subscription_registry.WebsocketSubscriptionRegistry;
import dev.ramadhani.network_tunneler.tunneler.HttpTunneler;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MainVerticle extends VerticleBase {
    private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);
    @Override
    public Future<?> start() {
        ConfigRetriever retriever = ConfigRetriever.create(vertx, new ConfigRetrieverOptions()
            .addStore(new ConfigStoreOptions().setType("env"))
            .addStore(new ConfigStoreOptions()
                .setType("file")
                .setFormat("properties")
                .setConfig(new JsonObject()
                    .put("path", "application.properties"))));
        return retriever.getConfig()
            .onSuccess(config -> {
                WebsocketSubscriptionRegistry<HttpServerRequest> registry = new WebsocketSubscriptionRegistry<>();
                WebsocketRequestDispatcher<HttpServerRequest> requestDispatcher = new WebsocketRequestDispatcher<>(registry, vertx);
                DeploymentOptions deploymentOptions = new DeploymentOptions().setConfig(config);
                AbstractVerticle httpTunneler = new HttpTunneler(vertx.createHttpServer(), requestDispatcher);
                vertx.deployVerticle(httpTunneler, deploymentOptions);
        });

    }
}
