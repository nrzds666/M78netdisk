package com.m78.netdisk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class M78NetdiskApplication {

    public static void main(String[] args) {
        SpringApplication.run(M78NetdiskApplication.class, args);
    }
}