package com.smartvoucher.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class HashGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
        System.out.println("admin123: " + encoder.encode("admin123"));
        System.out.println("staff123: " + encoder.encode("staff123"));
    }
}
