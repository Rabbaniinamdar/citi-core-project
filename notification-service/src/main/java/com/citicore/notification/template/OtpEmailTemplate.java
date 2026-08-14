package com.citicore.notification.template;

public class OtpEmailTemplate {

    /**
     * Generates an OTP verification email.
     *
     * @param email recipient email (used in greeting)
     * @param otp   the one-time password to display
     */
    public static String otpTemplate(String email, String otp) {
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
                          <td style="background:#1a73e8;padding:28px 40px;">
                            <h1 style="margin:0;color:#ffffff;font-size:22px;font-weight:700;">
                              🏦 CitiCore Banking
                            </h1>
                          </td>
                        </tr>

                        <!-- Body -->
                        <tr>
                          <td style="padding:36px 40px;">
                            <h2 style="margin:0 0 16px;color:#1a1a1a;font-size:20px;">
                              Your One-Time Password
                            </h2>
                            <p style="margin:0 0 24px;color:#555555;font-size:15px;line-height:1.6;">
                              Dear <strong>%s</strong>,<br><br>
                              Use the OTP below to complete your verification.
                              This OTP is valid for <strong>5 minutes</strong> and can only be used once.
                            </p>

                            <!-- OTP Box -->
                            <table width="100%%" cellpadding="0" cellspacing="0">
                              <tr>
                                <td align="center"
                                    style="background:#f0f4ff;border-radius:10px;
                                           padding:28px;margin:24px 0;">
                                  <p style="margin:0 0 6px;font-size:13px;color:#666666;
                                            text-transform:uppercase;letter-spacing:1px;">
                                    One-Time Password
                                  </p>
                                  <h1 style="margin:0;font-size:48px;letter-spacing:14px;
                                             color:#1a73e8;font-weight:800;">
                                    %s
                                  </h1>
                                </td>
                              </tr>
                            </table>

                            <p style="margin:24px 0 0;padding:16px;background:#fff3f3;
                                      border-left:4px solid #d93025;border-radius:4px;
                                      color:#d93025;font-size:13px;">
                              ⚠️ <strong>Never share this OTP with anyone.</strong>
                              CitiCore will never ask for your OTP over phone or email.
                            </p>
                            <p style="margin:20px 0 0;color:#555555;font-size:14px;">
                              If you did not request this OTP, please
                              <a href="mailto:support@citicore.com"
                                 style="color:#1a73e8;">contact support</a> immediately.
                            </p>
                          </td>
                        </tr>

                        <!-- Footer -->
                        <tr>
                          <td style="background:#f9f9f9;padding:20px 40px;
                                     border-top:1px solid #eeeeee;">
                            <p style="margin:0;font-size:12px;color:#999999;text-align:center;">
                              This is an automated message from CitiCore Banking Platform.<br>
                              Please do not reply to this email.
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
                """.formatted(email, otp);
    }
}