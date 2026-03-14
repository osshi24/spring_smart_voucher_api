package com.smartvoucher.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BulkAssignRequest {

    @NotEmpty(message = "Customer IDs must not be empty")
    @Size(max = 1000, message = "Maximum 1000 customers per request")
    private List<Long> customerIds;
}
