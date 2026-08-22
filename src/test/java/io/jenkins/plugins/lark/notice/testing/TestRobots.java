package io.jenkins.plugins.lark.notice.testing;

import io.jenkins.plugins.lark.notice.config.LarkGlobalConfig;
import io.jenkins.plugins.lark.notice.config.LarkRobotConfig;
import io.jenkins.plugins.lark.notice.enums.RobotProtocolType;
import io.jenkins.plugins.lark.notice.enums.RobotType;
import io.jenkins.plugins.lark.notice.enums.WebhookEndpointMode;
import io.jenkins.plugins.lark.notice.model.RobotConfigModel;
import io.jenkins.plugins.lark.notice.sdk.MessageSenderRegistry;
import io.jenkins.plugins.lark.notice.sdk.impl.DingMessageSender;
import io.jenkins.plugins.lark.notice.sdk.impl.LarkMessageSender;
import io.jenkins.plugins.lark.notice.sdk.impl.WechatWorkMessageSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builders for the robot configuration and sender boilerplate that most tests need. Keeping it here
 * means a change to how robots are configured is a one-line fix rather than a sweep across every
 * test class.
 */
public final class TestRobots {

    private TestRobots() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Builds a robot configuration without registering it.
     *
     * @param id       robot id
     * @param name     display name
     * @param protocol protocol family
     * @param webhook  webhook URL
     * @return robot configuration
     */
    public static LarkRobotConfig robot(String id, String name, RobotProtocolType protocol, String webhook) {
        LarkRobotConfig robot = new LarkRobotConfig(id, name, webhook, List.of());
        robot.setProtocolType(protocol);
        robot.setEndpointMode(WebhookEndpointMode.FULL_WEBHOOK);
        return robot;
    }

    /**
     * Replaces the saved global robot list and drops cached senders, so the next resolution reflects
     * exactly these robots.
     *
     * @param robots robots to install
     */
    public static void install(LarkRobotConfig... robots) {
        LarkGlobalConfig.getInstance().setRobotConfigs(new ArrayList<>(Arrays.asList(robots)));
        MessageSenderRegistry.getInstance().clear();
    }

    /**
     * Installs a single robot of the given protocol and returns it.
     *
     * @param id       robot id
     * @param protocol protocol family
     * @param webhook  webhook URL
     */
    public static void install(String id, RobotProtocolType protocol, String webhook) {
        LarkRobotConfig robot = robot(id, id, protocol, webhook);
        install(robot);
    }

    /**
     * Builds a runtime sender configuration.
     *
     * @param robotType robot platform
     * @param webhook   webhook URL
     * @param keys      security keyword, may be {@code null}
     * @return runtime configuration
     */
    public static RobotConfigModel configModel(RobotType robotType, String webhook, String keys) {
        RobotConfigModel model = new RobotConfigModel();
        model.setRobotType(robotType);
        model.setWebhook(webhook);
        model.setKeys(keys);
        return model;
    }

    /**
     * Creates a Lark sender pointed at a webhook.
     *
     * @param webhook webhook URL
     * @param keys    security keyword, may be {@code null}
     * @return Lark sender
     */
    public static LarkMessageSender larkSender(String webhook, String keys) {
        return new LarkMessageSender(configModel(RobotType.LARK, webhook, keys));
    }

    /**
     * Creates a DingTalk sender pointed at a webhook.
     *
     * @param webhook webhook URL
     * @param keys    security keyword, may be {@code null}
     * @return DingTalk sender
     */
    public static DingMessageSender dingSender(String webhook, String keys) {
        return new DingMessageSender(configModel(RobotType.DING_TALK, webhook, keys));
    }

    /**
     * Creates a WeCom sender pointed at a webhook.
     *
     * @param webhook webhook URL
     * @param keys    security keyword, may be {@code null}
     * @return WeCom sender
     */
    public static WechatWorkMessageSender weComSender(String webhook, String keys) {
        return new WechatWorkMessageSender(configModel(RobotType.WECHAT_WORK, webhook, keys));
    }
}
