package com.youhua.infra.log.logback;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingPatternLayoutTest {

    @Test
    void should_mask_phone_number_when_valid_mobile() {
        String input = "用户手机号 13812341234 已验证";
        String result = MaskingPatternLayout.maskSensitive(input);
        assertThat(result).isEqualTo("用户手机号 138****1234 已验证");
    }

    @Test
    void should_mask_id_card_when_18_digits() {
        String input = "身份证号 310101199001011234 已录入";
        String result = MaskingPatternLayout.maskSensitive(input);
        assertThat(result).isEqualTo("身份证号 310***********1234 已录入");
    }

    @Test
    void should_mask_bank_card_when_16_digits() {
        String input = "银行卡 6222021234561234 绑定成功";
        String result = MaskingPatternLayout.maskSensitive(input);
        assertThat(result).isEqualTo("银行卡 6222****1234 绑定成功");
    }

    @Test
    void should_mask_bank_card_when_19_digits() {
        String input = "银行卡 6222021234567891234 绑定成功";
        String result = MaskingPatternLayout.maskSensitive(input);
        assertThat(result).isEqualTo("银行卡 6222****1234 绑定成功");
    }

    @Test
    void should_mask_multiple_sensitive_data_when_mixed_text() {
        String input = "用户 13800000001 身份证 310101200001011234 银行卡 6222021234561234";
        String result = MaskingPatternLayout.maskSensitive(input);
        assertThat(result).doesNotContain("13800000001");
        assertThat(result).doesNotContain("310101200001011234");
        assertThat(result).doesNotContain("6222021234561234");
        assertThat(result).contains("138****0001");
        assertThat(result).contains("310***********1234");
        assertThat(result).contains("6222****1234");
    }

    @Test
    void should_not_mask_when_short_number() {
        String input = "订单号 12345678 已创建";
        String result = MaskingPatternLayout.maskSensitive(input);
        assertThat(result).isEqualTo("订单号 12345678 已创建");
    }

    @Test
    void should_not_mask_when_number_embedded_in_longer_digit_string() {
        // 20 位数字不应被手机号或身份证匹配
        String input = "流水号 12345678901234567890 已生成";
        String result = MaskingPatternLayout.maskSensitive(input);
        assertThat(result).isEqualTo("流水号 12345678901234567890 已生成");
    }

    @Test
    void should_return_null_when_input_null() {
        assertThat(MaskingPatternLayout.maskSensitive(null)).isNull();
    }

    @Test
    void should_return_empty_when_input_empty() {
        assertThat(MaskingPatternLayout.maskSensitive("")).isEmpty();
    }

    @Test
    void should_not_mask_when_no_sensitive_data() {
        String input = "普通日志消息，没有敏感数据";
        String result = MaskingPatternLayout.maskSensitive(input);
        assertThat(result).isEqualTo(input);
    }
}
