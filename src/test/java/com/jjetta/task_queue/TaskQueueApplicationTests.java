package com.jjetta.task_queue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TaskQueueApplicationTests {

	@Test
	void contextLoads() {
	}

}
