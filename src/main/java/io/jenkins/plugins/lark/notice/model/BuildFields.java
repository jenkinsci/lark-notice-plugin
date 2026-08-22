package io.jenkins.plugins.lark.notice.model;

import io.jenkins.plugins.lark.notice.enums.BuildStatusEnum;
import io.jenkins.plugins.lark.notice.i18n.NoticeI18n;

import java.util.List;
import java.util.Locale;

/**
 * Single source of truth for the standard build information rows: which rows exist, in what order,
 * what their labels are and which ones link somewhere. Both the Markdown body formatter and the
 * platform card renderers derive their output from {@link #of}, so a change here shows up
 * everywhere instead of having to be mirrored per output format.
 *
 * @author xm.z
 */
public final class BuildFields {

    private BuildFields() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Builds the standard rows from the shared build context.
     *
     * @param ctx build context
     * @return ordered rows, values may be blank when the context does not carry them
     */
    public static List<BuildField> of(BuildContext ctx) {
        Locale locale = ctx.getLocale();
        BuildStatusEnum statusType = ctx.getStatusType();
        return of(locale,
                ctx.getProjectName(), ctx.getProjectUrl(),
                ctx.getJobName(), ctx.getJobUrl(),
                statusType == null ? "" : statusType.getLabel(locale),
                ctx.getDuration(), ctx.getExecutorName());
    }

    /**
     * Builds the standard rows from raw values. Used by the template editor, which substitutes
     * environment variable placeholders rather than real build data.
     *
     * @param locale       locale used for the labels
     * @param projectName  Jenkins project display name
     * @param projectUrl    absolute project URL, or {@code null} for a non-linking row
     * @param jobName       build run display name
     * @param jobUrl        absolute run URL, or {@code null} for a non-linking row
     * @param statusLabel   build status, already localised by the caller
     * @param duration     build duration
     * @param executorName  who or what triggered the build
     * @return ordered rows
     */
    public static List<BuildField> of(Locale locale,
                                      String projectName, String projectUrl,
                                      String jobName, String jobUrl,
                                      String statusLabel,
                                      String duration, String executorName) {
        return List.of(
                new BuildField(BuildField.Kind.PROJECT,
                        NoticeI18n.buildMessageProjectName(locale), projectName, projectUrl),
                new BuildField(BuildField.Kind.JOB,
                        NoticeI18n.buildMessageJobName(locale), jobName, jobUrl),
                new BuildField(BuildField.Kind.STATUS,
                        NoticeI18n.buildMessageStatus(locale), statusLabel, null),
                new BuildField(BuildField.Kind.DURATION,
                        NoticeI18n.buildMessageDuration(locale), duration, null),
                new BuildField(BuildField.Kind.EXECUTOR,
                        NoticeI18n.buildMessageExecutor(locale), executorName, null));
    }
}
