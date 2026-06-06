package com.smartbanking.frauddetectionservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.kafka.annotation.EnableKafka;

// @EnableDiscoveryClient — registers with Eureka so other services
// can find us by name if they need to make HTTP calls to our admin endpoints.

// @EnableKafka — CRITICAL for this service.
// Without this, @KafkaListener in TransactionEventConsumer is silently ignored.
// The consumer never starts. Events pile up in Kafka unconsumed. No fraud checks run.

@SpringBootApplication
@EnableDiscoveryClient
@EnableKafka
public class FraudDetectionServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(FraudDetectionServiceApplication.class, args);
	}
}