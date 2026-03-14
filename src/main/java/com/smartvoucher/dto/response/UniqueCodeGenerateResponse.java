package com.smartvoucher.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UniqueCodeGenerateResponse {
    private int generated;
    private long total;
}
