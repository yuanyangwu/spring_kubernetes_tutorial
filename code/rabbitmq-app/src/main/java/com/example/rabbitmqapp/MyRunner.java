package com.example.rabbitmqapp;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class MyRunner implements CommandLineRunner {

	@Override
	public void run(String... args) throws Exception {
		while (true) {
			Thread.sleep(1000);
		}
	}

}
