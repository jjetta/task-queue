package com.jjetta.task_queue;

import org.springframework.boot.SpringApplication;

public class TestTaskQueueApplication {

	public static void main(String[] args) {
		SpringApplication.from(TaskQueueApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
