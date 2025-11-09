package com.ua.pohribnyi.fitadvisorbot;

import org.springframework.boot.SpringApplication;

public class TestSmartFitnessAdvisorBotApplication {

	public static void main(String[] args) {
		SpringApplication.from(SmartFitnessAdvisorBotApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
