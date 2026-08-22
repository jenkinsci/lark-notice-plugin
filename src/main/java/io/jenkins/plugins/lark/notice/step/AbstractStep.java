package io.jenkins.plugins.lark.notice.step;

import com.google.common.collect.ImmutableSet;
import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.lark.notice.config.MessageLocaleResolver;
import io.jenkins.plugins.lark.notice.enums.BuildStatusEnum;
import io.jenkins.plugins.lark.notice.enums.MsgTypeEnum;
import io.jenkins.plugins.lark.notice.model.*;
import io.jenkins.plugins.lark.notice.sdk.MessageDispatcher;
import io.jenkins.plugins.lark.notice.sdk.model.SendResult;
import io.jenkins.plugins.lark.notice.sdk.model.lark.support.Button;
import io.jenkins.plugins.lark.notice.sdk.model.lark.support.form.TextElement;
import io.jenkins.plugins.lark.notice.sdk.model.lark.support.view.img.ImgElement;
import io.jenkins.plugins.lark.notice.sdk.model.lark.support.view.title.TitleElement;
import jenkins.model.Jenkins;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundSetter;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AbstractStep is an abstract class that extends the Step class.
 * It provides common functionality and properties for different types of steps.
 *
 * @author xm.z
 */
@Getter
public abstract class AbstractStep extends Step {

    protected String robot;

    protected MsgTypeEnum type;

    /**
     * User-defined card information rows. When set they replace the built-in build rows.
     */
    protected List<CardFieldModel> cardFields;

    /**
     * Whether a notification send failure should fail this pipeline step.
     * Defaults to {@code true} to preserve historical behavior; set to {@code false}
     * to keep the build green and only log a warning when sending fails.
     */
    protected boolean failOnError = true;

    public AbstractStep(String robot, MsgTypeEnum type) {
        this.robot = robot;
        this.type = type;
    }

    @DataBoundSetter
    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    @DataBoundSetter
    public void setCardFields(List<CardFieldModel> cardFields) {
        this.cardFields = cardFields;
    }

    /**
     * Expands and converts the configured card rows into payload rows.
     *
     * @param envVars environment variables
     * @return expanded rows, or {@code null} when none are configured
     */
    protected List<CardField> resolveCardFields(EnvVars envVars) {
        if (CollectionUtils.isEmpty(cardFields)) {
            return null;
        }
        return cardFields.stream()
                .map(model -> model.toCardField(value -> expandNullable(envVars, value)))
                .toList();
    }

    /**
     * Returns the dispatcher to send through.
     *
     * @return shared dispatcher
     */
    protected MessageDispatcher dispatcher() {
        return MessageDispatcher.getInstance();
    }

    /**
     * Builds the absolute URL of a build run. Resolved per send rather than once per step instance,
     * because a step object is created while the pipeline script is evaluated and the Jenkins root
     * URL can be reconfigured before the step actually runs.
     *
     * @param run build run
     * @return absolute run URL
     */
    protected String jobUrl(Run<?, ?> run) {
        return Jenkins.get().getRootUrl() + run.getUrl();
    }

    /**
     * Assembles and sends this step's message.
     *
     * @param run      build the step is running in
     * @param envVars  environment of that build, already merged with any Pipeline context
     * @param listener task listener the send is logged to
     * @return send outcome; implementations report failures here rather than throwing
     */
    protected abstract SendResult send(Run<?, ?> run, EnvVars envVars, TaskListener listener);

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new GenericStepExecution<>(this, context);
    }

    /**
     * Expands environment variables, passing {@code null} through untouched so an unset optional
     * parameter stays unset instead of becoming an empty string.
     *
     * @param envVars environment variables
     * @param value   raw value from the step arguments, may be {@code null}
     * @return expanded value, or {@code null}
     */
    protected String expandNullable(EnvVars envVars, String value) {
        return value == null ? null : envVars.expand(value);
    }

    /**
     * Converts a configured image into the Lark card element, expanding the text-bearing fields.
     *
     * @param envVars  environment variables
     * @param imgModel configured image, may be {@code null}
     * @return image element, or {@code null} when no image is configured
     */
    protected ImgElement buildImg(EnvVars envVars, ImgModel imgModel) {
        if (imgModel == null) {
            return null;
        }
        ImgElement imgElement = new ImgElement();
        imgElement.setImgKey(expandNullable(envVars, imgModel.getImgKey()));
        imgElement.setAlt(TextElement.of(expandNullable(envVars, imgModel.getAltContent())));
        imgElement.setTitle(TitleElement.buildPlainText(expandNullable(envVars, imgModel.getTitle())));
        imgElement.setCornerRadius(imgModel.getCornerRadius());
        imgElement.setScaleType(imgModel.getScaleType());
        imgElement.setSize(imgModel.getSize());
        imgElement.setTransparent(imgModel.getTransparent());
        imgElement.setPreview(imgModel.getPreview());
        return imgElement;
    }

    /**
     * Converts configured buttons into wire-level buttons, expanding titles and URLs.
     *
     * @param envVars environment variables
     * @param buttons configured buttons, may be empty
     * @return expanded buttons, or {@code null} when none are configured, which is what lets callers
     * distinguish "no buttons given" from "an empty list was given" and substitute defaults
     */
    protected List<Button> expandButtons(EnvVars envVars, List<ButtonModel> buttons) {
        if (CollectionUtils.isEmpty(buttons)) {
            return null;
        }
        return buttons.stream().map(item ->
                new Button(envVars.expand(item.getTitle()), envVars.expand(item.getUrl()), item.getType())
        ).collect(Collectors.toList());
    }

    /**
     * Expands environment variables in the @mention identifiers and drops blanks.
     *
     * @param envVars environment variables
     * @param ats     raw identifiers from the step arguments
     * @return expanded identifiers, never {@code null}
     */
    protected Set<String> expandAts(EnvVars envVars, Set<String> ats) {
        if (CollectionUtils.isEmpty(ats)) {
            return Set.of();
        }
        return ats.stream()
                .map(envVars::expand)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
    }

    /**
     * Resolves the locale to render built-in labels with, honouring the target robot's configured
     * message locale strategy. All steps must go through this rather than {@link Locale#getDefault()}
     * so the per-robot setting applies consistently.
     *
     * @param robotId resolved robot id
     * @return locale for this send
     */
    protected Locale resolveLocale(String robotId) {
        return MessageLocaleResolver.resolveForRobotId(robotId);
    }

    /**
     * Builds the shared {@link BuildContext} for a run.
     *
     * @param run      build run
     * @param listener task listener
     * @param status   build status
     * @param locale   locale used to render built-in labels
     * @return build context
     */
    protected BuildContext buildContext(Run<?, ?> run, TaskListener listener, BuildStatusEnum status, Locale locale) {
        RunUser executor = RunUser.getExecutor(run, listener);
        String jobUrl = jobUrl(run);
        return BuildContext.builder()
                .projectName(run.getParent().getFullDisplayName())
                .projectUrl(run.getParent().getAbsoluteUrl())
                .jobName(run.getDisplayName())
                .jobUrl(jobUrl)
                .statusType(status)
                .duration(run.getDurationString())
                .executorName(executor.getName())
                .executorMobile(executor.getMobile())
                .executorOpenId(executor.getOpenId())
                .locale(locale).build();
    }

    /**
     * Base StepDescriptor that provides the shared required-context set.
     */
    public abstract static class AbstractStepDescriptor extends StepDescriptor {

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Run.class, TaskListener.class, EnvVars.class);
        }
    }
}
