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
import io.jenkins.plugins.lark.notice.model.CardField;
import io.jenkins.plugins.lark.notice.model.CardFieldModel;
import io.jenkins.plugins.lark.notice.sdk.model.lark.support.Button;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.*;

public class WechatWorkStepTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    private static void assertDefaultButton(Button button, String expectedText, String expectedUrlSuffix) {
        assertEquals(expectedText, button.getText());
        assertTrue(button.getUrl().endsWith(expectedUrlSuffix));
    }

    @Test
    public void sourceDescAndQuoteAreaShouldReachThePayload() throws Exception {
        LarkRobotConfig robot = new LarkRobotConfig("robot-wecom", "WeCom Robot",
                "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=token", List.of());
        robot.setProtocolType(RobotProtocolType.WECHAT_WORK);
        robot.setEndpointMode(WebhookEndpointMode.FULL_WEBHOOK);
        LarkGlobalConfig.getInstance().setRobotConfigs(new ArrayList<>(List.of(robot)));

        FreeStyleProject project = jenkins.createFreeStyleProject("wecom-quote-area");
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        WechatWorkStep step = new WechatWorkStep("robot-wecom", MsgTypeEnum.CARD);
        step.setSourceDesc("Jenkins ${BUILD_NUMBER}");
        step.setQuoteTitle("Release");
        step.setQuoteText("shipped ${BUILD_NUMBER}");
        step.setQuoteUrl("https://example.com/r/${BUILD_NUMBER}");
        EnvVars envVars = new EnvVars();
        envVars.put("BUILD_NUMBER", "7");

        WechatWorkStep.MessageBundle bundle = step.buildMessage(
                build, envVars, TaskListener.NULL, "robot-wecom");

        assertEquals("Jenkins 7", bundle.payload().getSourceDesc());
        assertEquals("Release", bundle.payload().getQuoteTitle());
        assertEquals("shipped 7", bundle.payload().getQuoteText());
        assertEquals("https://example.com/r/7", bundle.payload().getQuoteUrl());
    }

    @Test
    public void customCardFieldsShouldOverrideDefaultBuildRows() throws Exception {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);

            LarkRobotConfig robot = new LarkRobotConfig("robot-wecom", "WeCom Robot",
                    "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=token", List.of());
            robot.setProtocolType(RobotProtocolType.WECHAT_WORK);
            robot.setEndpointMode(WebhookEndpointMode.FULL_WEBHOOK);
            robot.setMessageLocaleStrategy(MessageLocaleStrategy.EN_US);
            LarkGlobalConfig.getInstance().setRobotConfigs(new ArrayList<>(List.of(robot)));

            FreeStyleProject project = jenkins.createFreeStyleProject("wecom-card-fields");
            FreeStyleBuild build = project.scheduleBuild2(0).get();

            WechatWorkStep step = new WechatWorkStep("robot-wecom", MsgTypeEnum.CARD);
            EnvVars envVars = new EnvVars();
            envVars.put("RELEASE_URL", "https://example.com/release/1");
            step.setCardFields(List.of(
                    new CardFieldModel("版本", "1.2.0", null),
                    new CardFieldModel("发布单", "详情", "${RELEASE_URL}")));

            WechatWorkStep.MessageBundle bundle = step.buildMessage(
                    build, envVars, TaskListener.NULL, "robot-wecom");

            List<CardField> fields = bundle.intent().getCardFields();
            assertNotNull(fields);
            assertEquals(2, fields.size());
            assertEquals("版本", fields.get(0).getKeyname());
            assertEquals("1.2.0", fields.get(0).getValue());
            assertEquals("发布单", fields.get(1).getKeyname());
            assertEquals("https://example.com/release/1", fields.get(1).getUrl());
        } finally {
            Locale.setDefault(previous);
        }
    }

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
}
