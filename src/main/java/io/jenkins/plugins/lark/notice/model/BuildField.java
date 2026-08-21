package io.jenkins.plugins.lark.notice.model;

/**
 * One row of the standard build information block, in a medium-neutral form. Markdown bodies and
 * platform cards both render the same list of these, so the set of rows, their order, their labels
 * and which of them link somewhere is defined in exactly one place
 * ({@code BuildFields}) rather than once per output format.
 *
 * @param kind  semantic identity of the row, letting renderers decorate specific rows
 * @param label localised row label
 * @param value row value
 * @param url   link target, or {@code null} for a plain row
 * @author xm.z
 */
public record BuildField(Kind kind, String label, String value, String url) {

    /**
     * Semantic identity of a build information row.
     */
    public enum Kind {

        /**
         * Jenkins project the build belongs to.
         */
        PROJECT,

        /**
         * The individual build run.
         */
        JOB,

        /**
         * Build outcome.
         */
        STATUS,

        /**
         * Build duration.
         */
        DURATION,

        /**
         * Who or what triggered the build.
         */
        EXECUTOR
    }
}
