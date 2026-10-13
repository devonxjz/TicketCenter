package vn.ticketscenter.identity.dto;

import java.util.UUID;

public record ProfileDto(UUID id, String email, String userName, String fullName, String phone) {
}
