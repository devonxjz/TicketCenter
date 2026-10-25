package vn.ticketscenter.identity.service;

import vn.ticketscenter.identity.dto.OtpSendRequestDto;
import vn.ticketscenter.identity.dto.OtpVerifyRequestDto;
import vn.ticketscenter.identity.dto.OtpVerifyResponseDto;

/** Orchestrates sending and verifying one-time codes; hashing, tokens and email text live in their own classes. */
public interface OtpService {
    /**
     * Sends a VERIFY_EMAIL or RESET_PASSWORD code: validate, check the 60 s cooldown, invalidate older codes,
     * store the new one and mail it after the transaction has committed.
     */
    boolean sendOtp(OtpSendRequestDto request);

    /**
     * Verifies a code. A wrong code counts towards the five-attempt limit; a right one is consumed atomically
     * and then either marks the email verified or yields a password-reset token.
     */
    OtpVerifyResponseDto verifyOtp(OtpVerifyRequestDto request);
}
