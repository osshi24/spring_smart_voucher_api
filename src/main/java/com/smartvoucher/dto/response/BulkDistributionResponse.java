package com.smartvoucher.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class BulkDistributionResponse {
    private int total;
    private int succeeded;
    private int failed;
    private List<BulkDistributionError> errors;

    @Getter
    @Builder
    public static class BulkDistributionError {
        private int index;
        private String reason;
    }
}
