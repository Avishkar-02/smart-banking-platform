package com.smartbanking.common.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

// Used when returning paginated lists — transaction history, account list etc.
// Contains both the data AND pagination metadata.

@Getter
@Builder
public class PageResponse<T> {

    private List<T> content;
    private int page;
    private int size;
    // Total items across ALL pages — frontend needs this to render page controls
    private long totalElements;
    private int totalPages;
    // Frontend uses these to enable/disable next/prev buttons
    private boolean first;
    private boolean last;
}