package vn.ticketscenter.fulfillment.refund.dto;

import java.util.List;
import java.util.stream.Collectors;

public record AdminRefundPageDto(
        List<AdminRefundSummaryDto> items, long total, int page, int pageSize) {
    public AdminRefundPageDto {
        items = List.copyOf(items);
    }

    public String json() {
        return "{\"items\":"
                + items.stream()
                        .map(AdminRefundSummaryDto::json)
                        .collect(Collectors.joining(",", "[", "]"))
                + ",\"total\":"
                + total
                + ",\"page\":"
                + page
                + ",\"pageSize\":"
                + pageSize
                + "}";
    }
}
