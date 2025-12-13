package dev.ramadhani.network_tunneler.transport;

import com.github.benmanes.caffeine.cache.RemovalListener;
import dev.ramadhani.network_tunneler.helper.TriConsumer;
import dev.ramadhani.network_tunneler.helper.TriFunction;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.streams.WriteStream;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Interface that contains needed methods for a class to be able to be used as network transport.
 * A network transport is a class that handle sending tunneling request from a tunneler server to tunneler client
 * and forward response received from tunneler client to the actual client.
 *
 * @param <T>
 */
public interface NetworkTransport<T> {
    /**
     * Method to be called on sending configuration to tunneling client
     * @param type type of the request
     * @param serializedConfig the serialized configuration
     */
    void handleDispatcherConfiguration(String type, String serializedConfig);
    /**
     * Method to be called to handle incoming tunneling request
     *
     * @param type    type of tunneling request
     * @param request the request object
     */
    void handleIncomingRequest(String type, T request);

    /**
     * Method to initialize network transport. Used to register the methods for processing tunneling request
     *
     * @param streamingRequestSerializer                Method called to serialize tunneling request, need to be compatible with client
     * @param channelProcessSubscriberResponse Method to be called to inform tunneler that a client has returned a response for a tunneling request
     * @param removalListener                  Method to be called in if tunneling request is expired
     */
    void registerTransport(TriFunction<T, WriteStream<Buffer>, Handler<Void>, Runnable> streamingRequestSerializer, BiConsumer<T, String> channelProcessSubscriberResponse, RemovalListener<String, T> removalListener);
}
