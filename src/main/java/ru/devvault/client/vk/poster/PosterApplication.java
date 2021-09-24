package ru.devvault.client.vk.poster;

import com.vk.api.sdk.objects.board.Topic;
import com.vk.api.sdk.objects.board.TopicComment;
import com.vk.api.sdk.objects.fave.Page;
import com.vk.api.sdk.objects.wall.GetFilter;
import com.vk.api.sdk.objects.wall.WallpostFull;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Set;

import static java.util.Objects.isNull;

@SpringBootApplication
@AllArgsConstructor
public class PosterApplication implements CommandLineRunner {

	private final ClientProperties clientProperties;
	private final VkService vkService;

	private static final Logger log = LoggerFactory.getLogger(PosterApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(PosterApplication.class, args);
	}

	private void sendTopicComments(Integer groupId, String groupName) throws InterruptedException {
		if (Boolean.FALSE.equals(clientProperties.getPostToGroupsTopics())) return;

		Set<Integer> excludedTopics = clientProperties.getExcludedGroupsTopics().get(groupId);
		for (Topic topic : vkService.getGroupTopics(groupId)) {
			Integer topicId = topic.getId();
			String topicTitle = topic.getTitle();

			if (isNull(excludedTopics) || !excludedTopics.contains(topicId)) {

				log.info("Selected group: {} - {} topic: {} - {}", groupId, groupName, topicId, topicTitle);

				TopicComment latestComment = vkService.getTopicComment(groupId, topicId);
				if (isNull(latestComment)) {
					if (vkService.createTopicComment(groupId, topicId)) {
						log.info("Topic comment posted! Group: {} - {}, topic: {} - {}", groupId, groupName, topicId, topicTitle);
					}
				} else {
					log.info("Topic comment already exists! Group: {} - {}, topic: {} - {}, comment: {}", groupId, groupName, topicId, topicTitle, latestComment);
				}

				Thread.sleep(clientProperties.getQueryInterval() * 3);
			} else {
				log.info("Processing topic excluded! Group: {} - {}, topic: {} - {}", groupId, groupName, topicId, topicTitle);
			}

			Thread.sleep(clientProperties.getQueryInterval());
		}
	}

	private void sendGroupMessage(Integer groupId, String groupName) throws InterruptedException {
		if (Boolean.FALSE.equals(clientProperties.getPostToGroups())) return;

		if (!clientProperties.getExcludedGroups().contains(groupId)) {
			WallpostFull latestPost = vkService.getGroupPost(groupId, GetFilter.SUGGESTS);
			if (isNull(latestPost)) {
				latestPost = vkService.getGroupPost(groupId, GetFilter.ALL);
			}
			if (isNull(latestPost)) {
				if (vkService.postWallMessage(groupId)) {
					log.info("Group message posted! Group: {} - {}", groupId, groupName);
				}
			} else {
				log.info("Post already exists! Group: {} - {}, post: {}", groupId, groupName, latestPost);
			}

			Thread.sleep(clientProperties.getQueryInterval() * 3);
		} else {
			log.info("Processing group excluded! Group: {} - {}", groupId, groupName);
		}

		Thread.sleep(clientProperties.getQueryInterval());
	}

	@Override
	public void run(String ...args) throws Exception {
		if (Boolean.FALSE.equals(vkService.authorize())) {
			log.error("Failed to authorize API requests. Check your settings!");

			System.exit(1);
		}

		for (Integer tagId : vkService.getTags()) {

			log.info("Selected tag: {}", tagId);

			for (Page page : vkService.getTagPages(tagId)) {
				Integer groupId = page.getGroup().getId();
				String groupName = page.getGroup().getName();

				log.info("Selected group: {} - {}", groupId, groupName);

				sendTopicComments(groupId, groupName);
				sendGroupMessage(groupId, groupName);
			}
		}
	}

}
