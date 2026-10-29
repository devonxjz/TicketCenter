package vn.ticketscenter.fulfillment.ticket.service;

import java.util.Optional;
import vn.ticketscenter.fulfillment.ticket.cache.TicketCache;
import vn.ticketscenter.fulfillment.ticket.dto.TicketDetailDto;
import vn.ticketscenter.fulfillment.ticket.dto.TicketSummaryDto;
import vn.ticketscenter.fulfillment.ticket.dao.TicketRepository;
import vn.ticketscenter.fulfillment.ticket.dao.TicketRepository.TicketRecord;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class TicketServiceImpl implements TicketService {
    private final TicketRepository repository;
    private final SignedTicketQrSigner qrSigner;
    private final TicketCache cache;

    public TicketServiceImpl(
            TicketRepository repository, SignedTicketQrSigner qrSigner, TicketCache cache) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
        this.qrSigner = Objects.requireNonNull(qrSigner, "qrSigner is required");
        this.cache = Objects.requireNonNull(cache, "cache is required");
    }

    protected TicketServiceImpl() {
        this.repository = null;
        this.qrSigner = null;
        this.cache = null;
    }

    @Override
    public List<TicketSummaryDto> getMyTickets(UUID actorId) {
        return getMyTickets(actorId, -1);
    }

    @Override
    public List<TicketSummaryDto> getMyTickets(UUID actorId, int authVersion) {
        if (actorId == null) {
            throw TicketException.unauthenticated();
        }
        return repository.findTicketsByBuyerId(actorId, authVersion);
    }

    @Override
    public TicketDetailDto getTicketDetail(UUID actorId, UUID ticketId) {
        return getTicketDetail(actorId, -1, ticketId);
    }

    @Override
    public TicketDetailDto getTicketDetail(UUID actorId, int authVersion, UUID ticketId) {
        if (actorId == null) {
            throw TicketException.unauthenticated();
        }
        if (ticketId == null) {
            throw TicketException.badRequest("ticketId is required");
        }

        Optional<TicketRecord> opt = repository.findById(ticketId, actorId, authVersion);
        if (opt.isEmpty()) {
            repository.assertTicketAccess(ticketId, actorId);
            throw TicketException.notFound();
        }
        TicketRecord record = opt.get();

        if (!actorId.equals(record.buyerId())) {
            throw TicketException.forbidden();
        }

        // read through the cache
        return cache.getTicketDetail(ticketId)
                .filter(
                        cached ->
                                cached.version() == record.version()
                                        && cached.status().equals(record.status()))
                .orElseGet(
                        () -> {
                            TicketDetailDto dto = record.toDetailDto();
                            cache.putTicketDetail(dto);
                            return dto;
                        });
    }

    @Override
    public byte[] getTicketQrPng(UUID actorId, UUID ticketId, int width, int height) {
        return getTicketQrPng(actorId, -1, ticketId, width, height);
    }

    @Override
    public byte[] getTicketQrPng(
            UUID actorId, int authVersion, UUID ticketId, int width, int height) {
        if (actorId == null) {
            throw TicketException.unauthenticated();
        }
        if (ticketId == null) {
            throw TicketException.badRequest("ticketId is required");
        }

        Optional<TicketRecord> opt = repository.findById(ticketId, actorId, authVersion);
        if (opt.isEmpty()) {
            repository.assertTicketAccess(ticketId, actorId);
            throw TicketException.notFound();
        }
        TicketRecord record = opt.get();

        if (!actorId.equals(record.buyerId())) {
            throw TicketException.forbidden();
        }

        long version = record.version();
        Optional<byte[]> cachedPng = cache.getTicketQrPng(ticketId, version);
        if (cachedPng.isPresent()) {
            return cachedPng.get();
        }

        // display name for the canonical QR payload
        String displayName = record.eventTitle();
        if (record.seatLabel() != null && !record.seatLabel().isBlank()) {
            displayName += " - " + record.seatLabel();
        } else if (record.zoneName() != null && !record.zoneName().isBlank()) {
            displayName += " - " + record.zoneName();
        }

        String envelope = qrSigner.buildEnvelope(ticketId, displayName);
        byte[] pngBytes = SignedTicketQrSigner.generateQrPng(envelope, width, height);

        // cache with a bounded TTL
        cache.putTicketQrPng(ticketId, version, pngBytes);
        return pngBytes;
    }

    @Override
    public void invalidate(UUID ticketId) {
        cache.invalidate(ticketId);
    }
}
