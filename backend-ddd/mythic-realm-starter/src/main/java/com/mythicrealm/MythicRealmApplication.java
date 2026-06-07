package com.mythicrealm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.mythicrealm")
@EnableScheduling
public class MythicRealmApplication {
    public static void main(String[] args) {
        SpringApplication.run(MythicRealmApplication.class, args);
    }
}
