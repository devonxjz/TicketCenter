package vn.ticketscenter.identity.dto;

import vn.ticketscenter.identity.model.OrganizationRole;

import java.util.UUID;

public record MembershipSummaryDto(
        UUID organizationId, String organizationName, OrganizationRole role, boolean isActive) {}
