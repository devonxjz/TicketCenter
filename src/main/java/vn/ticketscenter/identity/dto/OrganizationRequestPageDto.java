package vn.ticketscenter.identity.dto;

import java.util.List;
import java.util.stream.Collectors;

/** One page of organisation requests for the admin list. */
public record OrganizationRequestPageDto(
        List<OrganizationRequestSummaryDto> items, long total, int page, int pageSize) {

    public String toJson() {
        String itemsJson =
                items.stream()
                        .map(OrganizationRequestSummaryDto::toJson)
                        .collect(Collectors.joining(",", "[", "]"));
        return "{\"items\":"
                + itemsJson
                + ",\"total\":"
                + total
                + ",\"page\":"
                + page
                + ",\"pageSize\":"
                + pageSize
                + "}";
    }
}
