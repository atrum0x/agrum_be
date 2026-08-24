package com.atrum.agrum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class AgrumApplication {

	public static void main(String[] args) {
		SpringApplication.run(AgrumApplication.class, args);
	}

}
