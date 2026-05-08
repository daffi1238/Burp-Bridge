package burpbridge;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

public class BurpBridgeExtension implements BurpExtension {

    private static final String ENV_LINE_PREFIX = "export BURP_BRIDGE_TOKEN=";

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("Burp Bridge");

        byte[] raw = new byte[24];
        new SecureRandom().nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        int port = Integer.parseInt(System.getProperty("burpbridge.port", "8765"));

        ApiServer server;
        try {
            server = new ApiServer(api, port, token);
            server.start();
        } catch (Exception e) {
            api.logging().logToError("Burp Bridge failed to start: " + e.getMessage());
            return;
        }

        writeTokenToZshrc(token, api);

        api.logging().logToOutput("=== Burp Bridge ===");
        api.logging().logToOutput("URL:   http://127.0.0.1:" + port);
        api.logging().logToOutput("Token: " + token);
        api.logging().logToOutput("Token written to ~/.zshrc");

        api.extension().registerUnloadingHandler(server::stop);
    }

    private void writeTokenToZshrc(String token, MontoyaApi api) {
        Path zshrc = Paths.get(System.getProperty("user.home"), ".zshrc");
        String exportLine = ENV_LINE_PREFIX + token;
        try {
            if (Files.exists(zshrc)) {
                List<String> lines = Files.readAllLines(zshrc, StandardCharsets.UTF_8);
                List<String> updated = lines.stream()
                        .map(l -> l.startsWith(ENV_LINE_PREFIX) ? exportLine : l)
                        .collect(Collectors.toList());
                if (!updated.contains(exportLine)) {
                    updated.add(exportLine);
                }
                Files.write(zshrc, updated, StandardCharsets.UTF_8);
            } else {
                Files.writeString(zshrc, exportLine + "\n", StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            api.logging().logToError("Failed to write token to ~/.zshrc: " + e.getMessage());
        }
    }
}
