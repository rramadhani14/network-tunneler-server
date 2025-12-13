package dev.ramadhani.network_tunneler.protocol;


import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class JsonRpcHelper {


    public static JsonObject createTunnelerJsonRpcPayload(String id, String type, String payload, String end, Integer count) {
        List<String> params = new ArrayList<>(2);
        params.add(payload);
        if(end != null && count != null) {
            params.add(end);
            params.add(count.toString());
        }
        return JsonObject.of(
                "jsonrpc", "2.0",
                "id", id,
                "method", type,
                "params", params
        );
    }
}
