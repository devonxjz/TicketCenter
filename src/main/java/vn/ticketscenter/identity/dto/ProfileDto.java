package vn.ticketscenter.identity.dto;

import vn.ticketscenter.config.web.HttpResponses;

import java.util.UUID;

public record ProfileDto(UUID id, String email, String userName, String fullName, String phone) {

    public String toJson() {
        return "{\"id\":" + HttpResponses.jsonString(String.valueOf(id))
                + ",\"email\":" + HttpResponses.jsonString(email)
                + ",\"userName\":" + HttpResponses.jsonString(userName)
                + ",\"fullName\":" + HttpResponses.jsonString(fullName)
                + ",\"phone\":" + HttpResponses.jsonString(phone) + "}";
    }
}
