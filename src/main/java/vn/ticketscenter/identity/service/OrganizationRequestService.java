package vn.ticketscenter.identity.service;

import vn.ticketscenter.identity.dto.OrganizationRequestCreateDto;
import vn.ticketscenter.identity.dto.OrganizationRequestDetailDto;
import vn.ticketscenter.identity.dto.OrganizationRequestSummaryDto;
import java.util.List;
import java.util.UUID;

/** What a requester can do with organisation requests: submit one and read their own. */
public interface OrganizationRequestService {
    /**
     * Submits a new organisation request. When an idempotency key is given, a retry with the same payload
     * returns the first result and a retry with a different payload is rejected.
     */
    OrganizationRequestDetailDto submitRequest(
            UUID actorId, OrganizationRequestCreateDto dto, String idempotencyKey);

    /** All requests the actor has submitted. */
    List<OrganizationRequestSummaryDto> getMyRequests(UUID actorId);

    /**
     * One request's detail. Anyone but an admin may only read a request they submitted.
     *
     * @throws SecurityException when a non-admin asks for someone else's request
     */
    OrganizationRequestDetailDto getRequestDetail(
            UUID actorId, boolean isAdmin, UUID organizationId);
}
