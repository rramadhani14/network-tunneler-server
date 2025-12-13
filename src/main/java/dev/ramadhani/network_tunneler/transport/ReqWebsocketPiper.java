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

import java.util.ArrayDeque;

@RequiredArgsConstructor
public class ReqWebsocketPiper implements WriteStream<Buffer> {
    private final String id;
    private final ServerWebSocket serverWebSocket;
    private final String type;
    private final Handler<Void> endHandler;

    ArrayDeque<Buffer> buffers = new ArrayDeque<>(5);

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
            buffers.pop().appendBuffer(data);
            JsonObject jsonRpcPayload = JsonRpcHelper.createTunnelerJsonRpcPayload(id, type, buffer.toString(), null);
            serverWebSocket.writeBinaryMessage(jsonRpcPayload.toBuffer());
        }

        return Future.succeededFuture();
    }

    @Override
    public Future<Void> end() {
        Buffer buffer = Buffer.buffer();
        while (!buffers.isEmpty()) {
            buffer.appendBuffer(buffers.poll());
        }
        JsonObject jsonRpcPayload = JsonRpcHelper.createTunnelerJsonRpcPayload(id, type, buffer.toString(), "end");
        serverWebSocket.writeBinaryMessage(jsonRpcPayload.toBuffer());
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
