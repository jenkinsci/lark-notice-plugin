package io.jenkins.plugins.lark.notice.sdk.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import io.jenkins.plugins.lark.notice.enums.BuildStatusEnum;
import io.jenkins.plugins.lark.notice.enums.MsgTypeEnum;
import io.jenkins.plugins.lark.notice.enums.RobotType;
import io.jenkins.plugins.lark.notice.model.BuildContext;
import io.jenkins.plugins.lark.notice.model.MessageIntent;
import io.jenkins.plugins.lark.notice.model.RobotConfigModel;
import io.jenkins.plugins.lark.notice.model.payload.DingPayload;
import io.jenkins.plugins.lark.notice.sdk.model.SendResult;
import io.jenkins.plugins.lark.notice.sdk.model.lark.support.Button;
import io.jenkins.plugins.lark.notice.tools.JsonUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Baseline tests pinning the JSON shape produced by {@link DingMessageSender} for every message
 * type it supports, including both action-card branches (single jump button vs button list).
 */
public class DingMessageSenderTest {

    private HttpServer server;

    private AtomicReference<String> requestBody;

    @Before
    public void setUp() throws IOException {
        requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/robot/send", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"errcode\":0,\"errmsg\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    @After
    public void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private DingMessageSender sender() {
        return sender(null);
    }

    private DingMessageSender sender(String keys) {
        RobotConfigModel robotConfig = new RobotConfigModel();
        robotConfig.setRobotType(RobotType.DING_TALK);
        robotConfig.setWebhook("http://localhost:" + server.getAddress().getPort() + "/robot/send?access_token=t");
        robotConfig.setKeys(keys);
        return new DingMessageSender(robotConfig);
    }

    private JsonNode body() {
        return JsonUtils.readTree(requestBody.get());
    }

    @Test
    public void sendTextShouldUseTextMsgTypeAndCarryMentions() {
        MessageIntent intent = MessageIntent.builder()
                .type(MsgTypeEnum.TEXT)
                .text("hello")
                .atAll(false)
                .atUserIds(Set.of("13800138000", "zhangsan"))
                .build();

        SendResult result = sender().sendText(BuildContext.builder().build(), intent, DingPayload.builder().build());

        assertTrue(result.isOk());
        assertEquals(requestBody.get(), result.getRequestBody());
        JsonNode body = body();
        assertEquals("text", body.path("msgtype").asText());
        assertTrue(body.path("text").path("content").asText().startsWith("hello"));
        assertEquals("13800138000", body.path("at").path("atMobiles").get(0).asText());
        assertEquals("zhangsan", body.path("at").path("atUserIds").get(0).asText());
    }

    @Test
    public void sendTextShouldAppendConfiguredKeyword() {
        MessageIntent intent = MessageIntent.builder().type(MsgTypeEnum.TEXT).text("hello").build();

        sender("Jenkins").sendText(BuildContext.builder().build(), intent, DingPayload.builder().build());

        assertTrue(body().path("text").path("content").asText().contains("hello Jenkins"));
    }

    @Test
    public void sendMarkdownShouldCarryTitleAndKeyword() {
        MessageIntent intent = MessageIntent.builder()
                .type(MsgTypeEnum.MARKDOWN)
                .title("Build Notice")
                .text("**done**")
                .build();

        sender("Jenkins").sendMarkdown(BuildContext.builder().build(), intent, DingPayload.builder().build());

        JsonNode body = body();
        assertEquals("markdown", body.path("msgtype").asText());
        assertEquals("Build Notice", body.path("markdown").path("title").asText());
        assertTrue(body.path("markdown").path("text").asText().contains("**done** Jenkins"));
    }

    @Test
    public void sendLinkShouldCarryPicAndMessageUrl() {
        MessageIntent intent = MessageIntent.builder()
                .type(MsgTypeEnum.LINK)
                .title("Build Notice")
                .text("body")
                .picUrl("https://example.com/pic.png")
                .messageUrl("https://example.com/build")
                .build();

        sender().sendLink(BuildContext.builder().build(), intent, DingPayload.builder().build());

        JsonNode link = body().path("link");
        assertEquals("link", body().path("msgtype").asText());
        assertEquals("Build Notice", link.path("title").asText());
        assertEquals("https://example.com/pic.png", link.path("picUrl").asText());
        assertEquals("https://example.com/build", link.path("messageUrl").asText());
    }

    @Test
    public void sendCardWithButtonsShouldUseBtnsBranch() {
        MessageIntent intent = MessageIntent.builder()
                .type(MsgTypeEnum.CARD)
                .title("Build Notice")
                .text("body")
                .statusType(BuildStatusEnum.SUCCESS)
                .buttons(List.of(new Button("Changes", "https://example.com/changes", "default")))
                .build();
        DingPayload payload = DingPayload.builder().btnOrientation("0").build();

        sender("Jenkins").sendCard(BuildContext.builder().build(), intent, payload);

        JsonNode card = body().path("actionCard");
        assertEquals("actionCard", body().path("msgtype").asText());
        assertEquals("Build Notice", card.path("title").asText());
        assertEquals("0", card.path("btnOrientation").asText());
        assertEquals("Changes", card.path("btns").get(0).path("title").asText());
        assertEquals("https://example.com/changes", card.path("btns").get(0).path("actionURL").asText());
        assertTrue(card.path("text").asText().contains("body Jenkins"));
    }

    @Test
    public void sendCardWithSingleTitleShouldUseSingleJumpBranchAndIgnoreButtons() {
        MessageIntent intent = MessageIntent.builder()
                .type(MsgTypeEnum.CARD)
                .title("Build Notice")
                .text("body")
                .buttons(List.of(new Button("Changes", "https://example.com/changes", "default")))
                .build();
        DingPayload payload = DingPayload.builder()
                .singleTitle("Open").singleUrl("https://example.com/build").build();

        sender("Jenkins").sendCard(BuildContext.builder().build(), intent, payload);

        JsonNode card = body().path("actionCard");
        assertEquals("Open", card.path("singleTitle").asText());
        // The official custom-robot API spells the jump target singleURL, matching btns[].actionURL.
        assertEquals("https://example.com/build", card.path("singleURL").asText());
        assertTrue(card.path("singleUrl").isMissingNode());
        assertTrue(card.path("btns").isMissingNode() || card.path("btns").isNull());
        assertFalse(requestBody.get().contains("Changes"));
        // The single-jump branch appends the security keyword like the btns branch does.
        assertTrue(card.path("text").asText().contains("body Jenkins"));
    }

    @Test
    public void sendImageAndShareChatShouldReportUnsupported() {
        BuildContext ctx = BuildContext.builder().build();
        DingPayload payload = DingPayload.builder().build();
        MessageIntent intent = MessageIntent.builder().type(MsgTypeEnum.IMAGE).build();

        assertFalse(sender().sendImage(ctx, intent, payload).isOk());
        assertFalse(sender().sendShareChat(ctx, intent, payload).isOk());
        assertFalse(sender().sendPost(ctx, intent, payload).isOk());
    }
}
