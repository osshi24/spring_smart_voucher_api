package com.smartvoucher.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class BulkDistributeResponse {

    private int total;
    private int sent;
    private int skipped;
    private int failed;
    private List<BulkError> errors;

    public record BulkError(Long customerId, String reason) {}
}
