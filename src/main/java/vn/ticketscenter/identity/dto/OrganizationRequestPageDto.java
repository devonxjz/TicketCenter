package vn.ticketscenter.identity.dto;

import java.util.List;

import static vn.ticketscenter.config.web.Json.array;
import static vn.ticketscenter.config.web.Json.field;
import static vn.ticketscenter.config.web.Json.number;
import static vn.ticketscenter.config.web.Json.object;

/** One page of organisation requests for the admin list. */
public record OrganizationRequestPageDto(
        List<OrganizationRequestSummaryDto> items, long total, int page, int pageSize) {

    public String toJson() {
        return object(
                field("items", array(items, OrganizationRequestSummaryDto::toJson)),
                field("total", number(total)),
                field("page", number(page)),
                field("pageSize", number(pageSize)));
    }
}
