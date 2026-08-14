package com.citicore.user.controller;

import com.citicore.user.dto.*;
import com.citicore.user.entity.AuthUser;
import com.citicore.user.entity.KycStatus;
import com.citicore.user.entity.UserProfile;
import com.citicore.user.service.UserService;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user")
@RefreshScope
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * POST /api/v1/user/profile
     * Creates a user profile for the authenticated user.
     * Called once during onboarding after JWT registration.
     *
     * Body:
     * {
     *   "firstName": "Mahammad",
     *   "lastName": "Rabbani",
     *   "email": "rabbanitechm@gmail.com",
     *   "phoneNumber": "9876543210",
     *   "dateOfBirth": "1999-06-15",
     *   "address": "123 Main Street",
     *   "city": "Hyderabad",
     *   "state": "Telangana",
     *   "pincode": "500001"
     * }
     */
    @PostMapping("/profile")
    public ResponseEntity<ApiResponse<ProfileResponse>> createProfile(
            @RequestBody CreateProfileRequest request) {

        AuthUser authUser = getAuthUser();
        UserProfile profile = userService.createProfile(request, authUser.getId());
        return ResponseEntity.ok(ApiResponse.success(
                "Profile created successfully",
                ProfileResponse.from(profile)
        ));
    }

    /**
     * GET /api/v1/user/profile
     * Returns the authenticated user's profile.
     */
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<ProfileResponse>> getMyProfile() {
        AuthUser authUser = getAuthUser();
        UserProfile profile = userService.getMyProfile(authUser.getId());
        return ResponseEntity.ok(ApiResponse.success(
                "Profile fetched",
                ProfileResponse.from(profile)
        ));
    }

    /**
     * PUT /api/v1/user/profile
     * Updates mutable profile fields for the authenticated user.
     * Email and authUserId cannot be changed.
     *
     * Body: any subset of mutable fields (null fields are ignored)
     */
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<ProfileResponse>> updateProfile(
            @RequestBody UpdateProfileRequest request) {

        AuthUser authUser = getAuthUser();
        UserProfile updated = userService.updateProfile(request, authUser.getId());
        return ResponseEntity.ok(ApiResponse.success(
                "Profile updated successfully",
                ProfileResponse.from(updated)
        ));
    }

    /**
     * GET /api/v1/user/kyc-status/{userId}
     * Returns true if the user's KYC is APPROVED.
     *
     * Called internally by account-service via Feign (UserClient)
     * before allowing account creation:
     *   @FeignClient("user-service")
     *   boolean getKycStatus(@PathVariable Long userId);
     *
     * NOT a user-facing endpoint — used for service-to-service communication.
     */
    @GetMapping("/kyc-status/{userId}")
    public ResponseEntity<Boolean> getKycStatus(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getKycStatus(userId));
    }

    /**
     * GET /api/v1/user/kyc-status
     * Returns the full KYC status enum for the authenticated user.
     * Used by the frontend to show the correct KYC state in the UI.
     */
    @GetMapping("/kyc-status")
    public ResponseEntity<ApiResponse<KycStatus>> getMyKycStatus() {
        AuthUser authUser = getAuthUser();
        KycStatus status = userService.getKycStatusDetail(authUser.getId());
        return ResponseEntity.ok(ApiResponse.success("KYC status fetched", status));
    }

    // ─────────────────────────────────────────────────────────────────────────────

    private AuthUser getAuthUser() {
        return (AuthUser) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
}