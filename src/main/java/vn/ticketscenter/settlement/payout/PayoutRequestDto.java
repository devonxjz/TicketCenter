package vn.ticketscenter.settlement.payout;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;
import vn.ticketscenter.settlement.service.FinanceException;
import vn.ticketscenter.settlement.dto.FinanceJson;

public record PayoutRequestDto(
        BigDecimal amount,
        String bankAccountNumber,
        String bankName,
        String accountHolderName,
        String note) {
    public PayoutRequestDto {
        try {
            amount = amount.setScale(0, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException | NullPointerException failure) {
            throw invalid();
        }
        if (amount.signum() <= 0 || amount.precision() > 19) {
            throw invalid();
        }
        bankAccountNumber = term(bankAccountNumber, 64);
        bankName = term(bankName, 100);
        accountHolderName = term(accountHolderName, 200);
        note = note == null ? "" : note.strip();
        if (note.length() > 500) {
            throw invalid();
        }
        try {
            FinanceJson.write(List.of(bankAccountNumber, bankName, accountHolderName, note));
        } catch (IllegalArgumentException failure) {
            throw invalid();
        }
    }

    public byte[] fingerprint(UUID settlementId) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(
                            FinanceJson.write(
                                            List.of(
                                                    settlementId,
                                                    amount,
                                                    bankAccountNumber,
                                                    bankName,
                                                    accountHolderName,
                                                    note))
                                    .getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String term(String raw, int max) {
        if (raw == null || raw.isBlank() || raw.length() > max || raw.chars().anyMatch(c -> c < 32)) {
            throw invalid();
        }
        return raw.strip();
    }

    private static FinanceException invalid() {
        return new FinanceException("PAYOUT_INVALID", 400);
    }
}
