package ru.devvault.client.vk.poster;

import com.vk.api.sdk.objects.board.Topic;
import com.vk.api.sdk.objects.board.TopicComment;
import com.vk.api.sdk.objects.fave.Page;
import com.vk.api.sdk.objects.fave.Tag;
import com.vk.api.sdk.objects.groups.GroupFull;
import com.vk.api.sdk.objects.wall.GetFilter;
import com.vk.api.sdk.objects.wall.WallpostFull;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import ru.devvault.client.vk.poster.configuration.ClientProperties;
import ru.devvault.client.vk.poster.service.PosterService;
import ru.devvault.client.vk.poster.service.VkService;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

@SpringBootApplication
@Slf4j
@RequiredArgsConstructor
public class PosterApplication implements CommandLineRunner {

	private final PosterService posterService;

	public static void main(String[] args) {
		SpringApplication.run(PosterApplication.class, args);
	}

	@Override
	public void run(String ...args) {
		try {
			System.exit(posterService.process());
		} catch (Exception e) {
			log.error("Unhandled error exception: {}", e.getMessage());
			System.exit(2);
		}
	}

}
