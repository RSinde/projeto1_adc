package pt.unl.fct.di.adc.firstwebapp.util;

import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.ws.rs.core.Response;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class MessageHelper {
    private static final Gson g = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    // Erro
    public static Response error(String code, String message) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("status", code);
        map.put("data", message);
        return Response.ok(g.toJson(map)).build();
    }

    // Sucesso
    public static Response success(Object data) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", "success");
        map.put("data", data);
        return Response.ok(g.toJson(map)).build();
    }
}