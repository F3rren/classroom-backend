package com.classroom.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The single entry point towards the services.
 *
 * There is no code here beyond this class: the routes are declared in application.yml,
 * because they are configuration and not logic. Adding a service means adding an entry to
 * that file, not recompiling this module.
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
