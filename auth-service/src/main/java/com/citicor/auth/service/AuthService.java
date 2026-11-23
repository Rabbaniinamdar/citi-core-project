package com.citicor.auth.service;

import com.citicor.auth.dto.*;
import com.citicor.auth.entity.*;
import com.citicor.auth.entity.Role;
import com.citicor.auth.entity.User;
import com.citicor.auth.exception.AuthException;
import com.citicor.auth.kafka.OtpEventPublisher;
import com.citicor.auth.repository.LoginAttemptRepository;
import com.citicor.auth.repository.LoginChallengeRepository;
import com.citicor.auth.repository.PasswordResetTokenRepository;
import com.citicor.auth.repository.RefreshTokenRepository;
import com.citicor.auth.repository.RoleRepository;
import com.citicor.auth.repository.UserRepository;
import com.citicor.auth.security.JwtUtils;
import com.citicore.events.otp.VerificationOtpEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String VERIFICATION_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final String PURPOSE_LOGIN_MFA = "LOGIN_MFA";
    private static final String PURPOSE_DISABLE_MFA = "DISABLE_MFA";
    private static final String MFA_METHOD_EMAIL_OTP = "EMAIL_OTP";
    private static final int MAX_MFA_RESENDS = 3;

    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final PasswordEncoder encoder;
    private final JwtUtils jwtUtils;
    private final RefreshTokenRepository refreshTokenRepo;
    private final OtpEventPublisher otpEventPublisher;
    private final LoginAttemptRepository loginAttemptRepo;
    private final PasswordResetTokenRepository passwordResetTokenRepo;
    private final LoginChallengeRepository loginChallengeRepo;
    private final EmailOtpService emailOtpService;
    private final SecureRandom secureRandom;

    public AuthService(UserRepository userRepo, RoleRepository roleRepo, PasswordEncoder encoder, JwtUtils jwtUtils, RefreshTokenRepository refreshTokenRepo, OtpEventPublisher otpEventPublisher, LoginAttemptRepository loginAttemptRepo, PasswordResetTokenRepository passwordResetTokenRepo, LoginChallengeRepository loginChallengeRepo, EmailOtpService emailOtpService) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.encoder = encoder;
        this.jwtUtils = jwtUtils;
        this.refreshTokenRepo = refreshTokenRepo;
        this.otpEventPublisher = otpEventPublisher;
        this.loginAttemptRepo = loginAttemptRepo;
        this.passwordResetTokenRepo = passwordResetTokenRepo;
        this.loginChallengeRepo = loginChallengeRepo;
        this.emailOtpService = emailOtpService;
        this.secureRandom = new SecureRandom();
    }

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenDuration;

//    public void register(RegisterRequest req) {
//        User user = new User();
//        if (userRepo.findByEmail(req.getEmail()).isPresent()) {
//            throw new AuthException("Email already registered", HttpStatus.CONFLICT);
//        }
//        user.setEmail(req.getEmail());
//        user.setPassword(encoder.encode(req.getPassword()));
//        user.setEmailVerified(false);
//        user.setStatus(UserStatus.PENDING);
//        user.setVerificationCode(generateVerificationCode());
//        user.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(15));
//        Role role = roleRepo.findByName("ROLE_CUSTOMER")
//                .orElseThrow(()->
//                        new AuthException("role not found", HttpStatus.NOT_FOUND)
//                );
//
//        user.getRoles().add(role);
//        userRepo.save(user);
//        sendVerificationEmail(user);
//    }
    public void register(RegisterRequest req) {
        log.info("Register start for email: {}", req.getEmail());

        if (userRepo.findByEmail(req.getEmail()).isPresent()) {
            log.warn("Registration attempt with existing email: {}", req.getEmail());
            throw new AuthException(
                    "Email already registered",
                    HttpStatus.CONFLICT
            );
        }

        log.debug("Encoding password for new user: {}", req.getEmail());
        User user = new User();

        user.setEmail(req.getEmail());
        user.setPassword(encoder.encode(req.getPassword()));
        user.setEmailVerified(false);
        user.setStatus(UserStatus.PENDING);

        user.setVerificationCode(generateVerificationCode());
        user.setVerificationCodeExpiresAt(
                LocalDateTime.now().plusMinutes(15)
        );

        Role role = roleRepo.findByName("ROLE_CUSTOMER")
                .orElseThrow(() ->
                        new AuthException(
                                "role not found",
                                HttpStatus.NOT_FOUND
                        )
                );

        user.getRoles().add(role);

        userRepo.save(user);

        log.info("User saved with ID: {}", user.getId());

        sendVerificationEmail(user);

        log.info("OTP event published for user ID: {}", user.getId());
    }
    public LoginResponse login(LoginRequest req) {
        String email = req.getEmail().toLowerCase().trim();

        // Check login attempt lockout BEFORE checking credentials (prevents enumeration)
        LoginAttempt attempt = loginAttemptRepo.findByEmail(email)
                .orElseGet(() -> new LoginAttempt(email));

        if (attempt.isLocked()) {
            log.warn("Login attempt for locked account: {}", email);
            throw new AuthException(
                    "Account temporarily locked. Try again in " + LoginAttempt.LOCKOUT_MINUTES + " minutes.",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }

        User user = userRepo.findByEmail(email)
                .orElseThrow(() ->
                        new AuthException("Invalid credentials", HttpStatus.UNAUTHORIZED)
                );

        if (!user.isEmailVerified()) {
            throw new AuthException("Email not verified", HttpStatus.FORBIDDEN);
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthException("User not active", HttpStatus.FORBIDDEN);
        }

        if (!encoder.matches(req.getPassword(), user.getPassword())) {
            // Record failed attempt
            attempt.incrementFailedCount();
            loginAttemptRepo.save(attempt);

            // Generic error message - no account enumeration
            throw new AuthException("Invalid credentials", HttpStatus.UNAUTHORIZED);
        }

        // Successful password verification - reset failed attempts
        loginAttemptRepo.resetFailedCount(req.getEmail(), LocalDateTime.now());
        loginAttemptRepo.deleteExpiredLockouts(LocalDateTime.now());

        // Check if MFA is enabled for this user
        if (user.isMfaEnabled() && "EMAIL_OTP".equals(user.getMfaMethod())) {
            // Create login challenge and send OTP
            return createLoginMfaChallenge(user);
        }

        // No MFA required - issue tokens directly
        String accessToken = jwtUtils.generateToken(user);
        RefreshToken refreshToken = createRefreshToken(user);

        return new LoginResponse(accessToken, refreshToken.getToken());
    }

    private LoginResponse createLoginMfaChallenge(User user) {
        // Delete any existing unconsumed login challenges for this user
        loginChallengeRepo.deleteUnconsumedByUserAndPurpose(user.getId(), "LOGIN_MFA");

        // Generate OTP and hash it
        String otp = emailOtpService.generateOtp();
        String otpHash = emailOtpService.hashOtp(otp);
        LocalDateTime expiresAt = emailOtpService.calculateExpiry();

        // Create challenge ID
        String challengeId = UUID.randomUUID().toString();

        // Save login challenge
        LoginChallenge challenge = new LoginChallenge(
                user.getId(),
                challengeId,
                otpHash,
                expiresAt,
                emailOtpService.getMaxAttempts()
        );
        loginChallengeRepo.save(challenge);

        // Send OTP via email
        otpEventPublisher.publishLoginMfaOtp(
                user.getId(),
                user.getEmail(),
                otp,
                emailOtpService.getExpiryMinutes()
        );

        log.info("Login MFA challenge created for user: {}, challengeId: {}", user.getId(), challengeId);

        return new LoginResponse(
                true,
                challengeId,
                List.of("EMAIL_OTP"),
                "MFA required. An OTP has been sent to your email."
        );
    }
    private RefreshToken createRefreshToken(User user) {

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setToken(UUID.randomUUID().toString());
        token.setExpiryDate(Instant.now().plusMillis(refreshTokenDuration));
        token.setRevoked(false);

        return refreshTokenRepo.save(token);
    }

    public AuthResponse refreshToken(String requestToken) {

        RefreshToken token = refreshTokenRepo.findByToken(requestToken)
                .orElseThrow(() -> new AuthException("Invalid refresh token", HttpStatus.UNAUTHORIZED));
        if (token.isRevoked()) {
            throw new AuthException("Token revoked", HttpStatus.UNAUTHORIZED);
        }
        if (token.getExpiryDate().isBefore(Instant.now())) {
            throw new AuthException("Token expired", HttpStatus.UNAUTHORIZED);
        }
        User user = token.getUser();
        String newAccessToken = jwtUtils.generateToken(user);
        token.setRevoked(true);
        refreshTokenRepo.save(token);
        RefreshToken newRefreshToken = createRefreshToken(user);

        return new AuthResponse(newAccessToken, newRefreshToken.getToken());
    }
    public void logout(String refreshToken) {

        RefreshToken token = refreshTokenRepo.findByToken(refreshToken)
                .orElseThrow(() -> new AuthException("Invalid token", HttpStatus.NOT_FOUND));

        token.setRevoked(true);
        refreshTokenRepo.save(token);
    }
    private void sendVerificationEmail(User user) {
        otpEventPublisher.publishVerificationOtp(

                VerificationOtpEvent.builder()
                        .userId(user.getId())
                        .email(user.getEmail())
                        .verificationCode(
                                user.getVerificationCode()
                        )
                        .expiryMinutes(15)
                        .build()
        );
    }
    public void resendVerificationCode(String email) {
        Optional<User> optionalUser = userRepo.findByEmail(email);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            if (user.isEmailVerified()) {
                throw new AuthException("Account already verified", HttpStatus.BAD_REQUEST);
            }

            if (user.getVerificationCodeExpiresAt() != null &&
                    user.getVerificationCodeExpiresAt().isAfter(LocalDateTime.now().minusMinutes(1))) {
                throw new AuthException("Please wait before requesting a new code", HttpStatus.TOO_MANY_REQUESTS);
            }
            user.setVerificationCode(generateVerificationCode());
            user.setVerificationCodeExpiresAt(LocalDateTime.now().plusHours(1));
            sendVerificationEmail(user);
            userRepo.save(user);
        } else {
            throw new AuthException("User not found", HttpStatus.NOT_FOUND);
        }
    }
    public void verifyUser(VerifyUser input) {
        Optional<User> optionalUser = userRepo.findByEmail(input.getEmail());
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            if (user.getVerificationCodeExpiresAt() == null ||
                    user.getVerificationCodeExpiresAt().isBefore(LocalDateTime.now())) {
                throw new AuthException("Verification code has expired", HttpStatus.BAD_REQUEST);
            }
            if (user.getVerificationCode().equals(input.getVerificationCode())) {
                user.setVerificationCode(null);
                user.setVerificationCodeExpiresAt(null);
                user.setEmailVerified(true);
                user.setStatus(UserStatus.ACTIVE);
                userRepo.save(user);
            } else {
                throw new AuthException("Invalid verification code", HttpStatus.BAD_REQUEST);            }
        } else {
            throw new AuthException("User not found", HttpStatus.NOT_FOUND);        }
    }
    private String generateVerificationCode() {
        // 8-char alphanumeric = 36^8 ≈ 2.8 trillion combinations (vs 900K for 6-digit)
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(VERIFICATION_CHARS.charAt(secureRandom.nextInt(VERIFICATION_CHARS.length())));
        }
        return sb.toString();
    }

    // ========== Password Reset Flow ==========

    public void forgotPassword(ForgotPasswordRequest req) {
        String email = req.getEmail().toLowerCase().trim();

        // Always return success to prevent account enumeration
        User user = userRepo.findByEmail(email).orElse(null);
        if (user == null) {
            log.info("Forgot password request for non-existent email: {}", email);
            return;
        }

        // Delete any existing tokens for this user
        passwordResetTokenRepo.deleteByUserId(user.getId());

        // Create new reset token
        PasswordResetToken resetToken = new PasswordResetToken(user.getId());
        passwordResetTokenRepo.save(resetToken);

        // Send reset email via notification service
        sendPasswordResetEmail(user, resetToken.getToken());

        log.info("Password reset token generated for user: {}", user.getId());
    }

    public void resetPassword(ResetPasswordRequest req) {
        // Validate password match
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new AuthException("Passwords do not match", HttpStatus.BAD_REQUEST);
        }

        // Validate token
        PasswordResetToken resetToken = passwordResetTokenRepo.findByToken(req.getToken())
                .orElseThrow(() -> new AuthException("Invalid or expired reset token", HttpStatus.BAD_REQUEST));

        if (resetToken.isUsed()) {
            throw new AuthException("Reset token has already been used", HttpStatus.BAD_REQUEST);
        }

        if (resetToken.isExpired()) {
            throw new AuthException("Reset token has expired", HttpStatus.BAD_REQUEST);
        }

        User user = userRepo.findById(resetToken.getUserId())
                .orElseThrow(() -> new AuthException("User not found", HttpStatus.NOT_FOUND));

        // Update password
        user.setPassword(encoder.encode(req.getNewPassword()));
        userRepo.save(user);

        // Mark token as used
        resetToken.markAsUsed();
        passwordResetTokenRepo.save(resetToken);

        // Revoke all refresh tokens for this user
        revokeAllRefreshTokens(user.getId());

        log.info("Password reset successful for user: {}", user.getId());
    }

    public void changePassword(ChangePasswordRequest req, Long userId) {
        // Validate password match
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new AuthException("Passwords do not match", HttpStatus.BAD_REQUEST);
        }

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new AuthException("User not found", HttpStatus.NOT_FOUND));

        // Verify current password
        if (!encoder.matches(req.getCurrentPassword(), user.getPassword())) {
            throw new AuthException("Current password is incorrect", HttpStatus.UNAUTHORIZED);
        }

        // Update password
        user.setPassword(encoder.encode(req.getNewPassword()));
        userRepo.save(user);

        // Revoke all refresh tokens
        revokeAllRefreshTokens(userId);

        log.info("Password changed for user: {}", userId);
    }

    private void revokeAllRefreshTokens(Long userId) {
        refreshTokenRepo.findAll().stream()
                .filter(token -> token.getUser().getId().equals(userId))
                .forEach(token -> {
                    token.setRevoked(true);
                    refreshTokenRepo.save(token);
                });
    }

    private void sendPasswordResetEmail(User user, String token) {
        // Send email via notification service
        // For now, log the token (in production, send via notification service)
        log.info("Password reset token for {}: {}", user.getEmail(), token);
    }

// ========== MFA (Email OTP) Methods ==========

/**
 * Enable email OTP MFA for a user.
 * The user must already have a verified email address.
 */
@Transactional
public void enableEmailOtpMfa(Long userId) {
    User user = userRepo.findById(userId)
            .orElseThrow(() ->
                    new AuthException("User not found", HttpStatus.NOT_FOUND));

    if (!user.isEmailVerified()) {
        throw new AuthException(
                "Verify your email before enabling MFA",
                HttpStatus.FORBIDDEN
        );
    }

    if (user.isMfaEnabled()) {
        throw new AuthException(
                "MFA is already enabled",
                HttpStatus.BAD_REQUEST
        );
    }

    user.setMfaEnabled(true);
    user.setMfaMethod(MFA_METHOD_EMAIL_OTP);
    user.setMfaEnabledAt(LocalDateTime.now());

    userRepo.save(user);

    log.info("Email OTP MFA enabled for user: {}", userId);
}

/**
 * Verifies a login MFA challenge and issues tokens only after
 * successful OTP verification.
 */
@Transactional(noRollbackFor = AuthException.class)
public LoginResponse verifyMfaChallenge(
        VerifyMfaChallengeRequest request
) {
    LoginChallenge challenge = verifyAndConsumeChallenge(
            request.getChallengeId(),
            null,
            PURPOSE_LOGIN_MFA,
            request.getOtpCode()
    );

    User user = userRepo.findById(challenge.getUserId())
            .orElseThrow(() ->
                    new AuthException("User not found", HttpStatus.NOT_FOUND));

    if (!user.isMfaEnabled()
            || !MFA_METHOD_EMAIL_OTP.equals(user.getMfaMethod())) {
        throw new AuthException(
                "Email OTP MFA is no longer enabled",
                HttpStatus.FORBIDDEN
        );
    }

    String accessToken = jwtUtils.generateToken(user);
    RefreshToken refreshToken = createRefreshToken(user);

    log.info("Login MFA verified for user: {}", user.getId());

    return new LoginResponse(accessToken, refreshToken.getToken());
}

/**
 * Starts the MFA disable flow.
 *
 * The caller should return the challenge ID to the authenticated client.
 * The client must then submit that ID and the emailed OTP to disableMfa.
 */
@Transactional
public String initiateMfaDisable(Long userId) {
    User user = userRepo.findById(userId)
            .orElseThrow(() ->
                    new AuthException("User not found", HttpStatus.NOT_FOUND));

    if (!user.isMfaEnabled()) {
        throw new AuthException(
                "MFA is not enabled",
                HttpStatus.BAD_REQUEST
        );
    }

    if (!user.isEmailVerified()) {
        throw new AuthException(
                "A verified email is required for MFA recovery",
                HttpStatus.FORBIDDEN
        );
    }

    LoginChallenge challenge =
            createMfaChallenge(user, PURPOSE_DISABLE_MFA);

    return challenge.getChallengeId();
}

/**
 * Verifies a DISABLE_MFA challenge and disables MFA.
 *
 * This method does not issue login tokens.
 */
@Transactional(noRollbackFor = AuthException.class)
public void disableMfa(
        Long userId,
        String challengeId,
        String otpCode
) {
    User user = userRepo.findById(userId)
            .orElseThrow(() ->
                    new AuthException("User not found", HttpStatus.NOT_FOUND));

    if (!user.isMfaEnabled()) {
        throw new AuthException(
                "MFA is not enabled",
                HttpStatus.BAD_REQUEST
        );
    }

    verifyAndConsumeChallenge(
            challengeId,
            userId,
            PURPOSE_DISABLE_MFA,
            otpCode
    );

    user.setMfaEnabled(false);
    user.setMfaMethod(null);
    user.setMfaEnabledAt(null);

    userRepo.save(user);

    loginChallengeRepo.deleteUnconsumedByUserAndPurpose(
            userId,
            PURPOSE_LOGIN_MFA
    );
    loginChallengeRepo.deleteUnconsumedByUserAndPurpose(
            userId,
            PURPOSE_DISABLE_MFA
    );

    log.info("MFA disabled for user: {}", userId);
}

/**
 * Resends an OTP for an existing challenge.
 * Verification-attempt count is not reset by a resend.
 */
@Transactional(noRollbackFor = AuthException.class)
public void resendMfaOtp(String challengeId) {
    LoginChallenge challenge = loginChallengeRepo
            .findByChallengeIdForUpdate(challengeId)
            .orElseThrow(() ->
                    new AuthException(
                            "Invalid or expired challenge",
                            HttpStatus.BAD_REQUEST
                    ));

    if (challenge.isConsumed()) {
        throw new AuthException(
                "Challenge already used",
                HttpStatus.BAD_REQUEST
        );
    }

    if (challenge.isExpired()) {
        loginChallengeRepo.delete(challenge);
        throw new AuthException(
                "Challenge expired",
                HttpStatus.BAD_REQUEST
        );
    }

    if (challenge.isExhausted()) {
        throw new AuthException(
                "Maximum verification attempts exceeded",
                HttpStatus.TOO_MANY_REQUESTS
        );
    }

    if (!PURPOSE_LOGIN_MFA.equals(challenge.getPurpose())
            && !PURPOSE_DISABLE_MFA.equals(challenge.getPurpose())) {
        throw new AuthException(
                "Invalid challenge",
                HttpStatus.BAD_REQUEST
        );
    }

    if (challenge.getResendCount() >= MAX_MFA_RESENDS) {
        throw new AuthException(
                "Maximum OTP resend requests exceeded",
                HttpStatus.TOO_MANY_REQUESTS
        );
    }

    User user = userRepo.findById(challenge.getUserId())
            .orElseThrow(() ->
                    new AuthException("User not found", HttpStatus.NOT_FOUND));

    String otp = emailOtpService.generateOtp();

    challenge.setOtpHash(emailOtpService.hashOtp(otp));
    challenge.setExpiresAt(emailOtpService.calculateExpiry());
    challenge.incrementResendCount();

    // Intentionally keep the existing verification-attempt count.
    loginChallengeRepo.save(challenge);

    otpEventPublisher.publishLoginMfaOtp(
            user.getId(),
            user.getEmail(),
            otp,
            emailOtpService.getExpiryMinutes()
    );

    log.info("MFA OTP resent for user: {}", user.getId());
}

/**
 * Creates and emails an OTP challenge for the given purpose.
 */
private LoginChallenge createMfaChallenge(User user, String purpose) {
    loginChallengeRepo.deleteUnconsumedByUserAndPurpose(
            user.getId(),
            purpose
    );

    String otp = emailOtpService.generateOtp();

    LoginChallenge challenge = new LoginChallenge();
    challenge.setChallengeId(UUID.randomUUID().toString());
    challenge.setUserId(user.getId());
    challenge.setPurpose(purpose);
    challenge.setOtpHash(emailOtpService.hashOtp(otp));
    challenge.setExpiresAt(emailOtpService.calculateExpiry());
    challenge.setAttempts(0);
    challenge.setResendCount(0);
    challenge.setConsumed(false);

    loginChallengeRepo.save(challenge);

    otpEventPublisher.publishLoginMfaOtp(
            user.getId(),
            user.getEmail(),
            otp,
            emailOtpService.getExpiryMinutes()
    );

    return challenge;
}

/**
 * Validates and consumes a challenge.
 *
 * The caller must have an active transaction. The repository lookup must
 * lock the row so concurrent requests cannot consume the same challenge.
 */
private LoginChallenge verifyAndConsumeChallenge(
        String challengeId,
        Long expectedUserId,
        String expectedPurpose,
        String otpCode
) {
    LoginChallenge challenge = loginChallengeRepo
            .findByChallengeIdForUpdate(challengeId)
            .orElseThrow(() ->
                    new AuthException(
                            "Invalid or expired challenge",
                            HttpStatus.BAD_REQUEST
                    ));

    if (challenge.isConsumed()) {
        throw new AuthException(
                "Challenge already used",
                HttpStatus.BAD_REQUEST
        );
    }

    if (!expectedPurpose.equals(challenge.getPurpose())) {
        throw new AuthException(
                "Invalid challenge",
                HttpStatus.BAD_REQUEST
        );
    }

    if (expectedUserId != null
            && !expectedUserId.equals(challenge.getUserId())) {
        throw new AuthException(
                "Invalid challenge",
                HttpStatus.BAD_REQUEST
        );
    }

    if (challenge.isExpired()) {
        loginChallengeRepo.delete(challenge);
        throw new AuthException(
                "Challenge expired",
                HttpStatus.BAD_REQUEST
        );
    }

    if (challenge.isExhausted()) {
        throw new AuthException(
                "Maximum verification attempts exceeded",
                HttpStatus.TOO_MANY_REQUESTS
        );
    }

    if (!emailOtpService.verifyOtp(otpCode, challenge.getOtpHash())) {
        challenge.incrementAttempts();
        loginChallengeRepo.save(challenge);

        throw new AuthException(
                "Invalid OTP code",
                HttpStatus.UNAUTHORIZED
        );
    }

    challenge.markConsumed();
    loginChallengeRepo.save(challenge);

    return challenge;
}

    public Long extractUserIdFromToken(String token) {
        // Extract userId from JWT token
        try {
            return jwtUtils.extractUserId(token);
        } catch (Exception e) {
            throw new AuthException("Invalid token", HttpStatus.UNAUTHORIZED);
        }
    }

}
