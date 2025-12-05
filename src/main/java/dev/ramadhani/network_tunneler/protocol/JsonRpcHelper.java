package dev.ramadhani.network_tunneler.protocol;


import io.vertx.core.json.JsonObject;

import java.util.List;

public class JsonRpcHelper {


    public static JsonObject createTunnelerJsonRpcPayload(String id, String type, String serializedRequest) {
        return JsonObject.of(
                "jsonrpc", "2.0",
                "id", id,
                "method", type,
                "params", List.of(serializedRequest)
        );
    }
}
