package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class Program {
	public static void main(String[] args) {
		var application = new SpringApplication(Program.class);
		application.setWebApplicationType(WebApplicationType.NONE);
		application.run(args);
	}
}
