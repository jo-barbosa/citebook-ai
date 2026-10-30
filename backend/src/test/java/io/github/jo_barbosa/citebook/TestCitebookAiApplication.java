package io.github.jo_barbosa.citebook;

import org.springframework.boot.SpringApplication;

public class TestCitebookAiApplication {

	public static void main(String[] args) {
		SpringApplication.from(CitebookAiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
