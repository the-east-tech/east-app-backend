package com.eastapp.backend;

import org.springframework.boot.SpringApplication;

public class TestEastAppApplication {

    public static void main(String[] args) {
        SpringApplication.from(EastAppApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
