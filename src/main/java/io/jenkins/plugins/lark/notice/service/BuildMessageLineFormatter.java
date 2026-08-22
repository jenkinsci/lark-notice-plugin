package io.jenkins.plugins.lark.notice.service;

import io.jenkins.plugins.lark.notice.enums.RobotType;
import io.jenkins.plugins.lark.notice.model.BuildField;
import io.jenkins.plugins.lark.notice.model.BuildFields;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders the standard build information rows as Markdown body lines. The rows themselves come from
 * {@link BuildFields}; this class only decides how each platform decorates them.
 */
public final class BuildMessageLineFormatter {

    private BuildMessageLineFormatter() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Builds the shared message body lines.
     *
     * @param locale              locale used for labels
     * @param robotType           robot platform type
     * @param values              message line values
     * @param includeBlankContent whether to append a blank content line when content is empty
     * @return ordered list of message lines
     */
    public static List<String> buildBodyLines(Locale locale,
                                              RobotType robotType,
                                              BuildMessageLineValues values,
                                              boolean includeBlankContent) {
        List<BuildField> fields = BuildFields.of(locale,
                values.projectName(), values.projectUrl(),
                values.jobName(), values.jobUrl(),
                values.statusLabel(),
                values.duration(), values.executorName());
        String statusColor = robotType.normalizeStatusColor(values.statusColor());
        boolean weCom = RobotType.WECHAT_WORK.equals(robotType);

        List<String> lines = new ArrayList<>();
        for (BuildField field : fields) {
            lines.add(weCom
                    ? formatWeComLine(field, statusColor)
                    : formatDefaultLine(field, statusColor, robotType.getStatusTagName()));
        }

        if (includeBlankContent) {
            lines.add(values.content() == null ? "" : values.content());
        } else if (StringUtils.isNotBlank(values.content())) {
            lines.add(values.content());
        }

        return lines;
    }

    private static String formatWeComLine(BuildField field, String statusColor) {
        if (field.url() != null) {
            return String.format(">**%s**: [%s](%s)", field.label(), field.value(), field.url());
        }
        if (BuildField.Kind.STATUS.equals(field.kind())) {
            return String.format(">**%s**: <font color=\"%s\">%s</font>",
                    field.label(), statusColor, field.value());
        }
        return String.format(">**%s**: %s", field.label(), field.value());
    }

    private static String formatDefaultLine(BuildField field, String statusColor, String tagName) {
        String icon = icon(field.kind());
        if (field.url() != null) {
            return String.format("%s **%s**: [%s](%s)", icon, field.label(), field.value(), field.url());
        }
        if (BuildField.Kind.STATUS.equals(field.kind())) {
            return String.format("%s **%s**:  <%s color='%s'>%s</%s>",
                    icon, field.label(), tagName, statusColor, field.value(), tagName);
        }
        return String.format("%s **%s**:  %s", icon, field.label(), field.value());
    }

    private static String icon(BuildField.Kind kind) {
        return switch (kind) {
            case PROJECT -> "📋";
            case JOB -> "🔢";
            case STATUS -> "🌟";
            case DURATION -> "🕐";
            case EXECUTOR -> "👤";
        };
    }

    /**
     * Message values used to format the shared body lines.
     *
     * @param projectName  Jenkins project display name
     * @param projectUrl   absolute project URL
     * @param jobName      build run display name
     * @param jobUrl       absolute run URL
     * @param statusLabel  build status, already localised
     * @param statusColor  status color used in the tag
     * @param duration     build duration
     * @param executorName who or what triggered the build
     * @param content      optional content line
     */
    public record BuildMessageLineValues(String projectName,
                                         String projectUrl,
                                         String jobName,
                                         String jobUrl,
                                         String statusLabel,
                                         String statusColor,
                                         String duration,
                                         String executorName,
                                         String content) {
    }
}
