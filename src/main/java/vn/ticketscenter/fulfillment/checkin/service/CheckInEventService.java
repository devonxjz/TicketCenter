package vn.ticketscenter.fulfillment.checkin.service;

import vn.ticketscenter.fulfillment.checkin.CheckInException;
import vn.ticketscenter.fulfillment.checkin.cache.CheckInEventCache;
import vn.ticketscenter.fulfillment.checkin.dto.CheckInEventSummaryDto;
import vn.ticketscenter.fulfillment.checkin.dto.CheckInWindowStatusDto;
import vn.ticketscenter.fulfillment.checkin.repository.CheckInEventRepository;
import vn.ticketscenter.fulfillment.checkin.repository.CheckInEventRepository.EventInfo;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

public class CheckInEventService {
    private final CheckInEventRepository repository;
    private final CheckInEventCache cache;
    private final Clock clock;
    public CheckInEventService(CheckInEventRepository repository, CheckInEventCache cache, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.cache = Objects.requireNonNull(cache);
        this.clock = Objects.requireNonNull(clock);
    }

    public List<CheckInEventSummaryDto> events(UUID actorId, UUID organizationId) {
        requireActor(actorId);
        List<UUID> organizations = repository.authorizedOrganizations(actorId);
        if (organizations.isEmpty() || (organizationId != null && !organizations.contains(organizationId))) {
            throw forbidden();
        }
        Instant now = clock.instant();
        List<CheckInEventSummaryDto> summaries = new ArrayList<>();
        for (UUID orgId : organizationId == null ? organizations : List.of(organizationId)) {
            // Cached membership or event state must never authorize a request.
            List<EventInfo> current = repository.accessibleEvents(actorId, orgId, now);
            Map<UUID, EventInfo> cached = new HashMap<>();
            cache.events(actorId, orgId).ifPresent(events -> events.forEach(e -> cached.put(e.eventId(), e)));
            List<EventInfo> refreshed = new ArrayList<>();
            boolean changed = current.size() != cached.size();
            if (!changed) changed = current.stream().anyMatch(e -> !sameMetadata(e, cached.get(e.eventId())));
            for (EventInfo event : current) {
                EventInfo old = cached.get(event.eventId());
                EventInfo counted;
                // A rewritten list gets a new TTL, so refresh every counter together.
                // Otherwise repeated schedule edits could keep unrelated counts stale forever.
                if (!changed && sameMetadata(event, old)) counted = old;
                else counted = repository.withTicketCounts(event);
                refreshed.add(counted);
                String status = windowStatus(event, now);
                summaries.add(new CheckInEventSummaryDto(event.eventId(), event.title(), event.venue(),
                        event.startTime(), event.endTime(), event.windowStart(), event.isOpen(now),
                        counted.issued(), counted.checkedIn(), status));
            }
            // Don't renew a cache hit's TTL: ticket counts must refresh after 60 seconds.
            if (changed || cached.isEmpty()) cache.putEvents(actorId, orgId, List.copyOf(refreshed));
        }
        summaries.sort(Comparator.comparing(CheckInEventSummaryDto::startTime)
                .thenComparing(e -> e.eventId().toString()));
        return List.copyOf(summaries);
    }

    public CheckInWindowStatusDto window(UUID actorId, UUID eventId) {
        requireActor(actorId);
        Objects.requireNonNull(eventId, "eventId");
        EventInfo cached = cache.window(eventId).orElse(null);
        // A cache hit still requires current database authorization.
        EventInfo current = cached != null && repository.isCachedWindowCurrent(actorId, eventId, cached)
                ? cached : repository.accessibleEvent(actorId, eventId);
        if (!sameMetadata(current, cached)) {
            if (cached != null) cache.invalidate(eventId, current.organizationId());
            cache.putWindow(current);
        }
        Instant now = clock.instant();
        if ("CANCELLED".equals(current.status())) {
            throw new CheckInException(400, "EVENT_CANCELLED", "Sự kiện đã bị hủy bỏ.");
        }
        // The canonical UDF and Java must agree; a legacy UDF cannot silently change the contract.
        boolean open = repository.isCheckInOpen(eventId, now);
        if (open != current.isOpen(now)) {
            throw new CheckInException(503, "CHECK_IN_CONTRACT_MISMATCH", "Khung giờ soát vé chưa được cấu hình đồng bộ.");
        }
        String status = windowStatus(current, now);
        return new CheckInWindowStatusDto(eventId, open, status, current.windowStart(), current.endTime(), now,
                switch (status) {
                    case "OPEN" -> "Cổng soát vé đang hoạt động bình thường.";
                    case "NOT_YET_OPEN" -> "Chưa đến thời gian mở cổng soát vé.";
                    case "ENDED" -> "Sự kiện đã kết thúc.";
                    default -> "Sự kiện chưa được mở soát vé.";
                });
    }

    public void invalidateEvent(UUID eventId, UUID organizationId) { cache.invalidate(eventId, organizationId); }
    private static String windowStatus(EventInfo event, Instant now) {
        if ("CANCELLED".equals(event.status())) return "CANCELLED";
        if (!"PUBLISHED".equals(event.status())) return "UNAVAILABLE";
        if (now.isBefore(event.windowStart())) return "NOT_YET_OPEN";
        return now.isBefore(event.endTime()) ? "OPEN" : "ENDED";
    }
    private static boolean sameMetadata(EventInfo a, EventInfo b) {
        return b != null && a.eventId().equals(b.eventId()) && a.organizationId().equals(b.organizationId())
                && a.title().equals(b.title()) && a.venue().equals(b.venue()) && a.startTime().equals(b.startTime())
                && a.endTime().equals(b.endTime()) && a.status().equals(b.status());
    }
    private static void requireActor(UUID actorId) {
        if (actorId == null) throw new CheckInException(401, "UNAUTHENTICATED", "Bạn chưa đăng nhập.");
    }
    private static CheckInException forbidden() {
        return new CheckInException(403, "CHECK_IN_FORBIDDEN", "Bạn không có quyền soát vé tại tổ chức này.");
    }
}
