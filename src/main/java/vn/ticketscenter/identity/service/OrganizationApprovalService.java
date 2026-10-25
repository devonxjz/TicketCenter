package vn.ticketscenter.identity.service;

import vn.ticketscenter.identity.dto.OrganizationRequestDetailDto;
import vn.ticketscenter.identity.dto.OrganizationRequestSummaryDto;
import vn.ticketscenter.identity.model.OrganizationStatus;
import vn.ticketscenter.identity.dao.OrganizationDecisionRepository.CommissionPolicy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** What an admin can do with organisation requests: list, inspect, approve and reject them. */
public interface OrganizationApprovalService {
    /** One page of requests with the given status ({@code null} for every status), newest first. */
    List<OrganizationRequestSummaryDto> listRequests(
            UUID adminId, OrganizationStatus status, int page, int pageSize);

    long countRequests(UUID adminId, OrganizationStatus status);

    /** The detail of any request; the caller has already established that the actor is an admin. */
    OrganizationRequestDetailDto getRequestDetail(UUID adminId, UUID organizationId);

    /**
     * Approves a pending request: the requester becomes its manager, the commission policy (if any) is recorded,
     * and in demo mode an initial event is created.
     */
    OrganizationRequestDetailDto approve(
            UUID adminId, UUID organizationId, Optional<CommissionPolicy> policy);

    /** Rejects a pending request; the reason must be 10 to 500 characters. */
    OrganizationRequestDetailDto reject(UUID adminId, UUID organizationId, String reason);
}
