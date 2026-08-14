package com.citicore.notification.util;

public class MaskingUtil {

    /**
     * Masks an account number, revealing first 4 and last 4 characters.
     * CITI000000000002 → CITI********0002
     */
    public static String maskAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 8) {
            return "****";
        }
        int visiblePrefix = 4;
        int visibleSuffix = 4;
        int maskLength = accountNumber.length() - visiblePrefix - visibleSuffix;

        String prefix = accountNumber.substring(0, visiblePrefix);
        String suffix = accountNumber.substring(accountNumber.length() - visibleSuffix);
        String mask   = "*".repeat(maskLength);

        return prefix + mask + suffix;
    }

    /**
     * Masks an email address, revealing first char, last char before @, and domain.
     * rabbanitechm@gmail.com → r**********m@gmail.com
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "****";
        }
        String[] parts  = email.split("@");
        String local    = parts[0];
        String domain   = parts[1];

        if (local.length() <= 2) {
            return local.charAt(0) + "***@" + domain;
        }
        return local.charAt(0)
                + "*".repeat(local.length() - 2)
                + local.charAt(local.length() - 1)
                + "@" + domain;
    }

    /**
     * Masks a phone number, revealing only the last 4 digits.
     * 9876543210 → ******3210
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        return "*".repeat(phone.length() - 4)
                + phone.substring(phone.length() - 4);
    }
}