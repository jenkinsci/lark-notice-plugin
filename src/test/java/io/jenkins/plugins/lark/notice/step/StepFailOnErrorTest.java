package io.jenkins.plugins.lark.notice.step;

import hudson.model.Result;
import io.jenkins.plugins.lark.notice.enums.RobotProtocolType;
import io.jenkins.plugins.lark.notice.testing.TestRobots;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Verifies that the pipeline step {@code failOnError} parameter controls whether a send failure fails the build.
 *
 * @author xm.z
 */
@WithJenkins
public class StepFailOnErrorTest {
    private JenkinsRule jenkins;

    @BeforeEach
    public void setUp(JenkinsRule rule) {
        this.jenkins = rule;
        TestRobots.install("robot-unreachable", RobotProtocolType.LARK_COMPATIBLE, "http://127.0.0.1:1/open-apis/bot/v2/hook/x");
    }

    @Test
    public void stepShouldFailBuildByDefaultWhenSendFails() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "lark-fail-default");
        job.setDefinition(new CpsFlowDefinition(
                "lark robot: 'robot-unreachable', type: 'TEXT', text: ['hi']", true));
        jenkins.buildAndAssertStatus(Result.FAILURE, job);
    }

    @Test
    public void stepShouldKeepBuildGreenWhenFailOnErrorIsFalse() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "lark-keep-green");
        job.setDefinition(new CpsFlowDefinition(
                "lark robot: 'robot-unreachable', type: 'TEXT', text: ['hi'], failOnError: false", true));
        jenkins.buildAndAssertSuccess(job);
    }
}
