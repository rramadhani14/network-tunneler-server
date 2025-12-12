package dev.ramadhani.network_tunneler;


import dev.ramadhani.network_tunneler.dispatcher.WebsocketRequestDispatcher;
import dev.ramadhani.network_tunneler.subscription_registry.WebsocketSubscriptionRegistry;
import dev.ramadhani.network_tunneler.tunneler.HttpTunneler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.impl.AsyncFileImpl;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.ReadStream;
import io.vertx.ext.web.multipart.MultipartForm;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
        httpTunneler = new HttpTunneler(vertx.createHttpServer(), requestDispatcher);
        deploymentId = vertx.deployVerticle(httpTunneler);
        deploymentId.await();
    }

    @Test
    @Timeout(value = 30, timeUnit = TimeUnit.SECONDS)
    public void TunnelingHttpOverWebsocketSucceed(Vertx vertx, VertxTestContext testContext) throws InterruptedException {
        // Arrange
        int clients = 25;
        int requestsPerClient = 100;
        HttpClient httpClient = vertx.createHttpClient();
        String testId = UUID.randomUUID().toString();
        AtomicInteger succededRequests = new AtomicInteger(0);
        AtomicInteger connectionFailed = new AtomicInteger(0);
        AtomicInteger receiveConfigurationFailed = new AtomicInteger(0);
        AtomicInteger testingTunnelerServerFailed = new AtomicInteger(0);
        AtomicInteger otherErrors = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(clients * requestsPerClient);
        // Act
        for (int i = 0; i < clients; i++) {
            vertx.setTimer(((long) (Math.random() * 10) + 1) * 1000, l -> {
                try {
                    vertx.createWebSocketClient().connect(3000, "localhost", "/tunneler/ws")
                            .onFailure(throwable -> {
                                logger.error("Failed -> " + throwable.getMessage(), throwable);
                                connectionFailed.incrementAndGet();
                                for (int j = 0; j < requestsPerClient; j++) {
                                    latch.countDown();
                                }
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
                                        webSocket.write(JsonObject.of("jsonrpc", "2.0", "id", requestId, "result", "HTTP/1.1 200 OK\nContent-Type:text/plain\n\nsuccess!" + testId + "\r\n")
                                                .toBuffer());
                                        logger.info("WS sent response");
                                    }
                                });
                                for (int j = 0; j < requestsPerClient; j++) {
                                    promise.future()
                                            .onFailure(throwable -> {
                                                logger.error("Failed 2 -> " + throwable.getMessage(), throwable);
                                                receiveConfigurationFailed.incrementAndGet();
                                                latch.countDown();
                                            })
                                            .onSuccess(path -> httpClient.request(HttpMethod.GET, 3000, "localhost", "/" + path + "/test")
                                                    .compose(HttpClientRequest::send)
                                                    .compose(HttpClientResponse::body)
                                                    .onFailure(throwable -> {
                                                        logger.error("Failed 3 -> " + throwable.getMessage(), throwable);
                                                        testingTunnelerServerFailed.incrementAndGet();
                                                        latch.countDown();
                                                    })
                                                    .onSuccess(it -> {
                                                        succededRequests.incrementAndGet();
                                                        latch.countDown();
                                                        logger.info(it.toString(StandardCharsets.UTF_8));
                                                        assertEquals("success!" + testId, it.toString());
                                                    }));
                                }
                            });
                } catch (Exception e) {
                    latch.countDown();
                    logger.error("Failed -> " + e.getMessage(), e);
                    otherErrors.incrementAndGet();
                }
            });
        }
        latch.await();
        // Assert
        logger.info("Total possible requests: {}", clients * requestsPerClient);
        logger.info("Wait for latch: {}", latch);
        logger.info("Succeed: {}", succededRequests.get());
        logger.info("Connection failed: {}", connectionFailed.get());
        logger.info("Configuration not received: {}", receiveConfigurationFailed.get());
        logger.info("Testing tunneler failed: {}", testingTunnelerServerFailed.get());
        logger.info("Other errors: {}", otherErrors.get());
        int clientErrorsPossibleRequests = connectionFailed.get() * requestsPerClient;
        int totalPossibleRequests = (succededRequests.get() + testingTunnelerServerFailed.get() + clientErrorsPossibleRequests);
        assertEquals(clients * requestsPerClient, totalPossibleRequests);
        assertEquals(0, receiveConfigurationFailed.get());
        assertEquals(0, otherErrors.get());
        testContext.completeNow();
    }

    @Test
    @Timeout(value = 30, timeUnit = TimeUnit.SECONDS)
    public void TunnelingHttpMultipartOverWebsocketSucceed(Vertx vertx, VertxTestContext testContext) throws Exception {
        // Arrange
        int clients = 25;
        int requestsPerClient = 100;
        HttpClient httpClient = vertx.createHttpClient();
        String testId = UUID.randomUUID().toString();
        AtomicInteger succededRequests = new AtomicInteger(0);
        AtomicInteger connectionFailed = new AtomicInteger(0);
        AtomicInteger receiveConfigurationFailed = new AtomicInteger(0);
        AtomicInteger testingTunnelerServerFailed = new AtomicInteger(0);
        AtomicInteger otherErrors = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(clients * requestsPerClient);
        // Act
        for (int i = 0; i < clients; i++) {
            vertx.setTimer(((long) (Math.random() * 10) + 1) * 1000, l -> {
                try {
                    vertx.createWebSocketClient().connect(3000, "localhost", "/tunneler/ws")
                        .onFailure(throwable -> {
                            logger.error("Failed -> " + throwable.getMessage(), throwable);
                            connectionFailed.incrementAndGet();
                            for (int j = 0; j < requestsPerClient; j++) {
                                latch.countDown();
                            }
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
                                    webSocket.write(JsonObject.of("jsonrpc", "2.0", "id", requestId, "result", "HTTP/1.1 200 OK\nContent-Type:text/plain\n\nsuccess!" + testId + "\r\n")
                                        .toBuffer());
                                    logger.info("WS sent response");
                                }
                            });
                            for (int j = 0; j < requestsPerClient; j++) {
                                String content = generateRandomString(5000000);
                                MultipartForm form = MultipartForm.create()
                                        .attribute("test", "test-payload")
                                            .binaryFileUpload("test-" + j, "test-" + j + ".txt", content, "text/plain");
//                                httpClient.request(new RequestOptions().)
                                promise.future()
                                    .onFailure(throwable -> {
                                        logger.error("Failed 2 -> " + throwable.getMessage(), throwable);
                                        receiveConfigurationFailed.incrementAndGet();
                                        latch.countDown();
                                    })
                                    .onSuccess(path -> httpClient.request(HttpMethod.POST, 3000, "localhost", "/" + path + "/test")
                                        .compose(req -> req.send(content))
                                        .compose(HttpClientResponse::body)
                                        .onFailure(throwable -> {
                                            logger.error("Failed 3 -> " + throwable.getMessage(), throwable);
                                            testingTunnelerServerFailed.incrementAndGet();
                                            latch.countDown();
                                        })
                                        .onSuccess(it -> {
                                            succededRequests.incrementAndGet();
                                            latch.countDown();
                                            logger.info(it.toString(StandardCharsets.UTF_8));
                                            assertEquals("success!" + testId, it.toString());
                                        }));
                            }
                        });
                } catch (Exception e) {
                    latch.countDown();
                    logger.error("Failed -> " + e.getMessage(), e);
                    otherErrors.incrementAndGet();
                }
            });
        }
        latch.await();
        // Assert
        logger.info("Total possible requests: {}", clients * requestsPerClient);
        logger.info("Wait for latch: {}", latch);
        logger.info("Succeed: {}", succededRequests.get());
        logger.info("Connection failed: {}", connectionFailed.get());
        logger.info("Configuration not received: {}", receiveConfigurationFailed.get());
        logger.info("Testing tunneler failed: {}", testingTunnelerServerFailed.get());
        logger.info("Other errors: {}", otherErrors.get());
        int clientErrorsPossibleRequests = connectionFailed.get() * requestsPerClient;
        int totalPossibleRequests = (succededRequests.get() + testingTunnelerServerFailed.get() + clientErrorsPossibleRequests);
        assertEquals(clients * requestsPerClient, totalPossibleRequests);
        assertEquals(0, receiveConfigurationFailed.get());
        assertEquals(0, otherErrors.get());
        testContext.completeNow();
    }

    public String generateRandomString(int length) {
        Random random = new Random();
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
