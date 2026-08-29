package com.citicor.auth.service;

import com.citicor.auth.dto.*;
import com.citicor.auth.entity.*;
import com.citicor.auth.entity.Role;
import com.citicor.auth.entity.User;
import com.citicor.auth.exception.AuthException;
import com.citicor.auth.kafka.OtpEventPublisher;
import com.citicor.auth.repository.RefreshTokenRepository;
import com.citicor.auth.repository.RoleRepository;
import com.citicor.auth.repository.UserRepository;
import com.citicor.auth.security.JwtUtils;
import com.citicore.events.otp.VerificationOtpEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final PasswordEncoder encoder;
    private final JwtUtils jwtUtils;
    private final RefreshTokenRepository refreshTokenRepo;
    private final OtpEventPublisher otpEventPublisher;

    public AuthService(UserRepository userRepo, RoleRepository roleRepo, PasswordEncoder encoder, JwtUtils jwtUtils, RefreshTokenRepository refreshTokenRepo , OtpEventPublisher otpEventPublisher) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.encoder = encoder;
        this.jwtUtils = jwtUtils;
        this.refreshTokenRepo = refreshTokenRepo;
        this.otpEventPublisher = otpEventPublisher;
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

        System.out.println("========== REGISTER START ==========");

        System.out.println("Checking email...");
        if (userRepo.findByEmail(req.getEmail()).isPresent()) {
            System.out.println("Email already exists");
            throw new AuthException(
                    "Email already registered",
                    HttpStatus.CONFLICT
            );
        }

        System.out.println("Encoding password...");
        User user = new User();

        user.setEmail(req.getEmail());
        user.setPassword(encoder.encode(req.getPassword()));
        user.setEmailVerified(false);
        user.setStatus(UserStatus.PENDING);

        System.out.println("Generating verification code...");
        user.setVerificationCode(generateVerificationCode());
        user.setVerificationCodeExpiresAt(
                LocalDateTime.now().plusMinutes(15)
        );

        System.out.println("Finding ROLE_CUSTOMER...");

        Role role = roleRepo.findByName("ROLE_CUSTOMER")
                .orElseThrow(() ->
                        new AuthException(
                                "role not found",
                                HttpStatus.NOT_FOUND
                        )
                );

        user.getRoles().add(role);

        System.out.println("Saving user...");

        userRepo.save(user);

        System.out.println(
                "USER SAVED. ID = " + user.getId()
        );

        System.out.println("Publishing OTP event...");

        sendVerificationEmail(user);

        System.out.println("OTP EVENT PUBLISHED");

        System.out.println("========== REGISTER END ==========");
    }
    public AuthResponse login(LoginRequest req) {
        User user = userRepo.findByEmail(req.getEmail())
                .orElseThrow(() ->
                        new AuthException("User not found", HttpStatus.NOT_FOUND)
                );
        if (!user.isEmailVerified()) {
            throw new AuthException("Email not verified", HttpStatus.FORBIDDEN);
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthException("User not active", HttpStatus.FORBIDDEN);
        }
        if (!encoder.matches(req.getPassword(), user.getPassword())) {
            throw new AuthException("Invalid credentials", HttpStatus.UNAUTHORIZED);
        }
        String accessToken = jwtUtils.generateToken(user);

        RefreshToken refreshToken = createRefreshToken(user);

        return new AuthResponse(accessToken, refreshToken.getToken());

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
        SecureRandom random = new SecureRandom();
        int code = random.nextInt(900000) + 100000;
        return String.valueOf(code);
    }

}
