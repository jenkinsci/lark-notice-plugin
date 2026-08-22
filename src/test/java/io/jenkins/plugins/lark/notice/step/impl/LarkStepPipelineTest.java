package io.jenkins.plugins.lark.notice.step.impl;

import com.fasterxml.jackson.databind.JsonNode;
import hudson.model.Result;
import io.jenkins.plugins.lark.notice.enums.RobotProtocolType;
import io.jenkins.plugins.lark.notice.testing.TestRobots;
import io.jenkins.plugins.lark.notice.testing.WebhookServer;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests for the {@code lark} step covering the message types whose payload used to be
 * smuggled through the shared text field. The environment-variable assertions matter most: the old
 * path expanded the serialised blob as one string, so per-field expansion must keep working.
 */
@WithJenkins
public class LarkStepPipelineTest {
    @RegisterExtension
    final WebhookServer webhook = WebhookServer.lark();
    private JenkinsRule jenkins;

    @BeforeEach
    public void installRobot(JenkinsRule rule) {
        this.jenkins = rule;
        TestRobots.install("robot-lark", RobotProtocolType.LARK_COMPATIBLE, webhook.url());
    }

    private JsonNode runAndCapture(String args) throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "lark-" + System.nanoTime());
        job.setDefinition(new CpsFlowDefinition("lark " + args, true));
        jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0));
        return webhook.json();
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
    public void cardFieldsShouldRenderAsMarkdownBodyLines() throws Exception {
        JsonNode body = runAndCapture("robot: 'robot-lark', type: 'CARD', title: 'T', text: ['tail'], "
                + "cardFields: [[keyname: '\u7248\u672c', value: '1.2.0'], "
                + "[keyname: '\u53d1\u5e03\u5355', value: '\u8be6\u60c5', url: 'https://example.com/r/${BUILD_NUMBER}']]");

        assertEquals("interactive", body.path("msg_type").asText());
        String rendered = webhook.body();
        assertTrue(rendered.contains("**\u7248\u672c**: 1.2.0"));
        assertTrue(rendered.contains("[\u8be6\u60c5](https://example.com/r/1)"));
        // Free text stays, after the rows.
        assertTrue(rendered.contains("tail"));
    }

    @Test
    public void atsShouldBeSupportedByTheLarkStep() throws Exception {
        JsonNode body = runAndCapture("robot: 'robot-lark', type: 'TEXT', text: ['hello'], "
                + "ats: ['ou_${BUILD_NUMBER}']");

        // Lark's renderer puts the mention markers on their own line after the body.
        assertEquals("hello\n<at id=ou_1></at>", body.path("content").path("text").asText());
    }
}
