package io.jenkins.plugins.lark.notice.sdk.impl;

import com.fasterxml.jackson.databind.JsonNode;
import io.jenkins.plugins.lark.notice.enums.BuildStatusEnum;
import io.jenkins.plugins.lark.notice.enums.MsgTypeEnum;
import io.jenkins.plugins.lark.notice.enums.RobotProtocolType;
import io.jenkins.plugins.lark.notice.enums.RobotType;
import io.jenkins.plugins.lark.notice.model.BuildContext;
import io.jenkins.plugins.lark.notice.model.BuildJobModel;
import io.jenkins.plugins.lark.notice.model.CardField;
import io.jenkins.plugins.lark.notice.model.MessageIntent;
import io.jenkins.plugins.lark.notice.model.payload.WeComPayload;
import io.jenkins.plugins.lark.notice.sdk.DispatchTarget;
import io.jenkins.plugins.lark.notice.sdk.MessageDispatcher;
import io.jenkins.plugins.lark.notice.sdk.model.SendResult;
import io.jenkins.plugins.lark.notice.sdk.model.lark.support.Button;
import io.jenkins.plugins.lark.notice.testing.TestRobots;
import io.jenkins.plugins.lark.notice.testing.WebhookServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WeCom message payload generation.
 */
public class WechatWorkMessageSenderTest {

    @RegisterExtension
    final WebhookServer webhook = WebhookServer.weCom();

    private static void assertHorizontalContent(JsonNode content, int type, String key, String value, String url) {
        assertEquals(type, content.path("type").asInt());
        assertEquals(key, content.path("keyname").asText());
        assertEquals(value, content.path("value").asText());
        if (type == 1) {
            assertEquals(url, content.path("url").asText());
        } else {
            assertTrue(content.path("url").isNull());
        }
    }

    private WechatWorkMessageSender sender() {
        return TestRobots.weComSender(webhook.url(), null);
    }

    private JsonNode body() {
        return webhook.json();
    }

    @Test
    public void sendTextShouldUseWechatWorkPayloadShape() {
        WechatWorkMessageSender sender = sender();
        MessageIntent intent = MessageIntent.builder()
                .text("hello")
                .atAll(false)
                .atUserIds(Set.of("13800138000", "zhangsan"))
                .build();
        BuildContext ctx = BuildContext.builder().build();
        WeComPayload payload = WeComPayload.builder().build();

        SendResult result = sender.sendText(ctx, intent, payload);

        assertTrue(result.isOk());
        assertEquals(webhook.body(), result.getRequestBody());
        assertTrue(webhook.body().contains("\"msgtype\":\"text\""));
        assertTrue(webhook.body().contains("\"content\":\"hello\""));
        assertTrue(webhook.body().contains("\"mentioned_list\":[\"zhangsan\"]"));
        assertTrue(webhook.body().contains("\"mentioned_mobile_list\":[\"13800138000\"]"));
    }

    @Test
    public void cardMessageShouldUseNewsNoticeTemplateCardPayload() {
        WechatWorkMessageSender sender = sender();
        MessageIntent intent = MessageIntent.builder()
                .type(MsgTypeEnum.CARD)
                .title("Build Notice")
                .text("build ok")
                .statusType(BuildStatusEnum.SUCCESS)
                .buttons(List.of(
                        new Button("Changes", "https://jenkins.example/job/demo/1/changes", "primary_filled"),
                        new Button("Console", "https://jenkins.example/job/demo/1/console", "default")
                ))
                .atAll(false)
                .atUserIds(Set.of("zhangsan"))
                .build();
        BuildContext ctx = BuildContext.builder()
                .projectName("Demo Project")
                .projectUrl("https://jenkins.example/job/demo/")
                .jobName("#1")
                .jobUrl("https://jenkins.example/job/demo/1/")
                .statusType(BuildStatusEnum.SUCCESS)
                .duration("1 sec")
                .executorName("xm.z")
                .locale(Locale.US)
                .build();
        WeComPayload payload = WeComPayload.builder().build();

        SendResult result = MessageDispatcher.getInstance().send(null, ctx, intent, payload,
                new DispatchTarget(null, null, RobotProtocolType.WECHAT_WORK, null, sender));

        assertTrue(result.isOk());
        JsonNode root = body();
        JsonNode card = root.path("template_card");
        assertEquals("template_card", root.path("msgtype").asText());
        assertEquals("news_notice", card.path("card_type").asText());
        assertFalse(card.has("quote_area"));
        assertEquals("https://get.jenkins.io/art/jenkins-logo/favicon.ico", card.path("source").path("icon_url").asText());
        assertEquals("Lark Notice · Jenkins", card.path("source").path("desc").asText());
        assertEquals(3, card.path("source").path("desc_color").asInt());
        assertEquals("Build Notice", card.path("main_title").path("title").asText());
        assertTrue(card.path("main_title").path("desc").isNull());
        assertEquals("https://www.jenkins.io/images/post-images/2025/07/24/redesigning-jenkins-part-two.png",
                card.path("card_image").path("url").asText());
        assertEquals(2.25d, card.path("card_image").path("aspect_ratio").asDouble(), 0.001d);
        assertHorizontalContent(card.path("horizontal_content_list").get(0),
                1, "Task Name", "Demo Project", "https://jenkins.example/job/demo/");
        assertHorizontalContent(card.path("horizontal_content_list").get(1),
                1, "Job Number", "#1", "https://jenkins.example/job/demo/1/");
        assertHorizontalContent(card.path("horizontal_content_list").get(2),
                0, "Build Status", "Success", "");
        assertHorizontalContent(card.path("horizontal_content_list").get(3),
                0, "Build Duration", "1 sec", "");
        assertHorizontalContent(card.path("horizontal_content_list").get(4),
                0, "Executor", "xm.z", "");
        assertEquals("https://jenkins.example/job/demo/1/changes", card.path("card_action").path("url").asText());
        assertEquals("Changes", card.path("jump_list").get(0).path("title").asText());
        assertEquals("Console", card.path("jump_list").get(1).path("title").asText());
        // Structured build rows are present, so the vertical text block must be omitted.
        assertTrue(card.path("vertical_content_list").isMissingNode() || card.path("vertical_content_list").isNull());
    }

    @Test
    public void cardWithAdditionalContentShouldRenderVerticalBlock() {
        WechatWorkMessageSender sender = sender();
        MessageIntent intent = MessageIntent.builder()
                .type(MsgTypeEnum.CARD)
                .title("Build Notice")
                .text("build ok")
                .statusType(BuildStatusEnum.SUCCESS)
                .build();
        BuildContext ctx = BuildContext.builder()
                .projectName("Demo Project")
                .jobName("#1")
                .statusType(BuildStatusEnum.SUCCESS)
                .duration("1 sec")
                .executorName("xm.z")
                .locale(Locale.US)
                .build();
        WeComPayload payload = WeComPayload.builder().additionalContent("custom release notes").build();

        SendResult result = MessageDispatcher.getInstance().send(null, ctx, intent, payload,
                new DispatchTarget(null, null, RobotProtocolType.WECHAT_WORK, null, sender));

        assertTrue(result.isOk());
        JsonNode card = body().path("template_card");
        JsonNode vertical = card.path("vertical_content_list");
        assertFalse(vertical.isMissingNode());
        assertEquals("Build Notice", vertical.get(0).path("title").asText());
        assertEquals("custom release notes", vertical.get(0).path("desc").asText());
    }

    @Test
    public void cardWithCustomCardFieldsShouldOverrideDefaultRows() {
        WechatWorkMessageSender sender = sender();
        MessageIntent intent = MessageIntent.builder()
                .type(MsgTypeEnum.CARD)
                .title("Release")
                .statusType(BuildStatusEnum.SUCCESS)
                .build();
        BuildContext ctx = BuildContext.builder()
                .projectName("Ignored")
                .jobName("Ignored")
                .statusType(BuildStatusEnum.SUCCESS)
                .locale(Locale.US)
                .build();
        intent = intent.toBuilder()
                .cardFields(List.of(
                        CardField.builder().keyname("版本").value("1.2.0").build(),
                        CardField.builder().keyname("发布单").value("详情").url("https://example.com/release/1").build()))
                .build();
        WeComPayload payload = WeComPayload.builder().build();

        SendResult result = MessageDispatcher.getInstance().send(null, ctx, intent, payload,
                new DispatchTarget(null, null, RobotProtocolType.WECHAT_WORK, null, sender));

        assertTrue(result.isOk());
        JsonNode rows = body().path("template_card").path("horizontal_content_list");
        assertEquals(2, rows.size());
        assertEquals("版本", rows.get(0).path("keyname").asText());
        assertEquals("1.2.0", rows.get(0).path("value").asText());
        assertEquals("发布单", rows.get(1).path("keyname").asText());
        assertEquals("详情", rows.get(1).path("value").asText());
        assertEquals("https://example.com/release/1", rows.get(1).path("url").asText());
    }

    @Test
    public void cardFieldsShouldBeCappedAtSixRows() {
        WechatWorkMessageSender sender = sender();
        MessageIntent intent = MessageIntent.builder()
                .type(MsgTypeEnum.CARD)
                .title("Release")
                .statusType(BuildStatusEnum.SUCCESS)
                .build();
        BuildContext ctx = BuildContext.builder()
                .statusType(BuildStatusEnum.SUCCESS)
                .locale(Locale.US)
                .build();
        // Blank-valued rows are dropped before the cap, so 9 inputs must yield the first 6 non-blank rows.
        intent = intent.toBuilder()
                .cardFields(List.of(
                        CardField.builder().keyname("k1").value("v1").build(),
                        CardField.builder().keyname("blank").value("  ").build(),
                        CardField.builder().keyname("k2").value("v2").build(),
                        CardField.builder().keyname("k3").value("v3").build(),
                        CardField.builder().keyname("k4").value("v4").build(),
                        CardField.builder().keyname("k5").value("v5").build(),
                        CardField.builder().keyname("k6").value("v6").build(),
                        CardField.builder().keyname("k7").value("v7").build(),
                        CardField.builder().keyname("k8").value("v8").build()))
                .build();
        WeComPayload payload = WeComPayload.builder().build();

        SendResult result = MessageDispatcher.getInstance().send(null, ctx, intent, payload,
                new DispatchTarget(null, null, RobotProtocolType.WECHAT_WORK, null, sender));

        assertTrue(result.isOk());
        JsonNode rows = body().path("template_card").path("horizontal_content_list");
        assertEquals(6, rows.size());
        assertEquals("k1", rows.get(0).path("keyname").asText());
        assertEquals("k6", rows.get(5).path("keyname").asText());
    }

    @Test
    public void quoteAreaShouldBeOmittedUnlessConfigured() {
        WechatWorkMessageSender sender = sender();
        MessageIntent intent = MessageIntent.builder().type(MsgTypeEnum.CARD).title("T")
                .statusType(BuildStatusEnum.SUCCESS).build();
        BuildContext ctx = BuildContext.builder().locale(Locale.US).build();

        sender.sendCard(ctx, intent, WeComPayload.builder().build());
        assertFalse(body().path("template_card").has("quote_area"));

        sender.sendCard(ctx, intent, WeComPayload.builder()
                .quoteTitle("Release note").quoteText("v1.2.0 shipped")
                .quoteUrl("https://example.com/release").build());

        JsonNode quote = body().path("template_card").path("quote_area");
        assertEquals(1, quote.path("type").asInt());
        assertEquals("https://example.com/release", quote.path("url").asText());
        assertEquals("Release note", quote.path("title").asText());
        assertEquals("v1.2.0 shipped", quote.path("quote_text").asText());
    }

    @Test
    public void quoteAreaWithoutUrlShouldHaveNoClickAction() {
        WechatWorkMessageSender sender = sender();
        sender.sendCard(BuildContext.builder().locale(Locale.US).build(),
                MessageIntent.builder().type(MsgTypeEnum.CARD).title("T").build(),
                WeComPayload.builder().quoteText("no link").build());

        JsonNode quote = body().path("template_card").path("quote_area");
        assertEquals(0, quote.path("type").asInt());
        assertEquals("no link", quote.path("quote_text").asText());
    }

    @Test
    public void cardImageShouldFollowThePayloadAndFallBackForUnusableValues() {
        WechatWorkMessageSender sender = sender();
        String customImage = "https://cdn.example.com/build-banner.png";
        MessageIntent intent = MessageIntent.builder()
                .type(MsgTypeEnum.CARD)
                .title("Build Notice")
                .statusType(BuildStatusEnum.SUCCESS)
                .build();
        BuildContext ctx = BuildContext.builder().locale(Locale.US).build();

        SendResult result = MessageDispatcher.getInstance().send(null, ctx, intent,
                WeComPayload.builder().cardImageUrl(customImage).build(),
                new DispatchTarget(null, null, RobotProtocolType.WECHAT_WORK, null, sender));

        assertTrue(result.isOk());
        assertEquals(customImage, body().path("template_card").path("card_image").path("url").asText());

        // A Lark image key is not a URL WeCom can fetch, so the built-in banner has to win.
        sender.sendCard(ctx, intent, WeComPayload.builder().cardImageUrl("img_v2_key").build());

        assertEquals("https://www.jenkins.io/images/post-images/2025/07/24/redesigning-jenkins-part-two.png",
                body().path("template_card").path("card_image").path("url").asText());
    }

    @Test
    public void sourceIconShouldFollowThePayloadAndFallBackForUnusableValues() {
        WechatWorkMessageSender sender = sender();
        MessageIntent intent = MessageIntent.builder().type(MsgTypeEnum.CARD).title("Build Notice")
                .statusType(BuildStatusEnum.SUCCESS).build();
        BuildContext ctx = BuildContext.builder().locale(Locale.US).statusType(BuildStatusEnum.SUCCESS).build();

        sender.sendCard(ctx, intent, WeComPayload.builder()
                .sourceDesc("ACME Release Bot")
                .sourceIconUrl("https://cdn.example.com/acme.png")
                .build());

        JsonNode source = body().path("template_card").path("source");
        assertEquals("https://cdn.example.com/acme.png", source.path("icon_url").asText());
        assertEquals("ACME Release Bot", source.path("desc").asText());

        // WeCom drops the whole source block for anything but a public http(s) URL, so a local path
        // or an image key must keep the built-in icon rather than be passed through.
        sender.sendCard(ctx, intent, WeComPayload.builder().sourceIconUrl("img_v2_key").build());

        assertEquals("https://get.jenkins.io/art/jenkins-logo/favicon.ico",
                body().path("template_card").path("source").path("icon_url").asText());
    }

    @Test
    public void buildNoticeMarkdownShouldUseWechatWorkCompatibleFormat() {
        BuildJobModel model = BuildJobModel.builder()
                .title("Build Notice")
                .projectName("Demo Project")
                .projectUrl("https://jenkins.example/job/demo/")
                .jobName("#1")
                .jobUrl("https://jenkins.example/job/demo/1/")
                .statusType(BuildStatusEnum.SUCCESS)
                .duration("1 sec")
                .executorName("xm.z")
                .build();

        String markdown = model.toMarkdown(RobotType.WECHAT_WORK, Locale.US);

        assertTrue(markdown.contains(">**Task Name**: [Demo Project](https://jenkins.example/job/demo/)"));
        assertTrue(markdown.contains(">**Job Number**: [#1](https://jenkins.example/job/demo/1/)"));
        assertTrue(markdown.contains(">**Build Status**: <font color=\"info\">Success</font>"));
        assertTrue(markdown.contains(">**Build Duration**: 1 sec"));
        assertTrue(markdown.contains(">**Executor**: xm.z"));
    }
}
