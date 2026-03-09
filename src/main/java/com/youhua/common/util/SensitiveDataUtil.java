package com.youhua.common.util;

/**
 * Utility for masking sensitive data in logs and API responses (F-04 compliance).
 */
public final class SensitiveDataUtil {

    private SensitiveDataUtil() {
    }

    /**
     * Mask phone number: 13812341234 → 138****1234
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * Mask ID card number: 110101199003071234 → 110101****1234
     */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 8) {
            return "****";
        }
        return idCard.substring(0, 6) + "****" + idCard.substring(idCard.length() - 4);
    }
}
