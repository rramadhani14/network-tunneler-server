package dev.ramadhani.network_tunneler;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;

public class LocalTesterVerticle extends AbstractVerticle {
//  Logger logger = LoggerFactory.getLogger(LocalTesterVerticle.class);

//  @Override
//  public void start() {
//    HttpClient httpClient = vertx.createHttpClient();
//    vertx.setTimer(5000L, l -> {
//      vertx.createWebSocketClient().connect(8888, "localhost", "/ws")
//        .onFailure(failure -> {
//          logger.error(failure.getMessage());
//        })
//        .onSuccess(webSocket -> {
//          logger.info("Connected");
//          webSocket.handler(buffer -> {
//            logger.info("Received: " + buffer.toString());
//            JsonObject json = buffer.toJsonObject();
//            String id = json.getString("id");
//            webSocket.write(JsonObject.of("jsonrpc", "2.0", "id", id, "result", JsonObject.of("response", "HTTP/1.1 200 OK\nContent-Type:text/plain\n\nsuccess!"))
//              .toBuffer());
//          });
//          httpClient.request(HttpMethod.GET, 8888, "localhost", "/test")
//            .andThen(req -> logger.info("Sending request"))
//            .compose(HttpClientRequest::send)
//            .compose(HttpClientResponse::body)
//            .onFailure(failure -> logger.error("Failed to send request", failure))
//            .onSuccess(it -> logger.info("Request successful returning: " + it.toString()));
//        });
//    });
//  }
}
