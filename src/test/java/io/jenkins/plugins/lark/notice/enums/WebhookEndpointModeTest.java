package io.jenkins.plugins.lark.notice.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for webhook endpoint mode parsing behavior.
 */
public class WebhookEndpointModeTest {

    @Test
    public void fromValueShouldReturnNullForBlankOrUnknown() {
        assertNull(WebhookEndpointMode.fromValue(null));
        assertNull(WebhookEndpointMode.fromValue("  "));
        assertNull(WebhookEndpointMode.fromValue("UNKNOWN"));
    }

    @Test
    public void fromValueShouldTrimAndResolve() {
        assertEquals(WebhookEndpointMode.BASE_URL_AND_TOKEN, WebhookEndpointMode.fromValue(" BASE_URL_AND_TOKEN "));
    }
}
