package dev.ramadhani.network_tunneler.transport;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.ramadhani.network_tunneler.protocol.JsonRpcHelper;
import io.vertx.core.Future;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.*;

@ExtendWith(VertxExtension.class)
class WebsocketNetworkTransportTest {
  WebsocketNetworkTransport<String> transport;

  @Nested
  @DisplayName(("handleIncomingRequest"))
  class TestHandleIncomingRequest {
    ServerWebSocket mockServerWebSocket = mock(ServerWebSocket.class);
    AsyncCache<String, String> mockAsyncCache = spy(Caffeine.newBuilder().buildAsync());
    String testRequestType = "custom";
    String testRequest = "test";
    String testSerializedRequest = "serialized request";
    @BeforeEach
    void setUp() {
      transport = new WebsocketNetworkTransport<>();
      transport.setServerWebSocket(mockServerWebSocket);
      transport.setRequestSerializer(s -> Future.succeededFuture(testSerializedRequest));
      transport.setRequests(mockAsyncCache);

    }
    @Test
    void handleIncomingRequestSucceed(VertxTestContext testContext) {
      // Arrange
      // Act
      transport.handleIncomingRequest(testRequestType, testRequest);
      // Assert
      ArgumentCaptor<String> requestIdCaptor = ArgumentCaptor.forClass(String.class);
      verify(mockAsyncCache, times(1)).put(requestIdCaptor.capture(), any());
      verify(mockServerWebSocket, times(1)).write(JsonRpcHelper.createTunnelerJsonRpcPayload(requestIdCaptor.getValue(), testRequestType, testSerializedRequest).toBuffer());
      testContext.completeNow();
    }
  }

}
