package vn.ticketscenter.fulfillment.checkin.service;

import vn.ticketscenter.fulfillment.checkin.CheckInException;
import vn.ticketscenter.fulfillment.checkin.cache.CheckInScanCache;
import vn.ticketscenter.fulfillment.checkin.dto.CheckInScanRequestDto;
import vn.ticketscenter.fulfillment.checkin.dto.CheckInScanResponseDto;
import vn.ticketscenter.fulfillment.checkin.dto.RecentCheckInLogDto;
import vn.ticketscenter.fulfillment.checkin.dao.TicketCheckInRepository;
import vn.ticketscenter.fulfillment.checkin.security.SignedQrDecoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class TicketCheckInServiceImpl implements TicketCheckInService {
    private final TicketCheckInRepository repository;
    private final SignedQrDecoder decoder;
    private final CheckInScanCache cache;

    public TicketCheckInServiceImpl(
            TicketCheckInRepository repository, SignedQrDecoder decoder, CheckInScanCache cache) {
        this.repository = Objects.requireNonNull(repository);
        this.decoder = Objects.requireNonNull(decoder);
        this.cache = Objects.requireNonNull(cache);
    }

    @Override
    public CheckInScanResponseDto scan(UUID actor, UUID key, CheckInScanRequestDto request) {
        requireActor(actor);
        if (key == null) {
            throw new CheckInException(
                    400, "IDEMPOTENCY_REQUIRED", "Cần mã yêu cầu để soát vé an toàn.");
        }

        UUID ticket = null;
        String name = null;
        boolean invalidSignature = false;
        if (request.qrContent() != null) {
            try {
                SignedQrDecoder.Decoded decoded = decoder.decode(request.qrContent());
                ticket = decoded.ticketId();
                name = decoded.name();
            } catch (CheckInException exception) {
                if (!"INVALID_SIGNATURE".equals(exception.code())) {
                    throw exception;
                }
                invalidSignature = true;
            }
        }

        String fingerprint = fingerprint(request);
        // Redis reduces contention; SQL owns locking and durable request replay.
        try (CheckInScanCache.Lease lease = cache.lock(ticket)) {
            CheckInScanResponseDto result =
                    repository.scan(
                            actor,
                            key,
                            request.eventId(),
                            ticket,
                            request.ticketCode(),
                            name,
                            fingerprint,
                            invalidSignature);
            cache.completed(actor, key, result);
            return result;
        }
    }

    @Override
    public List<RecentCheckInLogDto> recent(UUID actor, UUID event, int limit) {
        requireActor(actor);
        if (limit < 1 || limit > 100) {
            throw new CheckInException(
                    400, "INVALID_PAGE_SIZE", "Số dòng nhật ký phải từ 1 đến 100.");
        }
        return repository.recent(actor, event, limit);
    }

    private static String fingerprint(CheckInScanRequestDto request) {
        String input =
                request.eventId()
                        + "\n"
                        + (request.qrContent() != null
                                ? "QR\n" + request.qrContent()
                                : "CODE\n" + request.ticketCode());
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void requireActor(UUID actor) {
        if (actor == null) {
            throw new CheckInException(401, "UNAUTHENTICATED", "Bạn chưa đăng nhập.");
        }
    }
}
