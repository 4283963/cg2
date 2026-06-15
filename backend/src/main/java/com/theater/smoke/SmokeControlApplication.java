package com.theater.smoke;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmokeControlApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmokeControlApplication.class, args);
    }
}
