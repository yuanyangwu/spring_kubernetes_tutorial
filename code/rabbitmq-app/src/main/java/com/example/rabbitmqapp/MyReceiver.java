package com.example.rabbitmqapp;

import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import lombok.extern.java.Log;

@Log
@RabbitListener(queues = "hello")
public class MyReceiver {

	public MyReceiver() {
	}

	@RabbitHandler
	public void receive(String in) throws Exception {
		Thread.sleep(100);
		if (Math.random() < 0.2) {
			log.severe("[x] Received Exception " + in);
			throw new Exception("Exception " + in);
		}
		log.info("[x] Received '" + in + "'");
	}
}