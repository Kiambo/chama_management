package com.personal.chama;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ChamaApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChamaApplication.class, args);
	}

}
