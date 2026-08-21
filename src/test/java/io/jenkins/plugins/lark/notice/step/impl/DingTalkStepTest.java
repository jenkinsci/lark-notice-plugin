package io.jenkins.plugins.lark.notice.step.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import hudson.model.Result;
import io.jenkins.plugins.lark.notice.config.LarkGlobalConfig;
import io.jenkins.plugins.lark.notice.config.LarkRobotConfig;
import io.jenkins.plugins.lark.notice.enums.RobotProtocolType;
import io.jenkins.plugins.lark.notice.enums.WebhookEndpointMode;
import io.jenkins.plugins.lark.notice.sdk.MessageSenderRegistry;
import io.jenkins.plugins.lark.notice.tools.JsonUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end baseline tests for the {@code dingTalk} pipeline step: script arguments through the
 * dispatcher and sender, asserted against the real captured request body. These pin the wire
 * format across step-layer refactors.
 */
public class DingTalkStepTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

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

        LarkRobotConfig robot = new LarkRobotConfig("robot-ding", "DingTalk",
                "http://localhost:" + server.getAddress().getPort() + "/robot/send?access_token=t", List.of());
        robot.setProtocolType(RobotProtocolType.DING_TALK);
        robot.setEndpointMode(WebhookEndpointMode.FULL_WEBHOOK);
        LarkGlobalConfig.getInstance().setRobotConfigs(new ArrayList<>(List.of(robot)));
        MessageSenderRegistry.getInstance().clear();
    }

    @After
    public void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private JsonNode runAndCapture(String args) throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "ding-" + System.nanoTime());
        job.setDefinition(new CpsFlowDefinition("dingTalk " + args, true));
        jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0));
        return JsonUtils.readTree(requestBody.get());
    }

    @Test
    public void textMessageShouldMatchRobotApiShape() throws Exception {
        JsonNode body = runAndCapture("robot: 'robot-ding', type: 'TEXT', text: ['hello'], "
                + "ats: ['13800138000', 'user001'], atAll: false");

        assertEquals("text", body.path("msgtype").asText());
        assertTrue(body.path("text").path("content").asText().startsWith("hello"));
        assertEquals("13800138000", body.path("at").path("atMobiles").get(0).asText());
        assertEquals("user001", body.path("at").path("atUserIds").get(0).asText());
        assertFalse(body.path("at").path("isAtAll").asBoolean());
    }

    @Test
    public void linkMessageShouldUseCamelCaseUrlFields() throws Exception {
        JsonNode body = runAndCapture("robot: 'robot-ding', type: 'LINK', title: 'T', text: ['body'], "
                + "picUrl: 'https://example.com/p.png', messageUrl: 'https://example.com/b'");

        JsonNode link = body.path("link");
        assertEquals("link", body.path("msgtype").asText());
        // Official robot API spells these messageUrl / picUrl (lowercase url), unlike feedCard.
        assertEquals("https://example.com/b", link.path("messageUrl").asText());
        assertEquals("https://example.com/p.png", link.path("picUrl").asText());
    }

    /**
     * A CARD without explicit buttons and without singleTitle gets the default changelog/console
     * buttons, which is the branch the sender actually renders.
     */
    @Test
    public void cardWithoutButtonsShouldGetDefaultButtons() throws Exception {
        JsonNode card = runAndCapture("robot: 'robot-ding', type: 'CARD', title: 'T', text: ['body']")
                .path("actionCard");

        assertEquals(2, card.path("btns").size());
        assertTrue(card.path("btns").get(0).path("actionURL").asText().endsWith("changes"));
        assertTrue(card.path("btns").get(1).path("actionURL").asText().endsWith("console"));
        assertTrue(card.path("singleTitle").isMissingNode() || card.path("singleTitle").isNull());
    }

    @Test
    public void cardWithExplicitButtonsShouldUseBtnsWithActionUrl() throws Exception {
        JsonNode card = runAndCapture("robot: 'robot-ding', type: 'CARD', title: 'T', text: ['body'], "
                + "buttons: [[title: 'Changes', url: 'https://example.com/c']], verticalButton: true")
                .path("actionCard");

        assertEquals("Changes", card.path("btns").get(0).path("title").asText());
        assertEquals("https://example.com/c", card.path("btns").get(0).path("actionURL").asText());
        assertEquals("0", card.path("btnOrientation").asText());
    }

    /**
     * With singleTitle set the sender renders a single jump action and ignores buttons, so the step
     * must not synthesise defaults, and the jump target must use the official singleURL spelling.
     */
    @Test
    public void cardWithSingleTitleShouldEmitSingleUrlAndNoButtons() throws Exception {
        JsonNode card = runAndCapture("robot: 'robot-ding', type: 'CARD', title: 'T', text: ['body'], "
                + "singleTitle: 'Open', singleUrl: 'https://example.com/b'")
                .path("actionCard");

        assertEquals("Open", card.path("singleTitle").asText());
        assertEquals("https://example.com/b", card.path("singleURL").asText());
        assertTrue(card.path("singleUrl").isMissingNode());
        assertTrue(card.path("btns").isMissingNode() || card.path("btns").isNull());
    }

    @Test
    public void atsShouldBeEnvironmentExpanded() throws Exception {
        JsonNode body = runAndCapture("robot: 'robot-ding', type: 'TEXT', text: ['hello'], "
                + "ats: ['${JOB_NAME}']");

        assertTrue(body.path("at").path("atUserIds").get(0).asText().startsWith("ding-"));
    }
}
