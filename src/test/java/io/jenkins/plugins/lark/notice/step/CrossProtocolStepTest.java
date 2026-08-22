package io.jenkins.plugins.lark.notice.step;

import hudson.model.Result;
import io.jenkins.plugins.lark.notice.config.LarkGlobalConfig;
import io.jenkins.plugins.lark.notice.config.LarkRobotConfig;
import io.jenkins.plugins.lark.notice.enums.RobotProtocolType;
import io.jenkins.plugins.lark.notice.enums.WebhookEndpointMode;
import io.jenkins.plugins.lark.notice.sdk.MessageSenderRegistry;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Rule;
import io.jenkins.plugins.lark.notice.testing.TestRobots;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies that pointing a platform-specific step at a robot of another protocol produces a
 * readable dispatcher failure instead of a {@link ClassCastException} escaping into the build log.
 */
public class CrossProtocolStepTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Before
    public void setUp() {
        TestRobots.install("robot-lark", RobotProtocolType.LARK_COMPATIBLE, "http://127.0.0.1:1/open-apis/bot/v2/hook/x");
    }

    @Test
    public void wechatWorkStepOnLarkRobotShouldFailWithReadableMessage() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "cross-fail");
        job.setDefinition(new CpsFlowDefinition(
                "wechatWork robot: 'robot-lark', type: 'TEXT', text: ['hi']", true));

        WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
        String log = run.getLog();

        assertFalse(log.contains("ClassCastException"));
        assertTrue(log.contains("does not match"));
        assertTrue(log.contains("LARK_COMPATIBLE"));
        assertTrue(log.contains("use the lark step"));
    }

    @Test
    public void wechatWorkStepOnLarkRobotShouldKeepBuildGreenWhenFailOnErrorIsFalse() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "cross-green");
        job.setDefinition(new CpsFlowDefinition(
                "wechatWork robot: 'robot-lark', type: 'TEXT', text: ['hi'], failOnError: false", true));

        jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0));
    }

    /**
     * failOnError must also cover unexpected exceptions thrown inside the send path, not just a
     * failed SendResult — an unknown robot id makes the step throw before any result exists.
     */
    @Test
    public void unexpectedExceptionShouldRespectFailOnError() throws Exception {
        WorkflowJob failing = jenkins.createProject(WorkflowJob.class, "exc-fail");
        failing.setDefinition(new CpsFlowDefinition(
                "lark robot: 'no-such-robot', type: 'TEXT', text: ['hi']", true));
        jenkins.assertBuildStatus(Result.FAILURE, failing.scheduleBuild2(0));

        WorkflowJob green = jenkins.createProject(WorkflowJob.class, "exc-green");
        green.setDefinition(new CpsFlowDefinition(
                "lark robot: 'no-such-robot', type: 'TEXT', text: ['hi'], failOnError: false", true));
        jenkins.assertBuildStatus(Result.SUCCESS, green.scheduleBuild2(0));
    }
}
