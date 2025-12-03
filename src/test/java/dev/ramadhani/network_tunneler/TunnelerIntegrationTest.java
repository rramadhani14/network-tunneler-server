package dev.ramadhani.network_tunneler;


import dev.ramadhani.network_tunneler.transport.WebsocketNetworkTransport;
import dev.ramadhani.network_tunneler.tunneler.HttpTunneler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;


import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(VertxExtension.class)
public class TunnelerIntegrationTest {
  private static final Logger logger = LoggerFactory.getLogger(TunnelerIntegrationTest.class);
  static AbstractVerticle httpTunneler;
  static Future<String> deploymentId;
  @BeforeAll
  static public void setUp(Vertx vertx) {
    WebsocketNetworkTransport<HttpServerRequest> ws = new WebsocketNetworkTransport<>();
    httpTunneler = new HttpTunneler(ws);
    deploymentId = vertx.deployVerticle(httpTunneler);
    deploymentId.await();
  }

  @Test
  @Timeout(value = 10, timeUnit =  TimeUnit.SECONDS)
  public void TunnelingSucceed(Vertx vertx, VertxTestContext testContext) {
    HttpClient httpClient = vertx.createHttpClient();
    vertx.setPeriodic(1000L, l -> vertx.createWebSocketClient().connect(3000, "localhost", "/ws")
      .onFailure(throwable -> {
        logger.error("Failed " + throwable.getMessage(), throwable);
//          testContext.failNow(throwable); // Coverage seems to be buggy and need to be retried
      })
      .onSuccess(webSocket -> {
        webSocket.handler(buffer -> {
          logger.info("WS handler received: " + buffer);
          JsonObject json = buffer.toJsonObject();
          String id = json.getString("id");
          webSocket.write(JsonObject.of("jsonrpc", "2.0", "id", id, "result", JsonObject.of("response", "HTTP/1.1 200 OK\nContent-Type:text/plain\n\nsuccess!"))
            .toBuffer());
          logger.info("WS sent response");
        });
        httpClient.request(HttpMethod.GET, 3000, "localhost", "/test")
          .compose(HttpClientRequest::send)
          .compose(HttpClientResponse::body)
          .onFailure(testContext::failNow)
          .onSuccess(it -> {
            assertEquals("success!", it.toString());
            vertx.undeploy(deploymentId.result());
            vertx.close();
            testContext.completeNow();
          });
      }));
  }
}
