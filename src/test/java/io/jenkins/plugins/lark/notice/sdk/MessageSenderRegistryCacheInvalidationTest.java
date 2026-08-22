package io.jenkins.plugins.lark.notice.sdk;

import io.jenkins.plugins.lark.notice.config.LarkGlobalConfig;
import io.jenkins.plugins.lark.notice.config.LarkProxyConfig;
import io.jenkins.plugins.lark.notice.config.LarkRobotConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests cache invalidation behavior in {@link MessageSenderRegistry}.
 */
@WithJenkins
public class MessageSenderRegistryCacheInvalidationTest {
    private JenkinsRule jenkins;

    private static LarkRobotConfig createRobot(String id) {
        return new LarkRobotConfig(
                id,
                "Robot-" + id,
                "https://open.feishu.cn/open-apis/bot/v2/hook/" + id,
                List.of()
        );
    }

    @BeforeEach
    public void setUp(JenkinsRule rule) {
        this.jenkins = rule;
        MessageSenderRegistry.getInstance().clear();
        LarkGlobalConfig.getInstance().setRobotConfigs(new ArrayList<>(List.of(createRobot("robot-a"))));
        LarkGlobalConfig.getInstance().setProxyConfig(null);
    }

    @Test
    public void proxyConfigChangeShouldClearCachedSenders() {
        MessageSenderRegistry senderRegistry = MessageSenderRegistry.getInstance();

        senderRegistry.resolve("robot-a");
        assertEquals(1, senderRegistry.cacheSize());

        LarkGlobalConfig.getInstance().setProxyConfig(new LarkProxyConfig(Proxy.Type.HTTP, "127.0.0.1", 8080, true));
        assertEquals(0, senderRegistry.cacheSize());
    }

    @Test
    public void robotConfigChangeShouldClearCachedSendersAndRefreshLookup() {
        MessageSenderRegistry senderRegistry = MessageSenderRegistry.getInstance();

        senderRegistry.resolve("robot-a");
        assertEquals(1, senderRegistry.cacheSize());

        LarkGlobalConfig.getInstance().setRobotConfigs(new ArrayList<>(List.of(createRobot("robot-b"))));

        assertEquals(0, senderRegistry.cacheSize());
        assertFalse(LarkGlobalConfig.getRobot("robot-a").isPresent());
        assertTrue(LarkGlobalConfig.getRobot("robot-b").isPresent());
    }

    @Test
    public void getRobotConfigsShouldExposeReadOnlySnapshot() {
        List<LarkRobotConfig> robotConfigs = LarkGlobalConfig.getInstance().getRobotConfigs();

        assertEquals(1, robotConfigs.size());
        assertThrows(UnsupportedOperationException.class, () -> robotConfigs.add(createRobot("robot-c")));
        assertEquals(1, LarkGlobalConfig.getInstance().getRobotConfigs().size());
        assertTrue(LarkGlobalConfig.getRobot("robot-a").isPresent());
    }
}
