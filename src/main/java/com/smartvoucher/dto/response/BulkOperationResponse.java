package com.smartvoucher.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class BulkOperationResponse {
    private int total;
    private int processed;
    private int skipped;
    private List<BulkError> errors;

    @Getter
    @Builder
    public static class BulkError {
        private Object ref;
        private String reason;
    }
}
