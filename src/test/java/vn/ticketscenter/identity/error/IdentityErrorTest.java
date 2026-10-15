package vn.ticketscenter.identity.error;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The code/status pairs are part of the public API; changing one is a breaking change. */
class IdentityErrorTest {

    @Test
    void registrationConflictsAllReportIdentityConflict() {
        for (IdentityError error : new IdentityError[]{
                IdentityError.EMAIL_ALREADY_EXISTS, IdentityError.USERNAME_ALREADY_EXISTS,
                IdentityError.IDENTITY_CONFLICT}) {
            assertEquals(409, error.status());
            assertEquals("IDENTITY_CONFLICT", error.code());
        }
    }

    @Test
    void otpFailuresKeepTheirCodesAndStatuses() {
        assertEquals(429, IdentityError.COOLDOWN_ACTIVE.status());
        assertEquals("COOLDOWN_ACTIVE", IdentityError.COOLDOWN_ACTIVE.code());
        assertEquals(500, IdentityError.EMAIL_SEND_FAILED.status());
        assertEquals("OTP_ERROR", IdentityError.OTP_ERROR.code());
        for (IdentityError error : new IdentityError[]{
                IdentityError.OTP_INVALID, IdentityError.OTP_NOT_FOUND, IdentityError.OTP_ALREADY_USED,
                IdentityError.OTP_EXPIRED, IdentityError.OTP_MAX_ATTEMPTS_EXCEEDED}) {
            assertEquals(400, error.status());
            assertEquals(error.name(), error.code());
        }
    }

    @Test
    void loginAndTokenFailures() {
        assertEquals(401, IdentityError.INVALID_CREDENTIALS.status());
        assertEquals(401, IdentityError.ACCOUNT_DISABLED.status());
        assertEquals(400, IdentityError.INVALID_TOKEN.status());
        assertEquals(400, IdentityError.INVALID_JSON.status());
        assertEquals(400, IdentityError.VALIDATION_FAILED.status());
    }
}
