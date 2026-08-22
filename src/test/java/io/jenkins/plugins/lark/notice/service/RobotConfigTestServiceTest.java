package io.jenkins.plugins.lark.notice.service;

import io.jenkins.plugins.lark.notice.enums.RobotProtocolType;
import io.jenkins.plugins.lark.notice.testing.TestRobots;
import io.jenkins.plugins.lark.notice.testing.WebhookServer;
import io.jenkins.plugins.lark.notice.tools.ApiResponse;
import net.sf.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests the robot configuration test button. It sends with values straight from the form, so the
 * important cases are the ones where the form and the saved configuration disagree — including a
 * robot that has never been saved.
 */
public class RobotConfigTestServiceTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Rule
    public WebhookServer webhook = WebhookServer.dingTalk();

    private static boolean isOk(ApiResponse response) {
        JSONObject json = response.toJson();
        return json.getBoolean("ok");
    }

    private static String message(ApiResponse response) {
        return response.toJson().optString("message");
    }

    private ApiResponse test(String webhookUrl) {
        return RobotConfigTestService.testRobotConfig("robot-a", "Robot A",
                RobotProtocolType.DING_TALK.name(), "FULL_WEBHOOK",
                webhookUrl, null, null, null, "[]", "AUTO");
    }

    /**
     * The main regression case: the robot does not exist in saved configuration at all, which is
     * what happens while a user is still filling the form.
     */
    @Test
    public void shouldSendForARobotThatWasNeverSaved() {
        TestRobots.install();

        ApiResponse response = test(webhook.url());

        assertTrue(message(response), isOk(response));
        assertEquals("actionCard", webhook.json().path("msgtype").asText());
    }

    /**
     * The saved robot carries a different protocol and webhook; neither may leak into the test send.
     */
    @Test
    public void formValuesShouldWinOverSavedConfiguration() {
        TestRobots.install("robot-a", RobotProtocolType.LARK_COMPATIBLE,
                "http://127.0.0.1:1/open-apis/bot/v2/hook/stale");

        ApiResponse response = test(webhook.url());

        assertTrue(message(response), isOk(response));
        // A DingTalk action card, sent to the form's webhook, not the saved Lark one.
        assertEquals("actionCard", webhook.json().path("msgtype").asText());
    }

    @Test
    public void unreachableWebhookShouldFailWithDetail() {
        TestRobots.install();

        ApiResponse response = test("http://127.0.0.1:1/robot/send?access_token=t");

        assertFalse(isOk(response));
        assertFalse(message(response).isEmpty());
    }

    @Test
    public void unsupportedWebhookShouldBeRejectedBeforeSending() {
        TestRobots.install();

        ApiResponse response = test("https://example.com/not-a-robot");

        assertFalse(isOk(response));
    }
}
