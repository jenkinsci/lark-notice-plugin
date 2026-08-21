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

/**
 * End-to-end tests for the {@code lark} step covering the message types whose payload used to be
 * smuggled through the shared text field. The environment-variable assertions matter most: the old
 * path expanded the serialised blob as one string, so per-field expansion must keep working.
 */
public class LarkStepPipelineTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    private HttpServer server;

    private AtomicReference<String> requestBody;

    @Before
    public void setUp() throws IOException {
        requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/open-apis/bot/v2/hook/", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"code\":0,\"msg\":\"success\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        LarkRobotConfig robot = new LarkRobotConfig("robot-lark", "Lark",
                "http://localhost:" + server.getAddress().getPort() + "/open-apis/bot/v2/hook/token", List.of());
        robot.setProtocolType(RobotProtocolType.LARK_COMPATIBLE);
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
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "lark-" + System.nanoTime());
        job.setDefinition(new CpsFlowDefinition("lark " + args, true));
        jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0));
        return JsonUtils.readTree(requestBody.get());
    }

    @Test
    public void imageKeyShouldReachTheWireAndBeEnvironmentExpanded() throws Exception {
        JsonNode body = runAndCapture("robot: 'robot-lark', type: 'IMAGE', imageKey: 'img_${BUILD_NUMBER}'");

        assertEquals("image", body.path("msg_type").asText());
        assertEquals("img_1", body.path("content").path("image_key").asText());
    }

    @Test
    public void shareChatIdShouldReachTheWireAndBeEnvironmentExpanded() throws Exception {
        JsonNode body = runAndCapture("robot: 'robot-lark', type: 'SHARE_CHAT', shareChatId: 'oc_${BUILD_NUMBER}'");

        assertEquals("share_chat", body.path("msg_type").asText());
        assertEquals("oc_1", body.path("content").path("share_chat_id").asText());
    }

    @Test
    public void postSegmentValuesShouldBeEnvironmentExpanded() throws Exception {
        JsonNode body = runAndCapture("robot: 'robot-lark', type: 'POST', title: 'T', "
                + "post: [[[tag: 'text', text: 'build ${BUILD_NUMBER}'], "
                + "[tag: 'a', text: 'link', href: 'https://example.com/${BUILD_NUMBER}']]]");

        assertEquals("post", body.path("msg_type").asText());
        JsonNode content = body.path("content").path("post").path("zh_cn").path("content");
        assertEquals("build 1", content.get(0).get(0).path("text").asText());
        assertEquals("https://example.com/1", content.get(0).get(1).path("href").asText());
    }

    @Test
    public void atsShouldBeSupportedByTheLarkStep() throws Exception {
        JsonNode body = runAndCapture("robot: 'robot-lark', type: 'TEXT', text: ['hello'], "
                + "ats: ['ou_${BUILD_NUMBER}']");

        // Lark's renderer puts the mention markers on their own line after the body.
        assertEquals("hello\n<at id=ou_1></at>", body.path("content").path("text").asText());
    }
}
