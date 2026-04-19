package com.example.vendas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class VendasServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(VendasServiceApplication.class, args);
    }
}
