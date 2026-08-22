package io.jenkins.plugins.lark.notice.service;

import hudson.model.FreeStyleProject;
import io.jenkins.plugins.lark.notice.config.LarkNotifierConfig;
import io.jenkins.plugins.lark.notice.config.property.LarkJobProperty;
import io.jenkins.plugins.lark.notice.enums.RobotProtocolType;
import io.jenkins.plugins.lark.notice.testing.TestRobots;
import io.jenkins.plugins.lark.notice.tools.ApiResponse;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the standalone "robot job binding" management page: loading the job list with its filters,
 * and applying bind / unbind selections. This is the largest untested surface in the plugin and the
 * one that writes to other people's job configuration, so the round trip matters.
 */
@WithJenkins
public class RobotJobBindingServiceTest {
    private JenkinsRule jenkins;

    private static boolean isOk(ApiResponse response) {
        return response.toJson().getBoolean("ok");
    }

    private static JSONObject data(ApiResponse response) {
        return response.toJson().getJSONObject("data");
    }

    private static JSONArray rows(ApiResponse response) {
        return data(response).getJSONArray("jobs");
    }

    @BeforeEach
    public void installRobot(JenkinsRule rule) {
        this.jenkins = rule;
        TestRobots.install("robot-a", RobotProtocolType.DING_TALK,
                "http://127.0.0.1:1/robot/send?access_token=t");
    }

    private boolean isBound(FreeStyleProject job) {
        LarkJobProperty property = job.getProperty(LarkJobProperty.class);
        if (property == null) {
            return false;
        }
        return property.getLarkNotifierConfigs().stream()
                .anyMatch(config -> "robot-a".equals(config.getRobotId()) && config.isChecked());
    }

    @Test
    public void loadShouldRejectAnUnknownRobot() {
        ApiResponse response = RobotJobBindingService.load("nope", null, null);

        assertFalse(isOk(response));
    }

    @Test
    public void loadShouldListEveryJobAsUnboundInitially() throws Exception {
        jenkins.createFreeStyleProject("alpha");
        jenkins.createFreeStyleProject("beta");

        ApiResponse response = RobotJobBindingService.load("robot-a", null, null);

        assertTrue(isOk(response));
        JSONObject summary = data(response).getJSONObject("summary");
        assertEquals(2, summary.getInt("totalJobCount"));
        assertEquals(0, summary.getInt("boundJobCount"));
        assertEquals(2, summary.getInt("unboundJobCount"));
        assertEquals(2, rows(response).size());
    }

    @Test
    public void loadShouldFilterByKeyword() throws Exception {
        jenkins.createFreeStyleProject("alpha");
        jenkins.createFreeStyleProject("beta");

        JSONArray filtered = rows(RobotJobBindingService.load("robot-a", "alph", null));

        assertEquals(1, filtered.size());
        assertEquals("alpha", filtered.getJSONObject(0).getString("jobFullName"));
    }

    @Test
    public void applySelectionShouldRequireAtLeastOneJob() {
        ApiResponse response = RobotJobBindingService.applySelection("robot-a", "", "");

        assertFalse(isOk(response));
    }

    @Test
    public void bindShouldAttachAnEnabledNotifierConfigToTheJob() throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject("alpha");

        ApiResponse response = RobotJobBindingService.applySelection("robot-a", "alpha", "");

        assertTrue(isOk(response));
        assertEquals(1, data(response).getInt("changedJobCount"));
        assertTrue(isBound(job));

        List<LarkNotifierConfig> configs = job.getProperty(LarkJobProperty.class).getLarkNotifierConfigs();
        assertEquals(1, configs.size());
        assertEquals("robot-a", configs.get(0).getRobotId());
    }

    /**
     * Re-binding is idempotent in effect — applyBind only appends when the robot is absent — but the
     * report still counts it as changed rather than skipped.
     */
    @Test
    public void bindingAnAlreadyBoundJobShouldNotDuplicateTheConfig() throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject("alpha");
        RobotJobBindingService.applySelection("robot-a", "alpha", "");

        ApiResponse response = RobotJobBindingService.applySelection("robot-a", "alpha", "");

        assertTrue(isOk(response));
        assertEquals(1, job.getProperty(LarkJobProperty.class).getLarkNotifierConfigs().size());
        assertTrue(isBound(job));
    }

    @Test
    public void unbindShouldDetachTheConfigAgain() throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject("alpha");
        RobotJobBindingService.applySelection("robot-a", "alpha", "");
        assertTrue(isBound(job));

        ApiResponse response = RobotJobBindingService.applySelection("robot-a", "", "alpha");

        assertTrue(isOk(response));
        assertEquals(1, data(response).getInt("changedJobCount"));
        assertFalse(isBound(job));
    }

    @Test
    public void boundJobsShouldBeReportedByTheStateFilter() throws Exception {
        jenkins.createFreeStyleProject("alpha");
        jenkins.createFreeStyleProject("beta");
        RobotJobBindingService.applySelection("robot-a", "alpha", "");

        JSONArray bound = rows(RobotJobBindingService.load("robot-a", null, "bound"));
        JSONArray unbound = rows(RobotJobBindingService.load("robot-a", null, "unbound"));

        assertEquals(1, bound.size());
        assertEquals("alpha", bound.getJSONObject(0).getString("jobFullName"));
        assertEquals(1, unbound.size());
        assertEquals("beta", unbound.getJSONObject(0).getString("jobFullName"));
    }

    /**
     * A name that matches no job is reported as skipped, not failed, so the overall response still
     * succeeds — only a genuine IO error while writing job config counts as a failure.
     */
    @Test
    public void unknownJobNamesShouldBeSkippedNotFailed() throws Exception {
        jenkins.createFreeStyleProject("alpha");

        ApiResponse response = RobotJobBindingService.applySelection("robot-a", "alpha\nghost", "");

        assertNotNull(data(response));
        assertEquals(1, data(response).getInt("changedJobCount"));
        assertEquals(1, data(response).getInt("skippedJobCount"));
        assertEquals(0, data(response).getInt("failedJobCount"));
        assertTrue(isOk(response));
    }
}
