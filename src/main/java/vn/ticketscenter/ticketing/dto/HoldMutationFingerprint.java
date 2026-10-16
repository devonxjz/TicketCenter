package vn.ticketscenter.ticketing.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

public final class HoldMutationFingerprint {
    private HoldMutationFingerprint() {}

    public static String create(CreateHoldRequest request) {
        return hash(
                "tc:hold:mutation:v1:CREATE\n" + Objects.requireNonNull(request).canonicalHash());
    }

    public static String cancel(UUID holdId) {
        if (holdId == null || holdId.equals(new UUID(0, 0)))
            throw new IllegalArgumentException("holdId must be a nonzero UUID");
        return hash("tc:hold:mutation:v1:CANCEL\n" + holdId);
    }

    private static String hash(String canonical) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by Java", impossible);
        }
    }
}
