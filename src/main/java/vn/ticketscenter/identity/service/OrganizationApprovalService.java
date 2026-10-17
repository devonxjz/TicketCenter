package vn.ticketscenter.identity.service;

import jakarta.persistence.EntityManager;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.dto.OrganizationRequestDetailDto;
import vn.ticketscenter.identity.dto.OrganizationRequestSummaryDto;
import vn.ticketscenter.identity.model.Organization;
import vn.ticketscenter.identity.model.OrganizationStatus;
import vn.ticketscenter.identity.dao.OrganizationDecisionRepository;
import vn.ticketscenter.identity.dao.OrganizationDecisionRepository.Approval;
import vn.ticketscenter.identity.dao.OrganizationDecisionRepository.CommissionPolicy;
import vn.ticketscenter.identity.dao.OrganizationDecisionRepository.InitialEvent;
import vn.ticketscenter.identity.dao.OrganizationRepository;
import vn.ticketscenter.identity.dao.UserRepository;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** What an admin can do with organisation requests: list, inspect, approve and reject them. */
public class OrganizationApprovalService {

    private static final String NOT_FOUND = "ORGANIZATION_NOT_FOUND: Không tìm thấy hồ sơ yêu cầu";
    private static final String CONFLICT = "CONFLICT: Yêu cầu không ở trạng thái PENDING_APPROVAL";

    private final TransactionManager transactions;
    private final OrganizationRepository organizationRepository;
    private final OrganizationDecisionRepository decisions;
    private final AdminGuard adminGuard;
    private final DemoInitialEventFactory initialEvents;

    public OrganizationApprovalService(
            TransactionManager transactions,
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            OrganizationDecisionRepository decisions,
            DemoInitialEventFactory initialEvents) {
        this.transactions = Objects.requireNonNull(transactions, "transactions must not be null.");
        this.organizationRepository =
                Objects.requireNonNull(
                        organizationRepository, "organizationRepository must not be null.");
        this.adminGuard = new AdminGuard(userRepository);
        this.decisions = Objects.requireNonNull(decisions, "decisions must not be null.");
        this.initialEvents =
                Objects.requireNonNull(initialEvents, "initialEvents must not be null.");
    }

    public OrganizationApprovalService(
            TransactionManager transactions,
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            OrganizationDecisionRepository decisions) {
        this(
                transactions,
                organizationRepository,
                userRepository,
                decisions,
                new DemoInitialEventFactory());
    }

    public OrganizationApprovalService(
            TransactionManager transactions,
            OrganizationRepository organizationRepository,
            UserRepository userRepository) {
        this(
                transactions,
                organizationRepository,
                userRepository,
                new OrganizationDecisionRepository());
    }

    /** One page of requests with the given status ({@code null} for every status), newest first. */
    public List<OrganizationRequestSummaryDto> listRequests(
            UUID adminId, OrganizationStatus status, int page, int pageSize) {
        requireId(adminId, "adminId");
        return transactions.execute(
                DatabasePrincipal.AUTH,
                em -> {
                    adminGuard.requireAdmin(em, adminId, "xem danh sách yêu cầu");
                    int size = Math.max(1, pageSize);
                    int offset = (Math.max(1, page) - 1) * size;
                    List<Organization> requests =
                            organizationRepository.findByStatus(em, status, offset, size);
                    return requests == null
                            ? List.<OrganizationRequestSummaryDto>of()
                            : requests.stream().map(OrganizationRequestSummaryDto::from).toList();
                });
    }

    public long countRequests(UUID adminId, OrganizationStatus status) {
        requireId(adminId, "adminId");
        return transactions.execute(
                DatabasePrincipal.AUTH,
                em -> {
                    adminGuard.requireAdmin(em, adminId, "xem danh sách yêu cầu");
                    return organizationRepository.countByStatus(em, status);
                });
    }

    /** The detail of any request; the caller has already established that the actor is an admin. */
    public OrganizationRequestDetailDto getRequestDetail(UUID adminId, UUID organizationId) {
        requireId(adminId, "actorId");
        requireId(organizationId, "organizationId");
        return transactions.execute(
                DatabasePrincipal.AUTH,
                em ->
                        OrganizationRequestDetailDto.from(
                                find(organizationRepository, em, organizationId)));
    }

    /**
     * Approves a pending request: the requester becomes its manager, the commission policy (if any) is recorded,
     * and in demo mode an initial event is created.
     */
    public OrganizationRequestDetailDto approve(
            UUID adminId, UUID organizationId, Optional<CommissionPolicy> policy) {
        requireId(adminId, "adminId");
        requireId(organizationId, "organizationId");
        InitialEvent initialEvent =
                transactions.execute(
                        DatabasePrincipal.AUTH,
                        em -> {
                            adminGuard.requireAdmin(em, adminId, "phê duyệt yêu cầu");
                            return initialEvents.create(requirePending(em, organizationId));
                        });
        Approval approval =
                decisions.approve(
                        transactions, organizationId, adminId, policy.orElse(null), initialEvent);

        return transactions.execute(
                DatabasePrincipal.AUTH,
                em -> {
                    Organization saved = find(organizationRepository, em, organizationId);
                    return new OrganizationRequestDetailDto(
                            saved.getId(),
                            saved.getName(),
                            saved.getContactEmail(),
                            saved.getContactPhone(),
                            saved.getDescription(),
                            saved.getStatus(),
                            approval.managerId(),
                            saved.getSubmittedAt(),
                            saved.getRejectionReason(),
                            approval.commissionRuleId());
                });
    }

    /** Rejects a pending request; the reason must be 10 to 500 characters. */
    public OrganizationRequestDetailDto reject(UUID adminId, UUID organizationId, String reason) {
        requireId(adminId, "adminId");
        requireId(organizationId, "organizationId");
        if (reason == null || reason.trim().length() < 10 || reason.trim().length() > 500) {
            throw new IllegalArgumentException("Lý do từ chối phải từ 10 đến 500 ký tự");
        }
        transactions.execute(
                DatabasePrincipal.AUTH,
                em -> {
                    adminGuard.requireAdmin(em, adminId, "từ chối yêu cầu");
                    requirePending(em, organizationId);
                    return null;
                });
        decisions.reject(transactions, organizationId, adminId, reason.trim());
        return getRequestDetail(adminId, organizationId);
    }

    private Organization requirePending(EntityManager em, UUID organizationId) {
        Organization org = find(organizationRepository, em, organizationId);
        if (org.getStatus() != OrganizationStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(CONFLICT);
        }
        return org;
    }

    private static Organization find(
            OrganizationRepository repository, EntityManager em, UUID organizationId) {
        return repository
                .findById(em, organizationId)
                .orElseThrow(() -> new NoSuchElementException(NOT_FOUND));
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " must not be null.");
        }
    }
}
