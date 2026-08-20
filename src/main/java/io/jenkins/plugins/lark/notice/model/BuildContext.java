package io.jenkins.plugins.lark.notice.model;

import io.jenkins.plugins.lark.notice.enums.BuildStatusEnum;
import lombok.Builder;
import lombok.Getter;

import java.util.Locale;

/**
 * Immutable build context shared across all robot platforms. Carries Jenkins build metadata
 * (project, job, status, duration, executor, URLs) that platforms may render into cards or
 * fold into Markdown. This is the source of truth for structured build fields and is decoupled
 * from any platform-specific message layout.
 *
 * @author xm.z
 */
@Getter
@Builder
public class BuildContext {

    /**
     * Full display name of the Jenkins project.
     */
    private final String projectName;

    /**
     * Absolute URL of the Jenkins project.
     */
    private final String projectUrl;

    /**
     * Display name of the Jenkins build run.
     */
    private final String jobName;

    /**
     * Absolute URL of the Jenkins build run.
     */
    private final String jobUrl;

    /**
     * Build outcome status.
     */
    private final BuildStatusEnum statusType;

    /**
     * Human-readable build duration.
     */
    private final String duration;

    /**
     * Display name of the build executor.
     */
    private final String executorName;

    /**
     * Mobile number of the executor, used for @mention by platforms that key on mobile.
     */
    private final String executorMobile;

    /**
     * Open id of the executor, used for @mention by platforms that key on open id.
     */
    private final String executorOpenId;

    /**
     * Locale used when rendering platform-specific structured card labels.
     */
    private final Locale locale;
}
