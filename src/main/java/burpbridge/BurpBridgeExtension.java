package burpbridge;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

import java.security.SecureRandom;
import java.util.Base64;

public class BurpBridgeExtension implements BurpExtension {

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

        api.logging().logToOutput("=== Burp Bridge ===");
        api.logging().logToOutput("URL:   http://127.0.0.1:" + port);
        api.logging().logToOutput("Token: " + token);
        api.logging().logToOutput("");
        api.logging().logToOutput("export BURP_BRIDGE_TOKEN=" + token);

        api.extension().registerUnloadingHandler(server::stop);
    }
}
