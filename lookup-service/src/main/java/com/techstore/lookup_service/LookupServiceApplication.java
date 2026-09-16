package com.techstore.lookup_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class LookupServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(LookupServiceApplication.class, args);
	}

}
