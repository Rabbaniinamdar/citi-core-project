package com.citicore.notification.template;

import com.citicore.notification.util.MaskingUtil;

import java.math.BigDecimal;

public class AccountEmailTemplate {

    /**
     * Welcome email sent when a new account is created.
     */
    public static String accountCreatedTemplate(
            String email,
            String accountNumber,
            BigDecimal initialDeposit
    ) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
                <body style="margin:0;padding:0;font-family:Arial,sans-serif;background:#f5f5f5;">

                <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f5f5f5;padding:30px 0;">
                  <tr>
                    <td align="center">
                      <table width="600" cellpadding="0" cellspacing="0"
                             style="background:#ffffff;border-radius:10px;overflow:hidden;
                                    box-shadow:0 2px 8px rgba(0,0,0,0.08);">

                        <!-- Header -->
                        <tr>
                          <td style="background:#1a73e8;padding:28px 40px;">
                            <h1 style="margin:0;color:#ffffff;font-size:22px;font-weight:700;">
                              🏦 CitiCore Banking
                            </h1>
                          </td>
                        </tr>

                        <!-- Body -->
                        <tr>
                          <td style="padding:36px 40px;">
                            <h2 style="margin:0 0 16px;color:#1a1a1a;font-size:22px;">
                              🎉 Welcome to CitiCore!
                            </h2>
                            <p style="margin:0 0 24px;color:#555555;font-size:15px;line-height:1.6;">
                              Dear <strong>%s</strong>,<br><br>
                              Congratulations! Your CitiCore bank account has been created successfully.
                              We are thrilled to have you on board.
                            </p>

                            <!-- Account Details -->
                            <table width="100%%" cellpadding="12" cellspacing="0"
                                   style="border:1px solid #e8e8e8;border-radius:8px;border-collapse:collapse;">
                              <tr style="background:#f9fbff;">
                                <td style="color:#666666;font-size:14px;border-bottom:1px solid #e8e8e8;">
                                  Account Number
                                </td>
                                <td style="font-weight:700;font-size:15px;border-bottom:1px solid #e8e8e8;">
                                  %s
                                </td>
                              </tr>
                              <tr>
                                <td style="color:#666666;font-size:14px;">Opening Deposit</td>
                                <td style="font-weight:700;font-size:15px;color:#1a8a1a;">₹ %s</td>
                              </tr>
                            </table>

                            <p style="margin:24px 0 0;padding:14px 16px;background:#fff8e1;
                                      border-left:4px solid #f9a825;border-radius:4px;
                                      font-size:13px;color:#7a6000;">
                              🔒 Please keep your account number safe.
                              Never share it with anyone you don't trust.
                            </p>
                            <p style="margin:20px 0 0;color:#555555;font-size:14px;">
                              For support, contact us at
                              <a href="mailto:support@citicore.com"
                                 style="color:#1a73e8;">support@citicore.com</a>.
                            </p>
                          </td>
                        </tr>

                        <!-- Footer -->
                        <tr>
                          <td style="background:#f9f9f9;padding:20px 40px;border-top:1px solid #eeeeee;">
                            <p style="margin:0;font-size:12px;color:#999999;text-align:center;">
                              This is an automated message from CitiCore Banking Platform.<br>
                              &copy; 2026 CitiCore. All rights reserved.
                            </p>
                          </td>
                        </tr>

                      </table>
                    </td>
                  </tr>
                </table>

                </body>
                </html>
                """.formatted(
                email,
                MaskingUtil.maskAccount(accountNumber),
                initialDeposit
        );
    }

    /**
     * Debit alert sent when money is debited from an account.
     */
    public static String debitTemplate(
            String email,
            BigDecimal amount,
            String txnRef,
            String accountNumber
    ) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
                <body style="margin:0;padding:0;font-family:Arial,sans-serif;background:#f5f5f5;">

                <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f5f5f5;padding:30px 0;">
                  <tr>
                    <td align="center">
                      <table width="600" cellpadding="0" cellspacing="0"
                             style="background:#ffffff;border-radius:10px;overflow:hidden;
                                    box-shadow:0 2px 8px rgba(0,0,0,0.08);">

                        <!-- Header -->
                        <tr>
                          <td style="background:#d93025;padding:28px 40px;">
                            <h1 style="margin:0;color:#ffffff;font-size:22px;font-weight:700;">
                              🏦 CitiCore Banking
                            </h1>
                          </td>
                        </tr>

                        <!-- Body -->
                        <tr>
                          <td style="padding:36px 40px;">
                            <h2 style="margin:0 0 16px;color:#d93025;font-size:22px;">
                              🔴 Debit Alert
                            </h2>
                            <p style="margin:0 0 24px;color:#555555;font-size:15px;line-height:1.6;">
                              Dear <strong>%s</strong>,<br><br>
                              A debit transaction has been processed on your CitiCore account.
                            </p>

                            <!-- Transaction Details -->
                            <table width="100%%" cellpadding="12" cellspacing="0"
                                   style="border:1px solid #e8e8e8;border-radius:8px;border-collapse:collapse;">
                              <tr style="background:#fff5f5;">
                                <td style="color:#666666;font-size:14px;border-bottom:1px solid #e8e8e8;">
                                  Amount Debited
                                </td>
                                <td style="font-weight:700;font-size:16px;color:#d93025;
                                           border-bottom:1px solid #e8e8e8;">
                                  ₹ %s
                                </td>
                              </tr>
                              <tr>
                                <td style="color:#666666;font-size:14px;border-bottom:1px solid #e8e8e8;">
                                  Account
                                </td>
                                <td style="font-weight:600;font-size:15px;border-bottom:1px solid #e8e8e8;">
                                  %s
                                </td>
                              </tr>
                              <tr>
                                <td style="color:#666666;font-size:14px;">Reference</td>
                                <td style="font-size:14px;color:#888888;">%s</td>
                              </tr>
                            </table>

                            <p style="margin:24px 0 0;padding:14px 16px;background:#fff3f3;
                                      border-left:4px solid #d93025;border-radius:4px;
                                      color:#d93025;font-size:13px;">
                              ⚠️ If you did not authorise this transaction, please contact
                              <a href="mailto:support@citicore.com"
                                 style="color:#d93025;font-weight:700;">support@citicore.com</a>
                              immediately or call our 24/7 helpline.
                            </p>
                          </td>
                        </tr>

                        <!-- Footer -->
                        <tr>
                          <td style="background:#f9f9f9;padding:20px 40px;border-top:1px solid #eeeeee;">
                            <p style="margin:0;font-size:12px;color:#999999;text-align:center;">
                              This is an automated message from CitiCore Banking Platform.<br>
                              &copy; 2026 CitiCore. All rights reserved.
                            </p>
                          </td>
                        </tr>

                      </table>
                    </td>
                  </tr>
                </table>

                </body>
                </html>
                """.formatted(
                email,
                amount,
                MaskingUtil.maskAccount(accountNumber),
                txnRef
        );
    }

    /**
     * Credit alert sent when money is credited to an account.
     */
    public static String creditTemplate(
            String email,
            BigDecimal amount,
            String txnRef,
            String accountNumber
    ) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
                <body style="margin:0;padding:0;font-family:Arial,sans-serif;background:#f5f5f5;">

                <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f5f5f5;padding:30px 0;">
                  <tr>
                    <td align="center">
                      <table width="600" cellpadding="0" cellspacing="0"
                             style="background:#ffffff;border-radius:10px;overflow:hidden;
                                    box-shadow:0 2px 8px rgba(0,0,0,0.08);">

                        <!-- Header -->
                        <tr>
                          <td style="background:#1a8a1a;padding:28px 40px;">
                            <h1 style="margin:0;color:#ffffff;font-size:22px;font-weight:700;">
                              🏦 CitiCore Banking
                            </h1>
                          </td>
                        </tr>

                        <!-- Body -->
                        <tr>
                          <td style="padding:36px 40px;">
                            <h2 style="margin:0 0 16px;color:#1a8a1a;font-size:22px;">
                              🟢 Credit Alert
                            </h2>
                            <p style="margin:0 0 24px;color:#555555;font-size:15px;line-height:1.6;">
                              Dear <strong>%s</strong>,<br><br>
                              Great news! Your CitiCore account has been credited.
                            </p>

                            <!-- Transaction Details -->
                            <table width="100%%" cellpadding="12" cellspacing="0"
                                   style="border:1px solid #e8e8e8;border-radius:8px;border-collapse:collapse;">
                              <tr style="background:#f5fff5;">
                                <td style="color:#666666;font-size:14px;border-bottom:1px solid #e8e8e8;">
                                  Amount Credited
                                </td>
                                <td style="font-weight:700;font-size:16px;color:#1a8a1a;
                                           border-bottom:1px solid #e8e8e8;">
                                  ₹ %s
                                </td>
                              </tr>
                              <tr>
                                <td style="color:#666666;font-size:14px;border-bottom:1px solid #e8e8e8;">
                                  Account
                                </td>
                                <td style="font-weight:600;font-size:15px;border-bottom:1px solid #e8e8e8;">
                                  %s
                                </td>
                              </tr>
                              <tr>
                                <td style="color:#666666;font-size:14px;">Reference</td>
                                <td style="font-size:14px;color:#888888;">%s</td>
                              </tr>
                            </table>

                            <p style="margin:24px 0 0;color:#555555;font-size:14px;">
                              Thank you for banking with CitiCore. 🙏
                            </p>
                          </td>
                        </tr>

                        <!-- Footer -->
                        <tr>
                          <td style="background:#f9f9f9;padding:20px 40px;border-top:1px solid #eeeeee;">
                            <p style="margin:0;font-size:12px;color:#999999;text-align:center;">
                              This is an automated message from CitiCore Banking Platform.<br>
                              &copy; 2026 CitiCore. All rights reserved.
                            </p>
                          </td>
                        </tr>

                      </table>
                    </td>
                  </tr>
                </table>

                </body>
                </html>
                """.formatted(
                email,
                amount,
                MaskingUtil.maskAccount(accountNumber),
                txnRef
        );
    }
}