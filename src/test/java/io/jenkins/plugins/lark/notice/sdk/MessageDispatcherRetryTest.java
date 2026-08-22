package io.jenkins.plugins.lark.notice.sdk;

import io.jenkins.plugins.lark.notice.config.LarkRetryConfig;
import io.jenkins.plugins.lark.notice.enums.MsgTypeEnum;
import io.jenkins.plugins.lark.notice.model.BuildContext;
import io.jenkins.plugins.lark.notice.model.MessageIntent;
import io.jenkins.plugins.lark.notice.model.payload.PlatformPayload;
import io.jenkins.plugins.lark.notice.model.payload.WeComPayload;
import io.jenkins.plugins.lark.notice.sdk.model.SendResult;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behavioral tests for retry handling in {@link MessageDispatcher}.
 */
public class MessageDispatcherRetryTest {

    private static MessageIntent textIntent() {
        return MessageIntent.builder().type(MsgTypeEnum.TEXT).text("hello").build();
    }

    private static BuildContext context() {
        return BuildContext.builder().build();
    }

    private static PlatformPayload payload() {
        return WeComPayload.builder().build();
    }

    @Test
    public void shouldRetryUntilSuccess() {
        RetryPolicy policy = RetryPolicy.from(new LarkRetryConfig(true, 3, 0, 0, 1.0, 0.0));
        AtomicInteger attempts = new AtomicInteger();
        MessageSender<PlatformPayload> sender = new MessageSender<>() {
            @Override
            public Class<PlatformPayload> payloadType() {
                return PlatformPayload.class;
            }

            @Override
            public SendResult sendText(BuildContext ctx, MessageIntent intent, PlatformPayload payload) {
                int count = attempts.incrementAndGet();
                return count < 3 ? SendResult.fail("fail") : new SendResult(0, "ok", null);
            }

            @Override
            public SendResult sendMarkdown(BuildContext ctx, MessageIntent intent, PlatformPayload payload) {
                return sendText(ctx, intent, payload);
            }
        };

        SendResult result = MessageDispatcher.getInstance().send(null, context(), textIntent(), payload(),
                new DispatchTarget(null, null, null, policy, sender));
        assertTrue(result.isOk());
        assertEquals(3, attempts.get());
    }

    @Test
    public void shouldStopAfterMaxAttempts() {
        RetryPolicy policy = RetryPolicy.from(new LarkRetryConfig(true, 2, 0, 0, 1.0, 0.0));
        AtomicInteger attempts = new AtomicInteger();
        MessageSender<PlatformPayload> sender = new MessageSender<>() {
            @Override
            public Class<PlatformPayload> payloadType() {
                return PlatformPayload.class;
            }

            @Override
            public SendResult sendText(BuildContext ctx, MessageIntent intent, PlatformPayload payload) {
                attempts.incrementAndGet();
                return SendResult.fail("fail");
            }

            @Override
            public SendResult sendMarkdown(BuildContext ctx, MessageIntent intent, PlatformPayload payload) {
                return sendText(ctx, intent, payload);
            }
        };

        SendResult result = MessageDispatcher.getInstance().send(null, context(), textIntent(), payload(),
                new DispatchTarget(null, null, null, policy, sender));
        assertFalse(result.isOk());
        assertEquals(2, attempts.get());
    }

}
