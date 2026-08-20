package io.jenkins.plugins.lark.notice.step.impl;

import hudson.EnvVars;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.TaskListener;
import io.jenkins.plugins.lark.notice.config.LarkGlobalConfig;
import io.jenkins.plugins.lark.notice.config.LarkRobotConfig;
import io.jenkins.plugins.lark.notice.enums.MessageLocaleStrategy;
import io.jenkins.plugins.lark.notice.enums.MsgTypeEnum;
import io.jenkins.plugins.lark.notice.enums.RobotProtocolType;
import io.jenkins.plugins.lark.notice.enums.WebhookEndpointMode;
import io.jenkins.plugins.lark.notice.i18n.NoticeI18n;
import io.jenkins.plugins.lark.notice.step.impl.WechatWorkStep.MessageBundle;
import io.jenkins.plugins.lark.notice.sdk.model.lark.support.Button;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WechatWorkStepTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void buildMessageShouldPopulateStructuredBuildFieldsForCardMessages() throws Exception {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);

            LarkRobotConfig robot = new LarkRobotConfig("robot-wecom", "WeCom Robot",
                    "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=token", List.of());
            robot.setProtocolType(RobotProtocolType.WECHAT_WORK);
            robot.setEndpointMode(WebhookEndpointMode.FULL_WEBHOOK);
            robot.setMessageLocaleStrategy(MessageLocaleStrategy.EN_US);
            LarkGlobalConfig.getInstance().setRobotConfigs(new ArrayList<>(List.of(robot)));

            FreeStyleProject project = jenkins.createFreeStyleProject("wecom-pipeline-step");
            FreeStyleBuild build = project.scheduleBuild2(0).get();

            WechatWorkStep step = new WechatWorkStep("robot-wecom", MsgTypeEnum.CARD);
            step.setText(List.of("line one", "line two"));

            WechatWorkStep.MessageBundle bundle = step.buildMessage(
                    build, new EnvVars(), TaskListener.NULL, "robot-wecom");

            assertNotNull(bundle);
            assertEquals("wecom-pipeline-step", bundle.ctx().getProjectName());
            assertEquals("#1", bundle.ctx().getJobName());
            assertEquals("line one\nline two", bundle.intent().getText());
            assertEquals("line one\nline two", bundle.payload().getAdditionalContent());
            assertEquals(Locale.US, bundle.ctx().getLocale());
            assertTrue(bundle.ctx().getJobUrl().endsWith("/job/wecom-pipeline-step/1/"));
            assertNotNull(bundle.intent().getButtons());
            assertEquals(2, bundle.intent().getButtons().size());
            assertDefaultButton(bundle.intent().getButtons().get(0), NoticeI18n.buildMessageButtonChangeLog(Locale.US), "/changes");
            assertDefaultButton(bundle.intent().getButtons().get(1), NoticeI18n.buildMessageButtonConsole(Locale.US), "/console");
            assertFalse(bundle.intent().isAtAll());
        } finally {
            Locale.setDefault(previous);
        }
    }

    private static void assertDefaultButton(Button button, String expectedText, String expectedUrlSuffix) {
        assertEquals(expectedText, button.getText());
        assertTrue(button.getUrl().endsWith(expectedUrlSuffix));
    }
}
