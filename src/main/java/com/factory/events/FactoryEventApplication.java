package com.factory.events;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
public class FactoryEventApplication {

    public static void main(String[] args) {
        SpringApplication.run(FactoryEventApplication.class, args);
    }
}