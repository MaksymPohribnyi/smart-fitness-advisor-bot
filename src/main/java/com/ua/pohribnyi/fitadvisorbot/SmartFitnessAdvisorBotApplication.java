package com.ua.pohribnyi.fitadvisorbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartFitnessAdvisorBotApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartFitnessAdvisorBotApplication.class, args);
	}

}
