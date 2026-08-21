package io.jenkins.plugins.lark.notice.sdk;

import io.jenkins.plugins.lark.notice.config.LarkGlobalConfig;
import io.jenkins.plugins.lark.notice.config.LarkRobotConfig;
import io.jenkins.plugins.lark.notice.enums.MsgTypeEnum;
import io.jenkins.plugins.lark.notice.enums.RobotProtocolType;
import io.jenkins.plugins.lark.notice.enums.RobotType;
import io.jenkins.plugins.lark.notice.enums.WebhookEndpointMode;
import io.jenkins.plugins.lark.notice.model.BuildContext;
import io.jenkins.plugins.lark.notice.model.MessageIntent;
import io.jenkins.plugins.lark.notice.model.RobotConfigModel;
import io.jenkins.plugins.lark.notice.model.payload.DingPayload;
import io.jenkins.plugins.lark.notice.sdk.impl.DingMessageSender;
import io.jenkins.plugins.lark.notice.sdk.model.SendResult;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The robot configuration test button sends with values straight from the form, which may differ
 * from what is saved. These tests pin that an explicitly supplied {@link DispatchTarget} wins over
 * the persisted robot, so the support check and retry behaviour match the sender being tested.
 */
public class DispatchTargetTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    private static DingMessageSender dingSender() {
        RobotConfigModel model = new RobotConfigModel();
        model.setRobotType(RobotType.DING_TALK);
        model.setWebhook("http://127.0.0.1:1/robot/send?access_token=t");
        return new DingMessageSender(model);
    }

    /**
     * A robot saved as Lark supports SHARE_CHAT; the target says DingTalk, which does not. The
     * failure must come from the target's protocol, proving the saved config is not consulted.
     */
    @Test
    public void explicitTargetProtocolShouldWinOverSavedConfiguration() {
        LarkRobotConfig saved = new LarkRobotConfig("robot-x", "Saved As Lark",
                "http://127.0.0.1:1/open-apis/bot/v2/hook/x", List.of());
        saved.setProtocolType(RobotProtocolType.LARK_COMPATIBLE);
        saved.setEndpointMode(WebhookEndpointMode.FULL_WEBHOOK);
        LarkGlobalConfig.getInstance().setRobotConfigs(new ArrayList<>(List.of(saved)));

        MessageIntent intent = MessageIntent.builder().type(MsgTypeEnum.SHARE_CHAT).text("x").build();
        DispatchTarget target = new DispatchTarget("robot-x", "Form Name",
                RobotProtocolType.DING_TALK, null, dingSender());

        SendResult result = MessageDispatcher.getInstance().send(null, BuildContext.builder().build(),
                intent, DingPayload.builder().build(), target);

        assertFalse(result.isOk());
        assertTrue(result.getMsg().contains("DING_TALK"));
    }

    @Test
    public void resolveTargetShouldReflectSavedConfiguration() {
        LarkRobotConfig saved = new LarkRobotConfig("robot-y", "Saved Robot",
                "http://127.0.0.1:1/robot/send?access_token=t", List.of());
        saved.setProtocolType(RobotProtocolType.DING_TALK);
        saved.setEndpointMode(WebhookEndpointMode.FULL_WEBHOOK);
        LarkGlobalConfig.getInstance().setRobotConfigs(new ArrayList<>(List.of(saved)));
        MessageSenderRegistry.getInstance().clear();

        DispatchTarget target = MessageDispatcher.getInstance().resolveTarget("robot-y");

        assertTrue(target.sender() instanceof DingMessageSender);
        assertTrue(RobotProtocolType.DING_TALK.equals(target.protocol()));
        assertTrue("Saved Robot".equals(target.robotName()));
    }

    @Test
    public void missingRobotShouldYieldATargetWithoutASender() {
        LarkGlobalConfig.getInstance().setRobotConfigs(new ArrayList<>());
        MessageSenderRegistry.getInstance().clear();

        DispatchTarget target = MessageDispatcher.getInstance().resolveTarget("nope");

        assertFalse(target.sender() != null);
    }
}
