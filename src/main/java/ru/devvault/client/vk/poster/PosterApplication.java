package ru.devvault.client.vk.poster;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.exceptions.ExceptionMapper;
import com.vk.api.sdk.exceptions.RequiredFieldException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.Validable;
import com.vk.api.sdk.objects.base.UserGroupFields;
import com.vk.api.sdk.objects.board.Topic;
import com.vk.api.sdk.objects.board.TopicComment;
import com.vk.api.sdk.objects.board.responses.GetCommentsResponse;
import com.vk.api.sdk.objects.board.responses.GetTopicsResponse;
import com.vk.api.sdk.objects.fave.GetPagesType;
import com.vk.api.sdk.objects.fave.Page;
import com.vk.api.sdk.objects.fave.Tag;
import com.vk.api.sdk.objects.fave.responses.GetPagesResponse;
import com.vk.api.sdk.objects.fave.responses.GetTagsResponse;
import com.vk.api.sdk.objects.wall.GetFilter;
import com.vk.api.sdk.objects.wall.WallpostFull;
import com.vk.api.sdk.objects.wall.responses.PostResponse;
import com.vk.api.sdk.objects.wall.responses.SearchResponse;
import com.vk.api.sdk.queries.board.BoardCreateCommentQuery;
import com.vk.api.sdk.queries.board.BoardGetCommentsQuery;
import com.vk.api.sdk.queries.board.BoardGetTopicsQuery;
import com.vk.api.sdk.queries.fave.FaveGetPagesQuery;
import com.vk.api.sdk.queries.fave.FaveGetTagsQuery;
import com.vk.api.sdk.queries.wall.WallGetQuery;
import com.vk.api.sdk.queries.wall.WallPostQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.StringReader;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@SpringBootApplication
public class PosterApplication implements CommandLineRunner {

	@Autowired
	private Gson gson;

	@Autowired
	private ClientProperties clientProperties;

	private static final Logger log = LoggerFactory.getLogger(PosterApplication.class);

	public static final Integer CAPTCHA_ERROR_CODE = 14;

	public static void main(String[] args) {
		SpringApplication.run(PosterApplication.class, args);
	}

	private <T> void validateValidable(String textResponse, T result) throws ClientException {
		try {
			Validable validable = (Validable) result;
			validable.validateRequired();
		} catch (RequiredFieldException e) {
			throw new ClientException("JSON validate fail: " + textResponse + "\n" + e.toString());
		} catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
			throw new ClientException("JSON validate fail:" + textResponse + e.toString());
		}
	}

	private <T> T execute(String textResponse, Type responseClass) throws ClientException, ApiException {
		log.debug("Response json: {}", textResponse);

		JsonReader jsonReader = new JsonReader(new StringReader(textResponse));
		JsonObject json = (JsonObject) JsonParser.parseReader(jsonReader);

		if (json.has("error")) {
			JsonElement errorElement = json.get("error");
			MyError error;
			try {
				error = gson.fromJson(errorElement, MyError.class);
			} catch (JsonSyntaxException e) {
				log.error("Invalid JSON: " + textResponse, e);
				throw new ClientException("Can't parse json response");
			}

			MyApiException exception = MyApiException.of(ExceptionMapper.parseException(error));
			exception.setError(error);

			log.error("API error", exception);
			throw exception;
		}

		JsonElement response = json;
		if (json.has("response")) {
			response = json.get("response");
		}

		try {
			T result = gson.fromJson(response, responseClass);
			if (result instanceof Validable) {
				validateValidable(textResponse, result);
			}

			return result;
		} catch (JsonSyntaxException e) {
			throw new ClientException("Can't parse json response: " + textResponse + "\n" + e.toString());
		}
	}

	private String getCaptchaCode(String captchaImg) {
		log.warn("Captcha image URL: {}", captchaImg);

		System.out.print("Enter captcha code: ");

		Scanner scanner = new Scanner(System.in);
		return scanner.next();
	}

	private List<Tag> getTags(VkApiClient api, UserActor actor, String captchaSid, String captchaKey) {
		GetTagsResponse tagsResponse;
		try {
			FaveGetTagsQuery query = api.fave()
					.getTags(actor);

			if (nonNull(captchaSid) && nonNull(captchaKey)) {
				query.captchaSid(captchaSid).captchaKey(captchaKey);
			}

			tagsResponse = execute(query.executeAsString(), GetTagsResponse.class);

			log.debug("Tags: {}", tagsResponse);

			return tagsResponse.getItems();
		} catch (MyApiException e) {
			if (e.getCode().equals(CAPTCHA_ERROR_CODE)) {
				getTags(api, actor, e.getError().getCaptchaSid(), getCaptchaCode(e.getError().getCaptchaImg()));
			}

			log.error("Get tags error: {} - {}", e.getCode(), e.getMessage());
		} catch (ApiException | ClientException e) {
			log.error("Get tags error: {}", e.getMessage());
		}

		return Collections.emptyList();
	}

	private List<Integer> getGroupTagIds(List<Tag> tags) {
		return tags.stream()
				.filter(tag -> clientProperties.getTags().contains(tag.getName()))
				.map(Tag::getId)
				.collect(Collectors.toList());
	}

	private List<Page> getTagPages(VkApiClient api, UserActor actor, Integer tagId, String captchaSid, String captchaKey) {
		GetPagesResponse groupsResponse;
		try {
			FaveGetPagesQuery query = api.fave()
					.getPages(actor)
					.count(clientProperties.getTagPagesQuerySize())
					.type(GetPagesType.GROUPS)
					.tagId(tagId)
					.fields(UserGroupFields.ID, UserGroupFields.NAME);

			if (nonNull(captchaSid) && nonNull(captchaKey)) {
				query.captchaSid(captchaSid).captchaKey(captchaKey);
			}

			groupsResponse = execute(query.executeAsString(), GetPagesResponse.class);

			log.debug("Groups: {}", groupsResponse);

			return groupsResponse.getItems();
		} catch (MyApiException e) {
			if (e.getCode().equals(CAPTCHA_ERROR_CODE)) {
				getTagPages(api, actor, tagId, e.getError().getCaptchaSid(), getCaptchaCode(e.getError().getCaptchaImg()));
			}

			log.error("Get pages error: {} - {}", e.getCode(), e.getMessage());
		} catch (ApiException | ClientException e) {
			log.error("Get pages error: {}", e.getMessage());
		}

		return Collections.emptyList();
	}

	private List<Topic> getGroupTopics(VkApiClient api, UserActor actor, Integer groupId, String captchaSid, String captchaKey) {
		GetTopicsResponse topicsResponse;
		try {
			BoardGetTopicsQuery query = api.board().getTopics(actor, groupId)
					.extended(false);

			if (nonNull(captchaSid) && nonNull(captchaKey)) {
				query.captchaSid(captchaSid).captchaKey(captchaKey);
			}

			topicsResponse = execute(query.executeAsString(), GetTopicsResponse.class);

			log.debug("Topics: {}", topicsResponse);

			return topicsResponse.getItems();
		} catch (MyApiException e) {
			if (e.getCode().equals(CAPTCHA_ERROR_CODE)) {
				getGroupTopics(api, actor, groupId, e.getError().getCaptchaSid(), getCaptchaCode(e.getError().getCaptchaImg()));
			}

			log.error("Get topics error: {} - {}", e.getCode(), e.getMessage());
		} catch (ApiException | ClientException e) {
			log.error("Get topics error: {}", e.getMessage());
		}

		return Collections.emptyList();
	}

	private boolean postTopicComment(VkApiClient api, UserActor actor, Integer groupId, Integer topicId, String captchaSid, String captchaKey) {
		try {
			BoardCreateCommentQuery query = api.board().createComment(actor, groupId, topicId)
					.fromGroup(false)
					.guid(clientProperties.getUserId().toString() + groupId.toString() + topicId.toString())
					.message(clientProperties.getPostMessage());

			if (nonNull(captchaSid) && nonNull(captchaKey)) {
				query.captchaSid(captchaSid).captchaKey(captchaKey);
			}

			execute(query.executeAsString(), Integer.class);

			return true;
		} catch (MyApiException e) {
			if (e.getCode().equals(CAPTCHA_ERROR_CODE)) {
				return postTopicComment(api, actor, groupId, topicId, e.getError().getCaptchaSid(), getCaptchaCode(e.getError().getCaptchaImg()));
			}

			log.error("Post topic comment error: {} - {}", e.getCode(), e.getMessage());
		} catch (ApiException | ClientException e) {
			log.error("Post topic comment error: {}", e.getMessage());
		}

		return false;
	}

	private boolean postGroupMessage(VkApiClient api, UserActor actor, Integer groupId, String captchaSid, String captchaKey) {
		try {
			WallPostQuery query = api.wall().post(actor)
					.ownerId(-groupId)
					.fromGroup(false)
					.friendsOnly(false)
					.signed(true)
					.guid(clientProperties.getUserId().toString() + groupId.toString())
					.markAsAds(false)
					.message(clientProperties.getPostMessage());

			if (nonNull(captchaSid) && nonNull(captchaKey)) {
				query.captchaSid(captchaSid).captchaKey(captchaKey);
			}

			execute(query.executeAsString(), PostResponse.class);

			return true;
		} catch (MyApiException e) {
			if (e.getCode().equals(CAPTCHA_ERROR_CODE)) {
				return postGroupMessage(api, actor, groupId, e.getError().getCaptchaSid(), getCaptchaCode(e.getError().getCaptchaImg()));
			}

			log.error("Post group message error: {} - {}", e.getCode(), e.getMessage());
		} catch (ApiException | ClientException e) {
			log.error("Post group message error: {}", e.getMessage());
		}

		return false;
	}

	private Integer getTotalTopicComments(VkApiClient api, UserActor actor, Integer groupId, Integer topicId, String captchaSid, String captchaKey) {
		try {
			BoardGetCommentsQuery query = api
					.board()
					.getComments(actor, groupId, topicId)
					.count(1);

			if (nonNull(captchaSid) && nonNull(captchaKey)) {
				query.captchaSid(captchaSid).captchaKey(captchaKey);
			}

			GetCommentsResponse countResponse = execute(query.executeAsString(), GetCommentsResponse.class);
			return countResponse.getCount();
		} catch (MyApiException e) {
			if (e.getCode().equals(CAPTCHA_ERROR_CODE)) {
				return getTotalTopicComments(api, actor, groupId, topicId, e.getError().getCaptchaSid(), getCaptchaCode(e.getError().getCaptchaImg()));
			}

			log.error("Get total topic comments error: {} - {}", e.getCode(), e.getMessage());
		} catch (ApiException | ClientException e) {
			log.error("Get total topic comments error: {}", e.getMessage());
		}

		return 0;
	}

	private TopicComment getLatestTopicComment(VkApiClient api, UserActor actor, Integer groupId, Integer topicId, String captchaSid, String captchaKey) {
		try {
			Integer count = getTotalTopicComments(api, actor, groupId, topicId, null, null);
			BoardGetCommentsQuery query = api
					.board()
					.getComments(actor, groupId, topicId)
					.offset(Math.floorDiv(count > 0 ? count - 1 : 0, clientProperties.getGroupTopicQuerySize()) * clientProperties.getGroupTopicQuerySize())
					.count(clientProperties.getGroupTopicQuerySize() * 2);

			if (nonNull(captchaSid) && nonNull(captchaKey)) {
				query.captchaSid(captchaSid).captchaKey(captchaKey);
			}

			GetCommentsResponse response = execute(query.executeAsString(), GetCommentsResponse.class);

			List<TopicComment> wallPosts = response.getItems();
			return wallPosts.stream()
					.filter(i -> i.getText().contains(clientProperties.getPostMessageQuery()))
					.findAny()
					.orElse(null);
		} catch (MyApiException e) {
			if (e.getCode().equals(CAPTCHA_ERROR_CODE)) {
				return getLatestTopicComment(api, actor, groupId, topicId, e.getError().getCaptchaSid(), getCaptchaCode(e.getError().getCaptchaImg()));
			}

			log.error("Get latest topic comment error: {} - {}", e.getCode(), e.getMessage());
		} catch (ApiException | ClientException e) {
			log.error("Get latest topic comment error: {}", e.getMessage());
		}

		return null;
	}

	private void sendTopicComments(VkApiClient api, UserActor actor, Integer groupId, String groupName) throws InterruptedException {
		if (Boolean.FALSE.equals(clientProperties.getPostToGroupsTopics())) return;

		Set<Integer> excludedTopics = clientProperties.getExcludedGroupsTopics().get(groupId);
		for (Topic topic : getGroupTopics(api, actor, groupId, null, null)) {
			Integer topicId = topic.getId();
			String topicTitle = topic.getTitle();

			if (isNull(excludedTopics) || !excludedTopics.contains(topicId)) {

				log.info("Selected group: {} - {} topic: {} - {}", groupId, groupName, topicId, topicTitle);

				TopicComment latestComment = getLatestTopicComment(api, actor, groupId, topicId, null, null);
				if (isNull(latestComment)) {
					if (postTopicComment(api, actor, groupId, topicId, null, null)) {
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

	private WallpostFull getLatestSuggestedGroupPost(VkApiClient api, UserActor actor, Integer groupId, String captchaSid, String captchaKey) {
		try {
			WallGetQuery query = api
					.wall()
					.get(actor)
					.filter(GetFilter.SUGGESTS)
					.count(clientProperties.getGroupPostQuerySize())
					.ownerId(-groupId);

			if (nonNull(captchaSid) && nonNull(captchaKey)) {
				query.captchaSid(captchaSid).captchaKey(captchaKey);
			}

			SearchResponse response = execute(query.executeAsString(), SearchResponse.class);
			List<WallpostFull> wallPosts = response.getItems();
			return wallPosts.stream()
					.filter(i -> i.getText().contains(clientProperties.getPostMessageQuery()))
					.findAny()
					.orElse(null);
		} catch (MyApiException e) {
			if (e.getCode().equals(CAPTCHA_ERROR_CODE)) {
				return getLatestSuggestedGroupPost(api, actor, groupId, e.getError().getCaptchaSid(), getCaptchaCode(e.getError().getCaptchaImg()));
			}

			log.error("Get latest suggested post error: {} - {}", e.getCode(), e.getMessage());
		} catch (ApiException | ClientException e) {
			log.error("Get latest suggested post error: {}", e.getMessage());
		}

		return null;
	}

	private WallpostFull getLatestGroupPost(VkApiClient api, UserActor actor, Integer groupId, String captchaSid, String captchaKey) {
		try {
			WallGetQuery query = api
					.wall()
					.get(actor)
					.filter(GetFilter.ALL)
					.count(clientProperties.getGroupPostQuerySize())
					.ownerId(-groupId);

			if (nonNull(captchaSid) && nonNull(captchaKey)) {
				query.captchaSid(captchaSid).captchaKey(captchaKey);
			}

			SearchResponse response = execute(query.executeAsString(), SearchResponse.class);
			List<WallpostFull> wallPosts = response.getItems();
			return wallPosts.stream()
					.filter(i -> i.getText().contains(clientProperties.getPostMessageQuery()))
					.findAny()
					.orElse(null);
		} catch (MyApiException e) {
			if (e.getCode().equals(CAPTCHA_ERROR_CODE)) {
				return getLatestGroupPost(api, actor, groupId, e.getError().getCaptchaSid(), getCaptchaCode(e.getError().getCaptchaImg()));
			}

			log.error("Get latest group post error: {} - {}", e.getCode(), e.getMessage());
		} catch (ApiException | ClientException e) {
			log.error("Get latest group post error: {}", e.getMessage());
		}

		return null;
	}

	private void sendGroupMessage(VkApiClient api, UserActor actor, Integer groupId, String groupName) throws InterruptedException {
		if (Boolean.FALSE.equals(clientProperties.getPostToGroups())) return;

		if (!clientProperties.getExcludedGroups().contains(groupId)) {
			WallpostFull latestPost = getLatestSuggestedGroupPost(api, actor, groupId, null, null);
			if (isNull(latestPost)) {
				latestPost = getLatestGroupPost(api, actor, groupId, null, null);
			}
			if (isNull(latestPost)) {
				if (postGroupMessage(api, actor, groupId, null, null)) {
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
		TransportClient transportClient = new HttpTransportClient();
		VkApiClient api = new VkApiClient(transportClient);

		UserActor actor = new UserActor(clientProperties.getUserId(), clientProperties.getAccessToken());

		List<Tag> tags = getTags(api, actor, null, null);
		List<Integer> tagIds = getGroupTagIds(tags);

		for (Integer tagId : tagIds) {

			log.info("Selected tag: {}", tagId);

			for (Page page : getTagPages(api, actor, tagId, null, null)) {
				Integer groupId = page.getGroup().getId();
				String groupName = page.getGroup().getName();

				log.info("Selected group: {} - {}", groupId, groupName);

				sendTopicComments(api, actor, groupId, groupName);
				sendGroupMessage(api, actor, groupId, groupName);
			}
		}
	}

}
