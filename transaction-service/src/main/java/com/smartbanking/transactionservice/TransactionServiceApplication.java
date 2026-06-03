package com.smartbanking.transactionservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.kafka.annotation.EnableKafka;

// @EnableKafka — activates @KafkaListener scanning.
// Our SagaEventListener uses @KafkaListener to consume events.
// Without this annotation, those listeners are silently ignored.

@SpringBootApplication
@EnableDiscoveryClient
@EnableKafka
public class TransactionServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(TransactionServiceApplication.class, args);
	}
}