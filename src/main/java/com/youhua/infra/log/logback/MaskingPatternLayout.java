package com.youhua.infra.log.logback;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Pattern;

/**
 * 日志脱敏 PatternLayout，对输出中的敏感数据做正则替换（F-04 合规）。
 * <ul>
 *   <li>手机号 13812341234 → 138****1234</li>
 *   <li>身份证 310101199001011234 → 310***********1234</li>
 *   <li>银行卡 6222021234561234 → 6222****1234</li>
 * </ul>
 */
public class MaskingPatternLayout extends PatternLayout {

    /**
     * 手机号：1 开头 + 10 位数字，前后不能有数字（避免误匹配更长的数字串）
     */
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?<![\\d])(1[3-9]\\d)\\d{4}(\\d{4})(?![\\d])");

    /**
     * 身份证：18 位，前后不能有数字
     */
    private static final Pattern ID_CARD_PATTERN =
            Pattern.compile("(?<![\\d])(\\d{3})\\d{11}(\\d{4})(?![\\d])");

    /**
     * 银行卡：16~19 位纯数字，前后不能有数字
     */
    private static final Pattern BANK_CARD_PATTERN =
            Pattern.compile("(?<![\\d])(\\d{4})\\d{8,11}(\\d{4})(?![\\d])");

    @Override
    public String doLayout(ILoggingEvent event) {
        String message = super.doLayout(event);
        return maskSensitive(message);
    }

    /**
     * 对文本中的敏感数据做脱敏替换。static 方法供 MaskingJsonEncoder 复用。
     *
     * @param text 原始文本
     * @return 脱敏后的文本
     */
    public static String maskSensitive(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        // 顺序：先匹配最长的（身份证 18 位），再银行卡（16~19 位），最后手机号（11 位）
        String masked = ID_CARD_PATTERN.matcher(text).replaceAll("$1***********$2");
        masked = BANK_CARD_PATTERN.matcher(masked).replaceAll("$1****$2");
        masked = PHONE_PATTERN.matcher(masked).replaceAll("$1****$2");
        return masked;
    }
}
