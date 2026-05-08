package burpbridge;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.proxy.ProxyHttpRequestResponse;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;

import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Handlers {

    private final MontoyaApi api;
    private static final Gson GSON = new Gson();

    public Handlers(MontoyaApi api) {
        this.api = api;
    }

    public void health(HttpExchange ex) {
        if (!"GET".equals(ex.getRequestMethod())) {
            ApiServer.respond(ex, 405, "{\"error\":\"method\"}");
            return;
        }
        ApiServer.respond(ex, 200, "{\"ok\":true}");
    }

    public void history(HttpExchange ex) {
        if (!"GET".equals(ex.getRequestMethod())) {
            ApiServer.respond(ex, 405, "{\"error\":\"method\"}");
            return;
        }

        Map<String, String> params = parseQuery(ex.getRequestURI().getRawQuery());
        String hostFilter = params.get("host");
        String methodFilter = params.get("method");
        String statusParam = params.get("status");
        Integer statusFilter = statusParam != null ? Integer.parseInt(statusParam) : null;
        int limit = Integer.parseInt(params.getOrDefault("limit", "1000"));
        boolean full = "true".equals(params.get("full"));

        List<ProxyHttpRequestResponse> all = api.proxy().history();
        List<Map<String, Object>> items = new ArrayList<>();

        for (ProxyHttpRequestResponse entry : all) {
            HttpRequest req = entry.finalRequest();
            HttpResponse resp = entry.response();

            if (hostFilter != null && !req.httpService().host().contains(hostFilter)) continue;
            if (methodFilter != null && !req.method().equalsIgnoreCase(methodFilter)) continue;
            if (statusFilter != null) {
                if (resp == null) continue;
                if (resp.statusCode() != statusFilter) continue;
            }

            Map<String, Object> item = new HashMap<>();
            item.put("index", all.indexOf(entry));
            item.put("url", req.url());
            item.put("method", req.method());
            item.put("host", req.httpService().host());
            item.put("port", req.httpService().port());
            item.put("secure", req.httpService().secure());
            item.put("status", resp != null ? resp.statusCode() : null);
            item.put("length", resp != null ? resp.body().length() : 0);
            item.put("mime", resp != null ? resp.statedMimeType().toString() : "");

            if (full) {
                item.put("request", req.toString());
                item.put("response", resp != null ? resp.toString() : null);
            }

            items.add(item);
            if (items.size() >= limit) break;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("count", items.size());
        result.put("total", all.size());
        result.put("items", items);

        ApiServer.respond(ex, 200, GSON.toJson(result));
    }

    public void historyItem(HttpExchange ex) {
        if (!"GET".equals(ex.getRequestMethod())) {
            ApiServer.respond(ex, 405, "{\"error\":\"method\"}");
            return;
        }

        String path = ex.getRequestURI().getPath();
        String indexStr = path.substring("/history/".length());

        int index;
        try {
            index = Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            ApiServer.respond(ex, 400, "{\"error\":\"invalid index\"}");
            return;
        }

        List<ProxyHttpRequestResponse> all = api.proxy().history();
        if (index < 0 || index >= all.size()) {
            ApiServer.respond(ex, 404, "{\"error\":\"not found\"}");
            return;
        }

        ProxyHttpRequestResponse entry = all.get(index);
        HttpRequest req = entry.finalRequest();
        HttpResponse resp = entry.response();

        Map<String, Object> item = new HashMap<>();
        item.put("index", index);
        item.put("url", req.url());
        item.put("method", req.method());
        item.put("host", req.httpService().host());
        item.put("port", req.httpService().port());
        item.put("secure", req.httpService().secure());
        item.put("status", resp != null ? resp.statusCode() : null);
        item.put("length", resp != null ? resp.body().length() : 0);
        item.put("mime", resp != null ? resp.statedMimeType().toString() : "");
        item.put("request", req.toString());
        item.put("response", resp != null ? resp.toString() : null);

        ApiServer.respond(ex, 200, GSON.toJson(item));
    }

    public void sitemap(HttpExchange ex) {
        if (!"GET".equals(ex.getRequestMethod())) {
            ApiServer.respond(ex, 405, "{\"error\":\"method\"}");
            return;
        }

        Map<String, String> params = parseQuery(ex.getRequestURI().getRawQuery());
        String prefix = params.get("prefix");

        List<HttpRequestResponse> all = api.siteMap().requestResponses();
        List<Map<String, Object>> items = new ArrayList<>();

        for (HttpRequestResponse entry : all) {
            HttpRequest req = entry.request();
            if (req == null) continue;

            String url = req.url();
            if (prefix != null && !url.startsWith(prefix)) continue;

            HttpResponse resp = entry.response();

            Map<String, Object> item = new HashMap<>();
            item.put("url", url);
            item.put("method", req.method());
            item.put("status", resp != null ? resp.statusCode() : null);
            items.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("count", items.size());
        result.put("items", items);

        ApiServer.respond(ex, 200, GSON.toJson(result));
    }

    public void scope(HttpExchange ex) {
        String method = ex.getRequestMethod();

        if ("GET".equals(method)) {
            Map<String, String> params = parseQuery(ex.getRequestURI().getRawQuery());
            String url = params.get("url");
            if (url == null || url.isEmpty()) {
                ApiServer.respond(ex, 400, "{\"error\":\"missing url parameter\"}");
                return;
            }
            boolean inScope = api.scope().isInScope(url);
            Map<String, Object> result = new HashMap<>();
            result.put("url", url);
            result.put("inScope", inScope);
            ApiServer.respond(ex, 200, GSON.toJson(result));

        } else if ("POST".equals(method)) {
            JsonObject body = readJson(ex);
            String url = body.has("url") ? body.get("url").getAsString() : null;
            if (url == null || url.isEmpty()) {
                ApiServer.respond(ex, 400, "{\"error\":\"missing url in body\"}");
                return;
            }
            api.scope().includeInScope(url);
            ApiServer.respond(ex, 200, "{\"added\":\"" + GSON.toJson(url).replace("\"", "") + "\"}");

        } else {
            ApiServer.respond(ex, 405, "{\"error\":\"method\"}");
        }
    }

    public void repeater(HttpExchange ex) {
        if (!"POST".equals(ex.getRequestMethod())) {
            ApiServer.respond(ex, 405, "{\"error\":\"method\"}");
            return;
        }

        JsonObject body = readJson(ex);
        String tab = body.has("tab") ? body.get("tab").getAsString() : "from-bridge";

        if (body.has("index")) {
            int index = body.get("index").getAsInt();
            List<ProxyHttpRequestResponse> all = api.proxy().history();
            if (index < 0 || index >= all.size()) {
                ApiServer.respond(ex, 404, "{\"error\":\"not found\"}");
                return;
            }
            HttpRequest req = all.get(index).finalRequest();
            api.repeater().sendToRepeater(req, tab);

            Map<String, Object> result = new HashMap<>();
            result.put("sent", true);
            result.put("tab", tab);
            ApiServer.respond(ex, 200, GSON.toJson(result));

        } else if (body.has("raw") && body.has("host")) {
            HttpRequest req = buildRequest(body);
            api.repeater().sendToRepeater(req, tab);

            Map<String, Object> result = new HashMap<>();
            result.put("sent", true);
            result.put("tab", tab);
            ApiServer.respond(ex, 200, GSON.toJson(result));

        } else {
            ApiServer.respond(ex, 400, "{\"error\":\"provide index or raw+host\"}");
        }
    }

    public void send(HttpExchange ex) {
        if (!"POST".equals(ex.getRequestMethod())) {
            ApiServer.respond(ex, 405, "{\"error\":\"method\"}");
            return;
        }

        JsonObject body = readJson(ex);
        if (!body.has("raw") || !body.has("host")) {
            ApiServer.respond(ex, 400, "{\"error\":\"missing raw or host\"}");
            return;
        }

        HttpRequest req = buildRequest(body);
        HttpRequestResponse rr = api.http().sendRequest(req);
        HttpResponse resp = rr.response();

        Map<String, Object> result = new HashMap<>();
        result.put("status", resp != null ? resp.statusCode() : null);
        result.put("response", resp != null ? resp.toString() : null);

        ApiServer.respond(ex, 200, GSON.toJson(result));
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isEmpty()) return map;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                map.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
            } else {
                map.put(
                    URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8)
                );
            }
        }
        return map;
    }

    private static JsonObject readJson(HttpExchange ex) {
        try (InputStreamReader reader = new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, JsonObject.class);
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private static HttpRequest buildRequest(JsonObject body) {
        String rawReq = body.get("raw").getAsString();
        String host = body.get("host").getAsString();
        int port = body.has("port") ? body.get("port").getAsInt() : 443;
        boolean tls = !body.has("tls") || body.get("tls").getAsBoolean();

        HttpService service = HttpService.httpService(host, port, tls);
        return HttpRequest.httpRequest(service, rawReq);
    }
}
