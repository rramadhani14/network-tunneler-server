package dev.ramadhani.network_tunneler.tunneler;

import dev.ramadhani.network_tunneler.dispatcher.RequestDispatcher;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;

import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.WriteStream;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@NoArgsConstructor
@Getter
public class HttpTunneler extends AbstractVerticle {
    public static final String TYPE = "http";
    private static final Logger logger = LoggerFactory.getLogger(HttpTunneler.class);
    private int port = -1;
    private RequestDispatcher<HttpServer, HttpServerRequest> requestDispatcher;
    private HttpServer server;

    public HttpTunneler(HttpServer httpServer, RequestDispatcher<HttpServer, HttpServerRequest> requestDispatcher) {
        this.server = httpServer;
        this.requestDispatcher = requestDispatcher;
    }

    @Override
    public void start() {
        port = config().getInteger("server.port", 3000);
        server = vertx.createHttpServer();
        this.requestDispatcher.registerHandlers(server, this::streamingRequestSerializer, this::dispatcherResponseHandler, this::removalListener);
        server
                .requestHandler(this::processIncomingHttpRequest)
                .listen(port).onSuccess(http -> logger.info("HTTP server started on port {}", port));
    }

    private void processIncomingHttpRequest(HttpServerRequest req) {
        logger.info("Received http request");
        logger.info("Path: {}", req.path().split("/")[1]);
        this.requestDispatcher.dispatch(req.path().split("/")[1], TYPE, req);
    }



    private void dispatcherResponseHandler(HttpServerRequest req, String payload) {
        // Get status from start line
        List<String> lines = payload.lines().toList();
        String startLine = lines.get(0);
        int status = Integer.parseInt(startLine.split(" ")[1]);

        // Get headers
        int headerSeparator = lines.indexOf("");
        List<String> headers = lines.subList(1, headerSeparator);

        // Get body
        String body = String.join("", lines.subList(headerSeparator + 1, lines.size()));
        Map<String, String> headersMap = headers.stream().map(item -> item.split(":")).collect(Collectors.toMap(it -> it[0].trim(), it -> it[1].trim()));

        logger.info("Sending response back to actual client");
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

    // Not really needed, but would be nice for implementing client if format is the same
//    private Future<String> requestSerializer(HttpServerRequest req) {
//        return req.body()
//                .map(buffer -> {
//                    String methodName = req.method().name();
//                    int slashIndex = req.path().indexOf("/", 1);
//                    String path = slashIndex == -1 ? "/" : req.path().substring(slashIndex);
//                    String httpVersion = req.version().alpnName().toUpperCase();
//                    String headers = req.headers().entries().stream().map(entry -> entry.getKey() + ": " + entry.getValue()).collect(Collectors.joining("\n"));
//                    String body = buffer.toString();
//                    return methodName + " " + path + " " + httpVersion + "\n" + headers + "\n\n" + body + "\r\n";
//                });
//    }

    private Runnable streamingRequestSerializer(HttpServerRequest req, WriteStream<Buffer> s, Handler<Void> endHandler) {
        return () -> {
            AtomicInteger counter = new AtomicInteger(0);
            AtomicInteger offset = new AtomicInteger();
            String methodName = req.method().name();
            int slashIndex = req.path().indexOf("/", 1);
            String path = slashIndex == -1 ? "/" : req.path().substring(slashIndex);
            String httpVersion = req.version().alpnName().toUpperCase();
            String headers = req.headers().entries().stream().map(entry -> entry.getKey() + ": " + entry.getValue()).collect(Collectors.joining("\n"));
            s.write(Buffer.buffer(methodName + " " + path + " " + httpVersion + "\n" + headers + "\n\n"));
            req.handler(buffer -> {
                logger.info("Received partial body: {}", buffer.length());
                counter.incrementAndGet();
                if (s.writeQueueFull()) {
                    req.pause();
                    s.drainHandler(v -> {
                        s.write(buffer);
                        offset.getAndUpdate(i -> i + buffer.length());
                        req.resume();
                    });
                } else {
                    s.write(buffer);
                    offset.getAndUpdate(i -> i + buffer.length());
                }
            });
            req.endHandler((v) -> {
                logger.info("Req handler triggered: {}", counter);
                s.write(Buffer.buffer("\r\n"));
                if(endHandler != null) {
                    endHandler.handle(null);
                }
                logger.info("End streaming request, offset {}", offset.get());
                s.end();
            });

        };
    }
}
