package com.youhua.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataUtilTest {

    @Test
    void should_mask_phone_with_standard_11_digits() {
        assertThat(SensitiveDataUtil.maskPhone("13812341234")).isEqualTo("138****1234");
        assertThat(SensitiveDataUtil.maskPhone("13600000001")).isEqualTo("136****0001");
    }

    @Test
    void should_return_stars_when_phone_null_or_short() {
        assertThat(SensitiveDataUtil.maskPhone(null)).isEqualTo("****");
        assertThat(SensitiveDataUtil.maskPhone("123")).isEqualTo("****");
        assertThat(SensitiveDataUtil.maskPhone("123456")).isEqualTo("****");
    }

    @Test
    void should_mask_phone_with_7_digit_minimum() {
        assertThat(SensitiveDataUtil.maskPhone("1234567")).isEqualTo("123****4567");
    }

    @Test
    void should_mask_id_card_with_18_digits() {
        assertThat(SensitiveDataUtil.maskIdCard("110101199003071234")).isEqualTo("110101****1234");
    }

    @Test
    void should_return_stars_when_id_card_null_or_short() {
        assertThat(SensitiveDataUtil.maskIdCard(null)).isEqualTo("****");
        assertThat(SensitiveDataUtil.maskIdCard("12345")).isEqualTo("****");
        assertThat(SensitiveDataUtil.maskIdCard("1234567")).isEqualTo("****");
    }

    @Test
    void should_mask_id_card_with_15_digit_old_format() {
        assertThat(SensitiveDataUtil.maskIdCard("110101900307123")).isEqualTo("110101****7123");
    }
}
