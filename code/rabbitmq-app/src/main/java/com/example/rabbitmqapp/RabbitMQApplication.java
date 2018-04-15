package com.example.rabbitmqapp;

import org.springframework.amqp.core.Queue;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RabbitMQApplication {
  public static void main(String[] args) {
    SpringApplication.run(RabbitMQApplication.class, args);
  }

  @Bean
  public Queue hello() {
    return new Queue("hello");
  }

  @Profile("sender")
  @Bean
  public MySender sender() {
    return new MySender();
  }

  @Profile("receiver")
  @Bean
  public MyReceiver receiver1() {
    return new MyReceiver();
  }
}
