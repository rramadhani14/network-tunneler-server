package dev.ramadhani.network_tunneler;


import dev.ramadhani.network_tunneler.dispatcher.WebsocketRequestDispatcher;
import dev.ramadhani.network_tunneler.subscription_registry.WebsocketSubscriptionRegistry;
import dev.ramadhani.network_tunneler.tunneler.HttpTunneler;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(VertxExtension.class)
public class TunnelerIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(TunnelerIntegrationTest.class);
    static AbstractVerticle httpTunneler;
    static Future<String> deploymentId;

    @BeforeAll
    static public void setUp(Vertx vertx) {
        WebsocketSubscriptionRegistry<HttpServerRequest> registry = new WebsocketSubscriptionRegistry<>();
        WebsocketRequestDispatcher<HttpServerRequest> requestDispatcher = new WebsocketRequestDispatcher<>(registry, vertx);
        AbstractVerticle httpTunneler = new HttpTunneler(vertx.createHttpServer(), requestDispatcher);
        deploymentId = vertx.deployVerticle(httpTunneler);
        deploymentId.await();
    }

    @Test
    @Timeout(value = 30, timeUnit = TimeUnit.SECONDS)
    public void TunnelingSucceed(Vertx vertx, VertxTestContext testContext) throws InterruptedException {
        int clients = 200;
        int requestsPerClient = 500;
        HttpClient httpClient = vertx.createHttpClient();
        String testId = UUID.randomUUID().toString();
        AtomicInteger counter = new AtomicInteger(0);
        AtomicInteger errorCounter1 = new AtomicInteger(0);
        AtomicInteger errorCounter2 = new AtomicInteger(0);
        AtomicInteger errorCounter3 = new AtomicInteger(0);
        AtomicInteger errorCounter4 = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(clients * requestsPerClient);
        for (int i = 0; i < clients; i++) {
            vertx.setTimer( ((long) (Math.random() * 10) + 1) * 1000, l -> {
                    try {
                    vertx.createWebSocketClient().connect(3000, "localhost", "/tunneler/ws")
                            .onFailure(throwable -> {
                                logger.error("Failed -> " + throwable.getMessage(), throwable);
                                errorCounter1.incrementAndGet();
                                for(int j = 0; j < requestsPerClient; j++) {
                                    latch.countDown();
                                }
//          testContext.failNow(throwable); // Coverage seems to be buggy and need to be retried
                            })
                            .onSuccess(webSocket -> {
                                Promise<String> promise = Promise.promise();
                                webSocket.handler(buffer -> {
                                    logger.info("WS handler received: " + buffer);
                                    JsonObject json = buffer.toJsonObject();
                                    if (json.getString("method").equals("config")) {
                                        String path = Buffer.buffer(json.getJsonArray("params").getString(0)).toJsonObject().getString("path");
                                        promise.succeed(path);
                                    } else {
                                        String requestId = json.getString("id");
                                        webSocket.write(JsonObject.of("jsonrpc", "2.0", "id", requestId, "result", JsonObject.of("response", "HTTP/1.1 200 OK\nContent-Type:text/plain\n\nsuccess!" + testId))
                                                .toBuffer());
                                        logger.info("WS sent response");
                                    }
                                });
                                for (int j = 0; j < requestsPerClient; j++) {
                                    promise.future()
                                            .onFailure(throwable -> {
                                                logger.error("Failed 2 -> " + throwable.getMessage(), throwable);
                                                errorCounter2.incrementAndGet();
                                                latch.countDown();
                                            })
                                            .onSuccess(path -> {
                                                httpClient.request(HttpMethod.GET, 3000, "localhost", path + "/test")
                                                        .compose(HttpClientRequest::send)
                                                        .compose(HttpClientResponse::body)
                                                        .onFailure(throwable -> {
                                                            logger.error("Failed 3 -> " + throwable.getMessage(), throwable);
                                                            errorCounter3.incrementAndGet();
                                                            latch.countDown();
                                                        })
                                                        .onSuccess(it -> {
                                                            counter.incrementAndGet();
                                                            latch.countDown();
                                                            assertEquals("success!" + testId, it.toString());
                                                        });
                                            });
                                }
                            });
            } catch (Exception e) {
                        latch.countDown();
                        logger.error("Failed -> " + e.getMessage(), e);
                        errorCounter4.incrementAndGet();
            }});
        }
        vertx.setPeriodic(3000, l -> {
            logger.info("Total requests: " + (clients * requestsPerClient));
            logger.info("Wait for latch: " + latch);
            logger.info("Succeed: " + counter.get());
            logger.info("Failed1: " + errorCounter1.get());
            logger.info("Failed2: " + errorCounter2.get());
            logger.info("Failed3: " + errorCounter3.get());
            logger.info("Failed4: " + errorCounter4.get());
        });
        latch.await();
        logger.info("Latch: " + latch);
        assertEquals(clients * requestsPerClient, counter.get());
        vertx.undeploy(deploymentId.result());
        vertx.close();
        testContext.completeNow();
    }
}
