package io.jenkins.plugins.lark.notice.testing;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import io.jenkins.plugins.lark.notice.tools.JsonUtils;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A throwaway HTTP server standing in for a robot webhook, capturing the request body so tests can
 * assert on the exact JSON that would have gone over the wire.
 *
 * <p>Register it as an extension; its callback runs before {@code @BeforeEach} methods, so a robot
 * can be pointed at {@link #url()} during setup:
 *
 * <pre>{@code
 * @RegisterExtension
 * final WebhookServer webhook = WebhookServer.lark();
 * }</pre>
 */
public final class WebhookServer implements BeforeEachCallback, AfterEachCallback {

    private final String path;

    private final String responseBody;

    private final AtomicReference<String> requestBody = new AtomicReference<>();

    private final AtomicReference<String> requestUri = new AtomicReference<>();

    private final AtomicReference<Map<String, List<String>>> requestHeaders = new AtomicReference<>();

    private HttpServer server;

    private WebhookServer(String path, String responseBody) {
        this.path = path;
        this.responseBody = responseBody;
    }

    /**
     * Creates a server answering on Lark's bot webhook path with a success response.
     *
     * @return Lark-shaped webhook server
     */
    public static WebhookServer lark() {
        return new WebhookServer("/open-apis/bot/v2/hook/", "{\"code\":0,\"msg\":\"success\"}");
    }

    /**
     * Creates a server answering on DingTalk's robot path with a success response.
     *
     * @return DingTalk-shaped webhook server
     */
    public static WebhookServer dingTalk() {
        return new WebhookServer("/robot/send", "{\"errcode\":0,\"errmsg\":\"ok\"}");
    }

    /**
     * Creates a server answering on WeCom's group robot path with a success response.
     *
     * @return WeCom-shaped webhook server
     */
    public static WebhookServer weCom() {
        return new WebhookServer("/cgi-bin/webhook/send", "{\"errcode\":0,\"errmsg\":\"ok\"}");
    }

    @Override
    public void beforeEach(ExtensionContext context) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(path, exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            requestUri.set(exchange.getRequestURI().toString());
            requestHeaders.set(Map.copyOf(exchange.getRequestHeaders()));
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * Returns the webhook URL to configure a robot with, including a dummy credential so the URL
     * looks like the real thing to the plugin's webhook parsing.
     *
     * @return full webhook URL
     */
    public String url() {
        String base = "http://localhost:" + server.getAddress().getPort() + path;
        return switch (path) {
            case "/open-apis/bot/v2/hook/" -> base + "token";
            case "/robot/send" -> base + "?access_token=t";
            default -> base + "?key=token";
        };
    }

    /**
     * Returns the raw body of the last captured request.
     *
     * @return request body, or {@code null} when nothing was sent
     */
    public String body() {
        return requestBody.get();
    }

    /**
     * Returns the request URI of the last captured request, including its query string. DingTalk
     * signing appends credentials to the URL rather than sending headers, so tests need this.
     *
     * @return request URI, or {@code null} when nothing was sent
     */
    public String uri() {
        return requestUri.get();
    }

    /**
     * Returns the first value of a header on the last captured request.
     *
     * @param name header name
     * @return header value, or {@code null} when absent
     */
    public String header(String name) {
        Map<String, List<String>> headers = requestHeaders.get();
        if (headers == null) {
            return null;
        }
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .flatMap(entry -> entry.getValue().stream())
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns the last captured request body parsed as JSON.
     *
     * @return parsed request body
     */
    public JsonNode json() {
        return JsonUtils.readTree(requestBody.get());
    }
}
