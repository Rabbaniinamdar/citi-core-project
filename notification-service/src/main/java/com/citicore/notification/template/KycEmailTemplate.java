package com.citicore.notification.template;

public class KycEmailTemplate {

    /**
     * Generates a KYC status update email.
     * Automatically adjusts colour, icon, and body text based on status.
     *
     * @param email  recipient email (used in greeting)
     * @param status KYC status string — "APPROVED" or "REJECTED"
     */
    public static String kycTemplate(String email, String status) {
        boolean approved = "APPROVED".equalsIgnoreCase(status);

        String headerColor = approved ? "#1a8a1a"  : "#d93025";
        String icon        = approved ? "✅"        : "❌";
        String heading     = approved ? "KYC Approved"  : "KYC Rejected";
        String bodyText    = approved
                ? "Congratulations! Your KYC verification has been completed successfully. "
                + "You now have full access to all CitiCore banking features."
                : "Unfortunately, your KYC verification was not successful. "
                + "Please re-submit your documents through the CitiCore app "
                + "or visit your nearest branch for assistance.";
        String actionText  = approved
                ? "You may now open an account or start transacting on CitiCore."
                : "Please ensure your documents are valid, clearly scanned, and try again within 7 days.";

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
                <body style="margin:0;padding:0;font-family:Arial,sans-serif;background-color:#f5f5f5;">

                <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f5f5f5;padding:30px 0;">
                  <tr>
                    <td align="center">
                      <table width="600" cellpadding="0" cellspacing="0"
                             style="background:#ffffff;border-radius:10px;overflow:hidden;
                                    box-shadow:0 2px 8px rgba(0,0,0,0.08);">

                        <!-- Header -->
                        <tr>
                          <td style="background:%s;padding:28px 40px;">
                            <h1 style="margin:0;color:#ffffff;font-size:22px;font-weight:700;">
                              🏦 CitiCore Banking
                            </h1>
                          </td>
                        </tr>

                        <!-- Body -->
                        <tr>
                          <td style="padding:36px 40px;">
                            <h2 style="margin:0 0 16px;color:#1a1a1a;font-size:22px;">
                              %s %s
                            </h2>
                            <p style="margin:0 0 20px;color:#555555;font-size:15px;line-height:1.6;">
                              Dear <strong>%s</strong>,<br><br>
                              %s
                            </p>

                            <!-- Status Badge -->
                            <table width="100%%" cellpadding="0" cellspacing="0">
                              <tr>
                                <td style="background:#f9f9f9;border-left:5px solid %s;
                                           padding:14px 20px;border-radius:4px;">
                                  <span style="font-size:14px;color:#444444;">KYC Status: </span>
                                  <strong style="color:%s;font-size:15px;">%s</strong>
                                </td>
                              </tr>
                            </table>

                            <p style="margin:24px 0 0;color:#555555;font-size:14px;line-height:1.6;">
                              %s
                            </p>
                            <p style="margin:16px 0 0;color:#555555;font-size:14px;">
                              For any queries, contact us at
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
                headerColor,
                icon, heading,
                email,
                bodyText,
                headerColor, headerColor, status.toUpperCase(),
                actionText
        );
    }
}