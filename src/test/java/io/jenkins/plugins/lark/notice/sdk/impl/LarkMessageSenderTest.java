package io.jenkins.plugins.lark.notice.sdk.impl;

import com.fasterxml.jackson.databind.JsonNode;
import io.jenkins.plugins.lark.notice.enums.BuildStatusEnum;
import io.jenkins.plugins.lark.notice.enums.MsgTypeEnum;
import io.jenkins.plugins.lark.notice.model.BuildContext;
import io.jenkins.plugins.lark.notice.model.MessageIntent;
import io.jenkins.plugins.lark.notice.model.payload.LarkPayload;
import io.jenkins.plugins.lark.notice.sdk.model.SendResult;
import io.jenkins.plugins.lark.notice.sdk.model.lark.support.Button;
import io.jenkins.plugins.lark.notice.tools.JsonUtils;
import io.jenkins.plugins.lark.notice.testing.TestRobots;
import io.jenkins.plugins.lark.notice.testing.WebhookServer;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Baseline tests pinning the JSON shape produced by {@link LarkMessageSender} for every message
 * type it supports. These exist to protect the wire format across refactors: any change to how
 * the step, intent or payload layers carry data must keep these assertions passing.
 */
public class LarkMessageSenderTest {

    @Rule
    public WebhookServer webhook = WebhookServer.lark();

    private LarkMessageSender sender() {
        return sender(null);
    }

    private LarkMessageSender sender(String keys) {
        return TestRobots.larkSender(webhook.url(), keys);
    }

    private JsonNode body() {
        return webhook.json();
    }

    @Test
    public void sendTextShouldUseTextMsgTypeAndAtAllShortCircuitsUserIds() {
        MessageIntent intent = MessageIntent.builder()
                .type(MsgTypeEnum.TEXT)
                .text("hello")
                .atAll(true)
                .atUserIds(Set.of("13800138000", "ou_abc"))
                .build();

        SendResult result = sender().sendText(BuildContext.builder().build(), intent, LarkPayload.builder().build());

        assertTrue(result.isOk());
        assertEquals(webhook.body(), result.getRequestBody());
        JsonNode body = body();
        assertEquals("text", body.path("msg_type").asText());
        // atAll wins: individual ids are not appended when at-all is set.
        assertEquals("hello<at id=all></at>", body.path("content").path("text").asText());
    }

    @Test
    public void sendTextShouldAppendOnlyNonMobileUserIds() {
        MessageIntent intent = MessageIntent.builder()
                .type(MsgTypeEnum.TEXT)
                .text("hello")
                .atAll(false)
                .atUserIds(Set.of("13800138000", "ou_abc"))
                .build();

        sender().sendText(BuildContext.builder().build(), intent, LarkPayload.builder().build());

        // Mobiles are partitioned away by MessageIntent#getAt and dropped by the Lark renderer.
        String text = body().path("content").path("text").asText();
        assertTrue(text.contains("<at id=ou_abc></at>"));
        assertFalse(text.contains("13800138000"));
    }

    @Test
    public void sendTextShouldAppendConfiguredKeyword() {
        MessageIntent intent = MessageIntent.builder().type(MsgTypeEnum.TEXT).text("hello").build();

        sender("Jenkins").sendText(BuildContext.builder().build(), intent, LarkPayload.builder().build());

        assertEquals("hello Jenkins", body().path("content").path("text").asText());
    }

    @Test
    public void sendImageShouldReadImageKeyFromPayload() {
        MessageIntent intent = MessageIntent.builder().type(MsgTypeEnum.IMAGE).text("body text").build();

        sender().sendImage(BuildContext.builder().build(), intent,
                LarkPayload.builder().imageKey("img_v2_key").build());

        JsonNode body = body();
        assertEquals("image", body.path("msg_type").asText());
        assertEquals("img_v2_key", body.path("content").path("image_key").asText());
    }

    @Test
    public void sendShareChatShouldReadChatIdFromPayload() {
        MessageIntent intent = MessageIntent.builder().type(MsgTypeEnum.SHARE_CHAT).text("body text").build();

        sender().sendShareChat(BuildContext.builder().build(), intent,
                LarkPayload.builder().shareChatId("oc_chat").build());

        JsonNode body = body();
        assertEquals("share_chat", body.path("msg_type").asText());
        assertEquals("oc_chat", body.path("content").path("share_chat_id").asText());
    }

    @Test
    public void sendPostShouldReadStructuredPostFromPayload() {
        List<List<Map<String, String>>> post = List.of(List.of(Map.of("tag", "text", "text", "line one")));
        MessageIntent intent = MessageIntent.builder()
                .type(MsgTypeEnum.POST)
                .title("Build")
                .text("body text")
                .build();

        sender().sendPost(BuildContext.builder().build(), intent, LarkPayload.builder().post(post).build());

        JsonNode body = body();
        assertEquals("post", body.path("msg_type").asText());
        JsonNode content = body.path("content").path("post").path("zh_cn");
        assertEquals("Build", content.path("title").asText());
        assertEquals("line one", content.path("content").get(0).get(0).path("text").asText());
    }

    @Test
    public void sendMarkdownShouldRenderInteractiveCardWithKeywordInTitle() {
        MessageIntent intent = MessageIntent.builder()
                .type(MsgTypeEnum.MARKDOWN)
                .title("Build Notice")
                .text("**done**")
                .statusType(BuildStatusEnum.SUCCESS)
                .build();

        sender("Jenkins").sendMarkdown(BuildContext.builder().locale(Locale.US).build(), intent,
                LarkPayload.builder().build());

        JsonNode body = body();
        assertEquals("interactive", body.path("msg_type").asText());
        assertTrue(body.path("card").path("header").toString().contains("Build Notice Jenkins"));
        assertTrue(webhook.body().contains("**done**"));
    }

    @Test
    public void sendCardShouldRenderButtonsFromIntent() {
        Button changes = new Button("Changes", "https://example.com/changes", "default");
        MessageIntent intent = MessageIntent.builder()
                .type(MsgTypeEnum.CARD)
                .title("Build Notice")
                .text("body text")
                .statusType(BuildStatusEnum.SUCCESS)
                .buttons(List.of(changes))
                .build();

        sender().sendCard(BuildContext.builder().locale(Locale.US).build(), intent, LarkPayload.builder().build());

        assertEquals("interactive", body().path("msg_type").asText());
        assertTrue(webhook.body().contains("Changes"));
        assertTrue(webhook.body().contains("https://example.com/changes"));
    }

    /**
     * Raw mode (freestyle "raw message" toggle) hands a full card JSON through the shared text
     * field; the sender must detect that and pass it through instead of building a card.
     */
    @Test
    public void sendCardShouldPassThroughRawCardJson() {
        String rawCard = "{\"header\":{\"template\":\"red\"},\"elements\":[]}";
        MessageIntent intent = MessageIntent.builder()
                .type(MsgTypeEnum.CARD)
                .title("ignored when raw")
                .text(rawCard)
                .build();

        sender().sendCard(BuildContext.builder().build(), intent, LarkPayload.builder().build());

        JsonNode body = body();
        assertEquals("interactive", body.path("msg_type").asText());
        assertEquals("red", body.path("card").path("header").path("template").asText());
        assertFalse(webhook.body().contains("ignored when raw"));
    }

    @Test
    public void sendLinkShouldReportUnsupported() {
        SendResult result = sender().sendLink(BuildContext.builder().build(),
                MessageIntent.builder().type(MsgTypeEnum.LINK).build(), LarkPayload.builder().build());

        assertFalse(result.isOk());
    }
}
