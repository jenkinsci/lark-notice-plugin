package io.jenkins.plugins.lark.notice.service;

import io.jenkins.plugins.lark.notice.enums.RobotType;
import io.jenkins.plugins.lark.notice.service.BuildMessageLineFormatter.BuildMessageLineValues;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the Markdown rendering of the standard build information rows. The row set itself is covered
 * by {@code BuildFieldsTest}; this covers the per-platform decoration, which differs in ways that
 * are easy to break silently — WeCom needs blockquote plus {@code <font>}, the others use icons and
 * a {@code text_tag}/{@code font} status tag with two spaces after the colon.
 */
public class BuildMessageLineFormatterTest {

    private static BuildMessageLineValues values(String content) {
        return new BuildMessageLineValues(
                "Demo Project", "https://ci/job/demo/",
                "#7", "https://ci/job/demo/7/",
                "Success", "green",
                "3.2 sec", "zhangsan",
                content);
    }

    @Test
    public void weComShouldUseBlockquoteRowsAndFontStatus() {
        List<String> lines = BuildMessageLineFormatter.buildBodyLines(
                Locale.US, RobotType.WECHAT_WORK, values(null), false);

        assertEquals(5, lines.size());
        assertEquals(">**Task Name**: [Demo Project](https://ci/job/demo/)", lines.get(0));
        assertEquals(">**Job Number**: [#7](https://ci/job/demo/7/)", lines.get(1));
        // WeCom does not know text_tag and normalizes green to its own "info" colour.
        assertEquals(">**Build Status**: <font color=\"info\">Success</font>", lines.get(2));
        assertEquals(">**Build Duration**: 3.2 sec", lines.get(3));
        assertEquals(">**Executor**: zhangsan", lines.get(4));
    }

    @Test
    public void larkShouldUseIconsAndATextTagStatus() {
        List<String> lines = BuildMessageLineFormatter.buildBodyLines(
                Locale.US, RobotType.LARK, values(null), false);

        assertEquals(5, lines.size());
        assertEquals("📋 **Task Name**: [Demo Project](https://ci/job/demo/)", lines.get(0));
        assertEquals("🔢 **Job Number**: [#7](https://ci/job/demo/7/)", lines.get(1));
        assertEquals("🌟 **Build Status**:  <text_tag color='green'>Success</text_tag>", lines.get(2));
        assertEquals("🕐 **Build Duration**:  3.2 sec", lines.get(3));
        assertEquals("👤 **Executor**:  zhangsan", lines.get(4));
    }

    @Test
    public void dingTalkShouldUseAFontStatusTag() {
        List<String> lines = BuildMessageLineFormatter.buildBodyLines(
                Locale.US, RobotType.DING_TALK, values(null), false);

        assertEquals("🌟 **Build Status**:  <font color='green'>Success</font>", lines.get(2));
    }

    @Test
    public void labelsShouldFollowTheRequestedLocale() {
        List<String> lines = BuildMessageLineFormatter.buildBodyLines(
                Locale.SIMPLIFIED_CHINESE, RobotType.LARK, values(null), false);

        assertTrue(lines.get(0).contains("**任务名称**"), lines.get(0));
        assertTrue(lines.get(4).contains("**执行者**"), lines.get(4));
    }

    @Test
    public void blankContentShouldBeAppendedOnlyWhenRequested() {
        assertEquals(5, BuildMessageLineFormatter.buildBodyLines(
                Locale.US, RobotType.LARK, values("   "), false).size());

        List<String> padded = BuildMessageLineFormatter.buildBodyLines(
                Locale.US, RobotType.LARK, values(null), true);
        assertEquals(6, padded.size());
        assertEquals("", padded.get(5));
    }

    @Test
    public void contentShouldBeAppendedAsTheLastLine() {
        List<String> lines = BuildMessageLineFormatter.buildBodyLines(
                Locale.US, RobotType.LARK, values("release notes"), false);

        assertEquals(6, lines.size());
        assertEquals("release notes", lines.get(5));
    }
}
