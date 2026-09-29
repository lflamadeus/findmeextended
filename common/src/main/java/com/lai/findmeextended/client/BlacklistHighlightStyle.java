package com.lai.findmeextended.client;

import com.lai.findmeextended.FindMeMod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.Locale;
import java.util.Objects;

/**
 * 黑名单高亮的视觉参数：轮廓色 + 填充色（含不透明度）。
 * <p>
 * 单独抽出来，是为了让 {@link BlacklistHighlighter} 只负责「画」：它每帧都会被调用，不该同时承担
 * 「十六进制字符串 → 颜色」的解析和兜底。样式对象不可变，配置不变时直接复用上一次的结果，
 * 所以渲染热路径上既不解析字符串、也不产生新对象。
 * <p>
 * 只在客户端主线程（也就是渲染线程）上访问，因此不需要同步。
 */
public final class BlacklistHighlightStyle {

    private static final Logger LOGGER = LoggerFactory.getLogger("findmeextended");

    /** 配置缺失或无法解析时用的兜底色：亮黄轮廓 + 深灰填充。 */
    private static final Color FALLBACK_LINE = new Color(0xFF, 0xE5, 0x33);
    private static final Color FALLBACK_FILL = new Color(0x2D, 0x2D, 0x2D);

    private static BlacklistHighlightStyle cached;
    /** 生成 {@link #cached} 时的配置内容，用来判断缓存是否还有效。 */
    private static String cachedLineHex;
    private static String cachedFillHex;
    private static double cachedFillAlpha;

    private final Color line;
    private final Color fill;

    private BlacklistHighlightStyle(Color line, Color fill) {
        this.line = line;
        this.fill = fill;
    }

    /** 轮廓颜色，不透明。 */
    public Color line() {
        return line;
    }

    /** 填充颜色，不透明度已经烘进 alpha 通道。 */
    public Color fill() {
        return fill;
    }

    /** 当前配置对应的样式；配置没变时复用上一次解析的结果。 */
    public static BlacklistHighlightStyle current() {
        String lineHex = FindMeMod.CONFIG.CLIENT.BLACKLIST_HIGHLIGHT_COLOR;
        String fillHex = FindMeMod.CONFIG.CLIENT.BLACKLIST_FILL_COLOR;
        double fillAlpha = FindMeMod.CONFIG.CLIENT.BLACKLIST_FILL_ALPHA;
        BlacklistHighlightStyle style = cached;
        if (style != null
                && Objects.equals(lineHex, cachedLineHex)
                && Objects.equals(fillHex, cachedFillHex)
                && Double.compare(fillAlpha, cachedFillAlpha) == 0) {
            return style;
        }
        style = new BlacklistHighlightStyle(
                parse(lineHex, FALLBACK_LINE, 255),
                parse(fillHex, FALLBACK_FILL, toAlphaByte(fillAlpha)));
        cachedLineHex = lineHex;
        cachedFillHex = fillHex;
        cachedFillAlpha = fillAlpha;
        cached = style;
        return style;
    }

    /** 解析一个 {@code #RRGGBB} 颜色并套上指定的 alpha；解析失败时退回兜底色。 */
    private static Color parse(String hex, Color fallback, int alpha) {
        Color color = decode(hex);
        if (color == null) {
            if (hex != null) {
                LOGGER.warn("[FindMeExtended] 颜色值 {} 无法解析，改用默认值 #{}", hex,
                        Integer.toHexString(fallback.getRGB() & 0xFFFFFF));
            }
            color = fallback;
        }
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static Color decode(String hex) {
        if (hex == null) {
            return null;
        }
        try {
            return Color.decode(hex.trim().toLowerCase(Locale.ROOT));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 0~1 的不透明度换成 0~255；越界值和 NaN 都夹到边界。 */
    private static int toAlphaByte(double alpha) {
        if (!(alpha > 0.0D)) {
            return 0;
        }
        if (alpha >= 1.0D) {
            return 255;
        }
        return (int) Math.round(alpha * 255.0D);
    }
}
