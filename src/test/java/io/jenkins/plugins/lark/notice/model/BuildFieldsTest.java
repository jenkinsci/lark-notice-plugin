package io.jenkins.plugins.lark.notice.model;

import io.jenkins.plugins.lark.notice.enums.BuildStatusEnum;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins the canonical build information rows, which both the Markdown formatter and the platform
 * card renderers derive from.
 */
public class BuildFieldsTest {

    @Test
    public void shouldExposeFiveRowsInAStableOrder() {
        BuildContext ctx = BuildContext.builder()
                .projectName("My Project").projectUrl("https://ci/job/p")
                .jobName("#7").jobUrl("https://ci/job/p/7")
                .statusType(BuildStatusEnum.SUCCESS)
                .duration("3.2 sec")
                .executorName("zhangsan")
                .locale(Locale.US)
                .build();

        List<BuildField> fields = BuildFields.of(ctx);

        assertEquals(5, fields.size());
        assertEquals(BuildField.Kind.PROJECT, fields.get(0).kind());
        assertEquals("Task Name", fields.get(0).label());
        assertEquals("My Project", fields.get(0).value());
        assertEquals("https://ci/job/p", fields.get(0).url());

        assertEquals(BuildField.Kind.JOB, fields.get(1).kind());
        assertEquals("Job Number", fields.get(1).label());

        assertEquals(BuildField.Kind.STATUS, fields.get(2).kind());
        assertEquals(BuildStatusEnum.SUCCESS.getLabel(Locale.US), fields.get(2).value());
        assertNull(fields.get(2).url());

        assertEquals(BuildField.Kind.DURATION, fields.get(3).kind());
        assertEquals("3.2 sec", fields.get(3).value());

        assertEquals(BuildField.Kind.EXECUTOR, fields.get(4).kind());
        assertEquals("zhangsan", fields.get(4).value());
        assertNull(fields.get(4).url());
    }

    @Test
    public void shouldRenderBlankStatusWhenContextHasNoOutcome() {
        BuildContext ctx = BuildContext.builder().locale(Locale.US).build();

        assertEquals("", BuildFields.of(ctx).get(2).value());
    }
}
