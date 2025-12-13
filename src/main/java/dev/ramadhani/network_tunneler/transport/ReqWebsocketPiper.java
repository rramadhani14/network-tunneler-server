package dev.ramadhani.network_tunneler.transport;

import dev.ramadhani.network_tunneler.protocol.JsonRpcHelper;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.WriteStream;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
public class ReqWebsocketPiper implements WriteStream<Buffer> {
    private final static Logger logger = LoggerFactory.getLogger(ReqWebsocketPiper.class);
    private final String id;
    private final ServerWebSocket serverWebSocket;
    private final String type;
    private final Handler<Void> endHandler;
    private final AtomicInteger offset = new AtomicInteger(0);
    private final AtomicInteger writes = new AtomicInteger(0);
    private final ArrayDeque<Buffer> buffers = new ArrayDeque<>(8);

    @Override
    public WriteStream<Buffer> exceptionHandler(@Nullable Handler<Throwable> handler) {
        serverWebSocket.exceptionHandler(handler);
        return this;
    }

    @Override
    public Future<Void> write(Buffer data) {
        if (buffers.size() < 5) {
            buffers.add(data);
        } else {
            Buffer buffer = buffers.poll();
            for (int i = 0; i < 4; i++) {
                buffer.appendBuffer(buffers.poll());
            }
            buffer.appendBuffer(data);
            offset.getAndUpdate(i -> i + buffer.length());
            logger.info("Buffer size {}", buffer.length());
            JsonObject jsonRpcPayload = JsonRpcHelper.createTunnelerJsonRpcPayload(id, type, buffer.toString(StandardCharsets.UTF_8), null, null);
            serverWebSocket.writeBinaryMessage(jsonRpcPayload.toBuffer());
            writes.incrementAndGet();
        }

        return Future.succeededFuture();
    }

    @Override
    public Future<Void> end() {
        Buffer buffer = Buffer.buffer();
        while (!buffers.isEmpty()) {
            buffer.appendBuffer(buffers.poll());
        }
        offset.getAndUpdate(i -> i + buffer.length());
        JsonObject jsonRpcPayload = JsonRpcHelper.createTunnelerJsonRpcPayload(id, type, buffer.toString(StandardCharsets.UTF_8), "end", writes.incrementAndGet());
        serverWebSocket.writeBinaryMessage(jsonRpcPayload.toBuffer());
        logger.info("Pipe transferred {}", offset.get());
        if(endHandler != null) {
            endHandler.handle(null);
        }
        return Future.succeededFuture();
    }

    @Override
    public WriteStream<Buffer> setWriteQueueMaxSize(int maxSize) {
        serverWebSocket.setWriteQueueMaxSize(maxSize);
        return null;
    }

    @Override
    public boolean writeQueueFull() {
        return serverWebSocket.writeQueueFull();
    }

    @Override
    public WriteStream<Buffer> drainHandler(@Nullable Handler<Void> handler) {
        serverWebSocket.drainHandler(handler);
        return this;
    }

}
