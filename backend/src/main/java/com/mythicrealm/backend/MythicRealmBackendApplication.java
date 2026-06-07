package com.mythicrealm.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MythicRealmBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(MythicRealmBackendApplication.class, args);
    }
}
