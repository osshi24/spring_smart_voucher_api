package com.smartvoucher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SmartVoucherApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartVoucherApplication.class, args);
    }
}
