package vn.ticketscenter.fulfillment.refund.dto;

import java.util.List;
import java.util.stream.Collectors;

public record RefundPageDto(List<RefundSummaryDto> items, long total, int page, int pageSize) {
    public RefundPageDto {
        items = List.copyOf(items);
    }

    public String json() {
        return "{\"items\":"
                + items.stream()
                        .map(RefundSummaryDto::json)
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
