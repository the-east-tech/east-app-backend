package com.eastapp.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EastAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(EastAppApplication.class, args);
    }

}
