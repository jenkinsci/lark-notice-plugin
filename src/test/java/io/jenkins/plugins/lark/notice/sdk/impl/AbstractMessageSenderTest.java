package io.jenkins.plugins.lark.notice.sdk.impl;

import io.jenkins.plugins.lark.notice.enums.MsgTypeEnum;
import io.jenkins.plugins.lark.notice.enums.RobotType;
import io.jenkins.plugins.lark.notice.model.BuildContext;
import io.jenkins.plugins.lark.notice.model.MessageIntent;
import io.jenkins.plugins.lark.notice.model.RobotConfigModel;
import io.jenkins.plugins.lark.notice.model.payload.DingPayload;
import io.jenkins.plugins.lark.notice.model.payload.LarkPayload;
import io.jenkins.plugins.lark.notice.sdk.model.SendResult;
import io.jenkins.plugins.lark.notice.testing.TestRobots;
import io.jenkins.plugins.lark.notice.testing.WebhookServer;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests the shared HTTP layer in {@link AbstractMessageSender}: how signing credentials reach the
 * request, how the sent body is reported back, and how transport failures are turned into a
 * {@link SendResult} instead of an exception.
 */
public class AbstractMessageSenderTest {

    @Rule
    public WebhookServer webhook = WebhookServer.dingTalk();

    @Rule
    public WebhookServer larkWebhook = WebhookServer.lark();

    private static MessageIntent textIntent() {
        return MessageIntent.builder().type(MsgTypeEnum.TEXT).text("hello").build();
    }

    /**
     * DingTalk signs by appending timestamp and sign to the webhook URL rather than sending
     * headers, which is the one platform-specific branch in the shared request builder.
     */
    @Test
    public void dingTalkSigningShouldAppendCredentialsToTheUrl() {
        RobotConfigModel config = TestRobots.configModel(RobotType.DING_TALK, webhook.url(), null);
        config.setSign("secret-value");

        SendResult result = new DingMessageSender(config)
                .sendText(BuildContext.builder().build(), textIntent(), DingPayload.builder().build());

        assertTrue(result.isOk());
        String uri = webhook.uri();
        assertTrue(uri, uri.contains("access_token=t"));
        assertTrue(uri, uri.contains("&timestamp="));
        assertTrue(uri, uri.contains("&sign="));
        // The credentials go in the URL, not in headers.
        assertNull(webhook.header("timestamp"));
        assertNull(webhook.header("sign"));
    }

    @Test
    public void unsignedDingTalkRequestShouldLeaveTheUrlAlone() {
        SendResult result = TestRobots.dingSender(webhook.url(), null)
                .sendText(BuildContext.builder().build(), textIntent(), DingPayload.builder().build());

        assertTrue(result.isOk());
        assertFalse(webhook.uri(), webhook.uri().contains("timestamp="));
        assertFalse(webhook.uri(), webhook.uri().contains("sign="));
    }

    @Test
    public void requestShouldBeSentAsJson() {
        TestRobots.dingSender(webhook.url(), null)
                .sendText(BuildContext.builder().build(), textIntent(), DingPayload.builder().build());

        assertEquals("application/json", webhook.header("Content-Type"));
    }

    /**
     * Callers log the exact body that went over the wire, so the sender copies it onto the result.
     */
    @Test
    public void sendResultShouldCarryTheRequestBody() {
        SendResult result = TestRobots.dingSender(webhook.url(), null)
                .sendText(BuildContext.builder().build(), textIntent(), DingPayload.builder().build());

        assertNotNull(result.getRequestBody());
        assertEquals(webhook.body(), result.getRequestBody());
    }

    /**
     * A refused connection must come back as a failed result. Notification code paths report send
     * failures through SendResult; an escaping exception would bypass failOnError handling.
     */
    @Test
    public void connectionFailureShouldBecomeAFailedResult() {
        SendResult result = TestRobots.larkSender("http://127.0.0.1:1/open-apis/bot/v2/hook/x", null)
                .sendText(BuildContext.builder().build(), textIntent(), LarkPayload.builder().build());

        assertFalse(result.isOk());
        assertNotNull(result.getMsg());
    }

    /**
     * Lark signs inside the JSON body, so a signed request carries timestamp and sign fields there
     * and leaves the URL untouched.
     */
    @Test
    public void larkSigningShouldGoIntoTheRequestBody() {
        RobotConfigModel config = TestRobots.configModel(RobotType.LARK, larkWebhook.url(), null);
        config.setSign("secret-value");

        SendResult result = new LarkMessageSender(config)
                .sendText(BuildContext.builder().build(), textIntent(), LarkPayload.builder().build());

        assertTrue(result.isOk());
        assertTrue(larkWebhook.json().has("timestamp"));
        assertTrue(larkWebhook.json().has("sign"));
        assertFalse(larkWebhook.uri().contains("sign="));
    }
}
