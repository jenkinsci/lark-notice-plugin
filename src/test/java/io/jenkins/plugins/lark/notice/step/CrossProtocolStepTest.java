package io.jenkins.plugins.lark.notice.step;

import hudson.model.Result;
import io.jenkins.plugins.lark.notice.enums.RobotProtocolType;
import io.jenkins.plugins.lark.notice.testing.TestRobots;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that pointing a platform-specific step at a robot of another protocol produces a
 * readable dispatcher failure instead of a {@link ClassCastException} escaping into the build log.
 */
@WithJenkins
public class CrossProtocolStepTest {
    private JenkinsRule jenkins;

    @BeforeEach
    public void setUp(JenkinsRule rule) {
        this.jenkins = rule;
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
        // The wording is localised, so assert on the parts that never are: the payload type that
        // did not fit, the robot's actual protocol, and the step the user should switch to.
        assertTrue(log.contains("WeComPayload"), log);
        assertTrue(log.contains("LarkPayload"), log);
        assertTrue(log.contains("LARK_COMPATIBLE"), log);
        assertTrue(log.contains("robot-lark"), log);
        assertTrue(log.contains("lark"), log);
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
