package com.smartvoucher.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class VoucherExpiredException extends RuntimeException {
    public VoucherExpiredException(String message) {
        super(message);
    }
}
