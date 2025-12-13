package dev.ramadhani.network_tunneler;


import dev.ramadhani.network_tunneler.dispatcher.WebsocketRequestDispatcher;
import dev.ramadhani.network_tunneler.subscription_registry.WebsocketSubscriptionRegistry;
import dev.ramadhani.network_tunneler.tunneler.HttpTunneler;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.multipart.MultipartForm;
import io.vertx.ext.web.multipart.impl.MultipartFormImpl;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        deploymentId = vertx.deployVerticle(httpTunneler, new DeploymentOptions());
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
    public void TunnelingHttpMultipartOverWebsocketSucceed(Vertx vertx, VertxTestContext testContext) throws InterruptedException {
        // Arrange
        int clients = 5;
        int requestsPerClient = 5;
        HttpClient httpClient = vertx.createHttpClient();
        WebClient webClient = WebClient.wrap(httpClient);
        String testId = UUID.randomUUID().toString();
        AtomicInteger succededRequests = new AtomicInteger(0);
        AtomicInteger connectionFailed = new AtomicInteger(0);
        AtomicInteger receiveConfigurationFailed = new AtomicInteger(0);
        AtomicInteger testingTunnelerServerFailed = new AtomicInteger(0);
        AtomicInteger otherErrors = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(clients * requestsPerClient);
        Buffer content = generateRandomBytesBuffer(200000);
        String contentString = content.toString(StandardCharsets.UTF_8);

        // Act
        for (int i = 0; i < clients; i++) {
            vertx.setTimer(((long) (Math.random() * 10) + 5) * 1000, l -> {
                try {
                    WebSocketClientOptions webSocketClientOptions = new WebSocketClientOptions()
                        .setMaxMessageSize(1024 * 1024)
                        .setReusePort(false)
                        .setReceiveBufferSize(1024 * 256);
                    vertx.createWebSocketClient(webSocketClientOptions).connect(3000, "localhost", "/tunneler/ws")
                        .onFailure(throwable -> {
                            logger.error("Failed -> {}", throwable.getMessage(), throwable);
                            connectionFailed.incrementAndGet();
                            for (int j = 0; j < requestsPerClient; j++) {
                                latch.countDown();
                            }
                        })
                        .onSuccess(webSocket -> {
                            Promise<String> promise = Promise.promise();
                            Map<String, Buffer> payloadCollectors = new HashMap<>();
                            Map<String, AtomicInteger> packetCounters = new HashMap<>();
                            webSocket.binaryMessageHandler(buffer -> {
                                logger.info("WS handler received");
                                JsonObject json = buffer.toJsonObject();
                                if (json.getString("method").equals("config")) {
                                    logger.info("Received config");
                                    String path = Buffer.buffer(json.getJsonArray("params").getString(0)).toJsonObject().getString("path");
                                    promise.succeed(path);
                                } else {
                                    logger.info("Received http");
                                    String requestId = json.getString("id");
                                    JsonArray params = json.getJsonArray("params");
                                    String partialPayload = json.getJsonArray("params").getString(0);
                                    logger.info("Partial length: {}", partialPayload.length());
                                    Buffer col = payloadCollectors.getOrDefault(requestId, Buffer.buffer());
                                    col = col.appendString(partialPayload);
                                    AtomicInteger packetCounter = packetCounters.getOrDefault(requestId, new AtomicInteger());
                                    packetCounters.put(requestId, packetCounter);
                                    packetCounter.incrementAndGet();
                                    payloadCollectors.put(requestId, col);
                                    if (params.size() == 3 && params.getString(1).equals("end")) {
                                        logger.info("Received end");
                                        Buffer collectedPayload = payloadCollectors.get(requestId);
                                        logger.info("Sent payload: {}", content.toString().length());
                                        String collectedPayloadsString = collectedPayload.toString();
                                        Pattern p = Pattern.compile("boundary=([^;\\n]+)");
                                        Matcher m = p.matcher(collectedPayloadsString);
                                        m.find();
                                        String boundary = m.group(1);
                                        List<String> body = Arrays.stream(collectedPayloadsString.split(boundary)).collect(Collectors.toList());
                                        String filePart = body.stream().filter(it -> it.contains("filename")).findFirst().get();
                                        String fileBytes = filePart.trim().split("\n\n")[1].trim();
//                                        assertEquals(contentString, fileBytes);
                                        logger.info("Counted packets: {}", packetCounter.get());
                                        logger.info("Sent packets: {}", params.getString(2));
                                        logger.info("Collected payload: {}", collectedPayload.toString().length());
                                        logger.info("File payload: {}", Buffer.buffer(fileBytes).length());
                                        assertTrue(collectedPayload.toString().length() > 200000);
//                                        assertEquals(content.toString(), collectedPayload.toString());
                                        webSocket.write(JsonObject.of("jsonrpc", "2.0", "id", requestId, "result", "HTTP/1.1 200 OK\nContent-Type:text/plain\n\nsuccess!" + testId + "\r\n")
                                            .toBuffer());
                                        payloadCollectors.remove(requestId);
                                        logger.info("WS sent response");
                                    } else {
                                        logger.info("Received partial request");
                                    }
                                }
                            });
                            for (int j = 0; j < requestsPerClient; j++) {
                                MultipartForm form = MultipartForm.create()
                                    .attribute("test", "test-payload")
                                    .textFileUpload("test-" + j, "test-" + j + ".txt", content, "text/plain");
                                promise.future()
                                    .onFailure(throwable -> {
                                        logger.error("Failed 2 -> " + throwable.getMessage(), throwable);
                                        receiveConfigurationFailed.incrementAndGet();
                                        latch.countDown();
                                    })
                                    .onSuccess(path -> {
                                        webClient.post(3000, "localhost", "/" + path + "/test")
                                            .sendMultipartForm(form)
//                                            .sendBuffer(Buffer.buffer("test"))
                                            .onFailure(throwable -> {
                                                logger.error("Failed 3 -> {}", throwable.getMessage(), throwable);
                                                testingTunnelerServerFailed.incrementAndGet();
                                                latch.countDown();
                                            })
                                            .onSuccess(response -> {
                                                succededRequests.incrementAndGet();
                                                latch.countDown();
                                                logger.info(response.toString());
                                                assertEquals("success!" + testId, response.body().toString());
                                            });
                                    });
//                                    .onSuccess(path -> httpClient.request(HttpMethod.POST, 3000, "localhost", "/" + path + "/test")
//                                        .compose(req -> req.send(Buffer.buffer("test")))
//                                        .compose(HttpClientResponse::body)
//                                        .onFailure(throwable -> {
//                                            logger.error("Failed 3 -> {}", throwable.getMessage(), throwable);
//                                            testingTunnelerServerFailed.incrementAndGet();
//                                            latch.countDown();
//                                        })
//                                        .onSuccess(it -> {
//                                            succededRequests.incrementAndGet();
//                                            latch.countDown();
//                                            logger.info(it.toString(StandardCharsets.UTF_8));
//                                            assertEquals("success!" + testId, it.toString());
//                                        }));
                            }
                        });
                } catch (Exception e) {
                    latch.countDown();
                    logger.error("Failed -> " + e.getMessage(), e);
                    otherErrors.incrementAndGet();
                }
            });
        }
        vertx.setPeriodic(5000, l -> {
            logger.info("Total possible requests: {}", clients * requestsPerClient);
            logger.info("Wait for latch: {}", latch);
            logger.info("Succeed: {}", succededRequests.get());
            logger.info("Connection failed: {}", connectionFailed.get());
            logger.info("Configuration not received: {}", receiveConfigurationFailed.get());
            logger.info("Testing tunneler failed: {}", testingTunnelerServerFailed.get());
            logger.info("Other errors: {}", otherErrors.get());
        });
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

//    class RandomReadStream implements ReadStream<Buffer> {
//        private int length;
//        private int currentPosition = 0;
//        private Vertx vertx;
//        private Handler<Buffer> handler;
//        private Handler<Void> endHandler;
//
//        private InboundBuffer<Buffer> queue;
//        RandomReadStream(int length, Vertx vertx) {
//            this.length = length;
//            this.queue = new InboundBuffer<>(vertx.getOrCreateContext());
//        }
//
//        @Override
//        public ReadStream<Buffer> exceptionHandler(@Nullable Handler<Throwable> handler) {
//            queue.exceptionHandler(handler);
//            return this;
//        }
//
//        @Override
//        public ReadStream<Buffer> handler(@Nullable Handler<Buffer> handler) {
//            queue.handler(buffer -> {
//                if(buffer.length() > 0) {
//                    handler.handle(buffer);
//                } else {
//                    endHandler()
//                }
//            });
//            return this;
//        }
//
//        @Override
//        public ReadStream<Buffer> pause() {
//            queue.pause();
//            return this;
//        }
//
//        @Override
//        public ReadStream<Buffer> resume() {
//            queue.resume();
//            return this;
//        }
//
//        @Override
//        public ReadStream<Buffer> fetch(long amount) {
//            queue.fetch(amount);
//            return this;
//        }
//
//        @Override
//        public ReadStream<Buffer> endHandler(@Nullable Handler<Void> endHandler) {
//            this.endHandler = endHandler;
//            return this;
//        }
//    }

    public Buffer generateRandomBytesBuffer(int length) {
        Random random = new Random();
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return Buffer.buffer(bytes);
    }
}
