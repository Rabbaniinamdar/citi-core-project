package com.citicore.notification.template;

import com.citicore.notification.util.MaskingUtil;

import java.math.BigDecimal;

public class TransactionEmailTemplate {

    /**
     * Transfer success email — sent after full saga completes (credit-success-topic).
     */
    public static String successTemplate(
            String customerName,
            BigDecimal amount,
            String reference,
            String toAccount
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
                              ✅ Transfer Successful
                            </h2>
                            <p style="margin:0 0 24px;color:#555555;font-size:15px;line-height:1.6;">
                              Dear <strong>%s</strong>,<br><br>
                              Your fund transfer has been processed successfully.
                            </p>

                            <!-- Transaction Details -->
                            <table width="100%%" cellpadding="12" cellspacing="0"
                                   style="border:1px solid #e8e8e8;border-radius:8px;border-collapse:collapse;">
                              <tr style="background:#f5fff5;">
                                <td style="color:#666666;font-size:14px;border-bottom:1px solid #e8e8e8;
                                           width:40%%;">Amount Transferred</td>
                                <td style="font-weight:700;font-size:16px;color:#1a8a1a;
                                           border-bottom:1px solid #e8e8e8;">₹ %s</td>
                              </tr>
                              <tr>
                                <td style="color:#666666;font-size:14px;border-bottom:1px solid #e8e8e8;">
                                  To Account
                                </td>
                                <td style="font-weight:600;font-size:15px;border-bottom:1px solid #e8e8e8;">
                                  %s
                                </td>
                              </tr>
                              <tr>
                                <td style="color:#666666;font-size:14px;">Reference Number</td>
                                <td style="font-size:14px;color:#888888;">%s</td>
                              </tr>
                            </table>

                            <p style="margin:24px 0 0;padding:12px 16px;background:#f5fff5;
                                      border-left:4px solid #1a8a1a;border-radius:4px;
                                      font-size:13px;color:#1a8a1a;">
                              💡 Please save your reference number for future queries.
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
                customerName,
                amount,
                MaskingUtil.maskAccount(toAccount),
                reference
        );
    }

    /**
     * Transfer failed email — sent when saga fails (credit-failed-topic or debit-failed-topic).
     */
    public static String failedTemplate(
            String customerName,
            BigDecimal amount,
            String reference,
            String reason
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
                              ❌ Transfer Failed
                            </h2>
                            <p style="margin:0 0 24px;color:#555555;font-size:15px;line-height:1.6;">
                              Dear <strong>%s</strong>,<br><br>
                              We were unable to process your fund transfer.
                              <strong>No amount has been deducted</strong> from your account.
                            </p>

                            <!-- Transaction Details -->
                            <table width="100%%" cellpadding="12" cellspacing="0"
                                   style="border:1px solid #e8e8e8;border-radius:8px;border-collapse:collapse;">
                              <tr style="background:#fff5f5;">
                                <td style="color:#666666;font-size:14px;border-bottom:1px solid #e8e8e8;
                                           width:40%%;">Amount</td>
                                <td style="font-weight:700;font-size:16px;border-bottom:1px solid #e8e8e8;">
                                  ₹ %s
                                </td>
                              </tr>
                              <tr>
                                <td style="color:#666666;font-size:14px;border-bottom:1px solid #e8e8e8;">
                                  Reference
                                </td>
                                <td style="font-size:14px;color:#888888;border-bottom:1px solid #e8e8e8;">
                                  %s
                                </td>
                              </tr>
                              <tr>
                                <td style="color:#666666;font-size:14px;">Reason</td>
                                <td style="font-size:14px;color:#d93025;font-weight:600;">%s</td>
                              </tr>
                            </table>

                            <p style="margin:24px 0 0;color:#555555;font-size:14px;">
                              Please try again or contact
                              <a href="mailto:support@citicore.com"
                                 style="color:#1a73e8;">support@citicore.com</a>
                              if the issue persists.
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
                """.formatted(customerName, amount, reference, reason);
    }

    /**
     * Reversal email — sent when credit failed and money was refunded (reversal-success-topic).
     */
    public static String reversalTemplate(
            String customerName,
            BigDecimal amount,
            String reference
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
                          <td style="background:#f9a825;padding:28px 40px;">
                            <h1 style="margin:0;color:#ffffff;font-size:22px;font-weight:700;">
                              🏦 CitiCore Banking
                            </h1>
                          </td>
                        </tr>

                        <!-- Body -->
                        <tr>
                          <td style="padding:36px 40px;">
                            <h2 style="margin:0 0 16px;color:#f9a825;font-size:22px;">
                              🔄 Transfer Reversed &amp; Refunded
                            </h2>
                            <p style="margin:0 0 24px;color:#555555;font-size:15px;line-height:1.6;">
                              Dear <strong>%s</strong>,<br><br>
                              Your transfer could not be completed and has been reversed.
                              The full amount has been <strong>refunded to your account</strong>.
                            </p>

                            <!-- Transaction Details -->
                            <table width="100%%" cellpadding="12" cellspacing="0"
                                   style="border:1px solid #e8e8e8;border-radius:8px;border-collapse:collapse;">
                              <tr style="background:#fffdf0;">
                                <td style="color:#666666;font-size:14px;border-bottom:1px solid #e8e8e8;
                                           width:40%%;">Amount Refunded</td>
                                <td style="font-weight:700;font-size:16px;color:#1a8a1a;
                                           border-bottom:1px solid #e8e8e8;">₹ %s</td>
                              </tr>
                              <tr>
                                <td style="color:#666666;font-size:14px;">Reference</td>
                                <td style="font-size:14px;color:#888888;">%s</td>
                              </tr>
                            </table>

                            <p style="margin:24px 0 0;padding:12px 16px;background:#fffdf0;
                                      border-left:4px solid #f9a825;border-radius:4px;
                                      font-size:13px;color:#7a5000;">
                              ⏳ The refund may take a few minutes to reflect in your available balance.
                            </p>
                            <p style="margin:20px 0 0;color:#555555;font-size:14px;">
                              For assistance, contact
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
                """.formatted(customerName, amount, reference);
    }
}