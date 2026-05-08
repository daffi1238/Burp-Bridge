package burpbridge;

import burp.api.montoya.MontoyaApi;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class ApiServer {

    private final HttpServer server;
    private final MontoyaApi api;
    private final String token;
    private final Handlers handlers;

    public ApiServer(MontoyaApi api, int port, String token) throws IOException {
        this.api = api;
        this.token = token;
        this.handlers = new Handlers(api);
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        registerRoutes();
    }

    private void registerRoutes() {
        server.createContext("/health", handlers::health);
        server.createContext("/history/", auth(handlers::historyItem));
        server.createContext("/history", auth(handlers::history));
        server.createContext("/sitemap", auth(handlers::sitemap));
        server.createContext("/scope", auth(handlers::scope));
        server.createContext("/repeater", auth(handlers::repeater));
        server.createContext("/send", auth(handlers::send));
    }

    private HttpHandler auth(HttpHandler inner) {
        return ex -> {
            String authHeader = ex.getRequestHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.equals("Bearer " + token)) {
                respond(ex, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            try {
                inner.handle(ex);
            } catch (Exception e) {
                api.logging().logToError("Handler error: " + e.getMessage());
                respond(ex, 500, "{\"error\":\"" + e.getClass().getSimpleName() + "\"}");
            }
        };
    }

    public static void respond(HttpExchange ex, int code, String body) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        } catch (IOException e) {
            // connection already closed
        }
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }
}
