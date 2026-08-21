package io.jenkins.plugins.lark.notice.model.payload;

import lombok.*;

/**
 * One row of a WeCom {@code news_notice} template card's {@code horizontal_content_list}.
 * <p>Fields default to text rows ({@link #TEXT_TYPE}); set {@link #url} to render a jump row
 * ({@link #LINK_TYPE}). Users may override the label/value of the built-in build rows or add
 * fully custom rows, which is what drives the "card fields are configurable" capability.
 *
 * @author xm.z
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardField {

    /**
     * Horizontal content type: plain text row.
     */
    public static final int TEXT_TYPE = 0;

    /**
     * Horizontal content type: jump row that opens {@link #url}.
     */
    public static final int LINK_TYPE = 1;

    /**
     * Row label, e.g. "任务名称".
     */
    private String keyname;

    /**
     * Row value.
     */
    private String value;

    /**
     * Jump URL. When blank the row is rendered as a plain text row.
     */
    private String url;

    /**
     * Resolves the content type from whether {@link #url} is set.
     *
     * @return {@link #TEXT_TYPE} or {@link #LINK_TYPE}
     */
    public int resolveType() {
        return (url == null || url.isBlank()) ? TEXT_TYPE : LINK_TYPE;
    }
}
