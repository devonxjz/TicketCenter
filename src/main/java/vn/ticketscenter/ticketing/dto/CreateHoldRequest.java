package vn.ticketscenter.ticketing.dto;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

public record CreateHoldRequest(UUID eventId, List<HoldSelection> selections) {
    public CreateHoldRequest {
        if (eventId == null || eventId.equals(new UUID(0, 0))) {
            throw new IllegalArgumentException("eventId must be a nonzero UUID");
        }
        if (selections == null || selections.isEmpty() || selections.size() > 8) {
            throw new IllegalArgumentException("selections must contain 1..8 entries");
        }
        var standing = new HashMap<UUID, Integer>();
        var seats = new HashSet<UUID>();
        var normalized = new ArrayList<HoldSelection>();
        int total = 0;
        for (HoldSelection selection : selections) {
            if (selection == null) {
                throw new IllegalArgumentException("selection must not be null");
            }
            total += selection.quantity();
            if (total > 8) {
                throw new IllegalArgumentException("a hold must contain 1..8 tickets");
            }
            if (selection.seatId() == null) {
                standing.merge(selection.zoneId(), selection.quantity(), Integer::sum);
            } else {
                if (!seats.add(selection.seatId())) {
                    throw new IllegalArgumentException("a seat must appear only once");
                }
                normalized.add(selection);
            }
        }
        standing.forEach((zone, quantity) -> normalized.add(new HoldSelection(zone, null, quantity)));
        normalized.sort(Comparator.comparing((HoldSelection selection) -> selection.zoneId().toString())
                .thenComparing(selection -> selection.seatId() == null ? "" : selection.seatId().toString()));
        selections = List.copyOf(normalized);
    }

    /** Version 1 uses big-endian UUID bits, a count, and a seat-presence byte per selection. */
    public String canonicalHash() {
        ByteBuffer bytes = ByteBuffer.allocate(1 + 16 + 4 + selections.size() * (16 + 1 + 16 + 4));
        bytes.put((byte) 1);
        putUuid(bytes, eventId);
        bytes.putInt(selections.size());
        for (HoldSelection selection : selections) {
            putUuid(bytes, selection.zoneId());
            bytes.put((byte) (selection.seatId() == null ? 0 : 1));
            if (selection.seatId() != null) {
                putUuid(bytes, selection.seatId());
            }
            bytes.putInt(selection.quantity());
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(bytes.array(), 0, bytes.position());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by Java", impossible);
        }
    }

    private static void putUuid(ByteBuffer bytes, UUID id) {
        bytes.putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits());
    }
}
