package com.jjetta.task_queue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TaskQueueApplication {

	public static void main(String[] args) {
		SpringApplication.run(TaskQueueApplication.class, args);
	}

}
