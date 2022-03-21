package ru.devvault.vk.poster;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import ru.devvault.vk.poster.service.PosterService;

@SpringBootApplication
@Slf4j
@RequiredArgsConstructor
public class PosterApplication implements CommandLineRunner {

	private final PosterService posterService;

	public static void main(String[] args) {
		SpringApplication.run(PosterApplication.class, args);
	}

	@Override
	public void run(String ...args) throws InterruptedException {
		try {
			System.exit(posterService.process());
		} catch (InterruptedException e) {
			log.error("Interrupted exception: {}", e.getMessage());
			throw e;
		} catch (Exception e) {
			log.error("Unhandled error exception: {}", e.getMessage());
			System.exit(2);
		}
	}

}
