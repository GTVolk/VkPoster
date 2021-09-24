package ru.devvault.client.vk.poster;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.vk.api.sdk.client.AbstractQueryBuilder;
import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.exceptions.*;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.UserAuthResponse;
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
import com.vk.api.sdk.objects.groups.GroupFull;
import com.vk.api.sdk.objects.wall.GetFilter;
import com.vk.api.sdk.objects.wall.WallpostFull;
import com.vk.api.sdk.objects.wall.responses.GetResponse;
import com.vk.api.sdk.objects.wall.responses.PostResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@Slf4j
@Service
@RequiredArgsConstructor
public class VkService {

    public static final Integer CAPTCHA_ERROR_CODE = 14;

    private final Gson gson;
    private final VkApiClient apiClient;
    private final Scanner scanner;

    private UserActor userActor;
    private Integer tagPagesQuerySize;
    private Integer topicCommentsQuerySize;
    private Integer groupWallPostsQuerySize;

    public void setTagPagesQuerySize(Integer tagPagesQuerySize) {
        this.tagPagesQuerySize = tagPagesQuerySize;
    }

    public Integer getTagPagesQuerySize() {
        return tagPagesQuerySize;
    }

    public void setTopicCommentsQuerySize(Integer topicCommentsQuerySize) {
        this.topicCommentsQuerySize = topicCommentsQuerySize;
    }

    public Integer getTopicCommentsQuerySize() {
        return topicCommentsQuerySize;
    }

    public void setGroupWallPostsQuerySize(Integer groupWallPostsQuerySize) {
        this.groupWallPostsQuerySize = groupWallPostsQuerySize;
    }

    public Integer getGroupWallPostsQuerySize() {
        return groupWallPostsQuerySize;
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

            log.debug("{} response: {}", responseClass, result);

            return result;
        } catch (JsonSyntaxException e) {
            throw new ClientException("Can't parse json response: " + textResponse + "\n" + e.toString());
        }
    }

    private String readInput(String prompt) {
        System.out.print(String.format("%s: ", prompt));
        return scanner.next();
    }

    private <T, R> AbstractQueryBuilder<T, R> addCaptcha(AbstractQueryBuilder<T, R> query, MyError e) {
        query.captchaSid(e.getCaptchaSid());

        log.warn("Captcha image URL: {}", e.getCaptchaImg());
        query.captchaKey(readInput("Enter captcha code"));

        return query;
    }

    private <T, R> R getQueryData(AbstractQueryBuilder<T, R> query, Class<R> responseType) throws ApiException, ClientException {
        try {
            return execute(query.executeAsString(), responseType);
        } catch (MyApiException e) {
            if (e.getCode().equals(CAPTCHA_ERROR_CODE)) {
                return getQueryData(addCaptcha(query, e.getError()), responseType);
            }

            throw e;
        }
    }

    public Boolean authorize(Integer appId, String clientSecret, String redirectUri, String code) {
        try {
            if (isNull(appId)) appId = Integer.valueOf(readInput("Enter application ID"));
            if (isNull(clientSecret)) clientSecret = readInput("Enter application client secret");

            if (isNull(code)) {
                log.warn("Code request URL: https://oauth.vk.com/authorize?client_id={}&redirect_uri={}&display=page&scope=268431359&response_type=code&state=123456", appId, redirectUri);
                code = readInput("Enter authorization code");
            }

            UserAuthResponse authResponse = getQueryData(
                    apiClient
                            .oAuth()
                            .userAuthorizationCodeFlow(appId, clientSecret, redirectUri, code),
                    UserAuthResponse.class
            );

            userActor = new UserActor(authResponse.getUserId(), authResponse.getAccessToken());

            return true;
        } catch (OAuthException e) {
            log.error("OAuth error: {}", e.getRedirectUri());
        } catch (ApiException | ClientException e) {
            log.error("Authorization error: {}", e.getMessage());
        }

        return false;
    }

    public Boolean authorize(Integer appId, String redirectUri, Integer userId, String accessToken) {
        try {
            if (isNull(userId) || isNull(accessToken)) {
                if (isNull(appId)) appId = Integer.valueOf(readInput("Enter application ID"));

                log.warn("Token request URL: https://oauth.vk.com/authorize?client_id={}&redirect_uri={}&display=page&scope=268431359&response_type=code&state=123456", appId, redirectUri);

                if (isNull(accessToken)) accessToken = readInput("Enter application access token");
                if (isNull(userId)) userId = Integer.valueOf(readInput("Enter application user ID"));
            }

            userActor = new UserActor(userId, accessToken);
        } catch (Exception e) {
            log.error("Authorization exception: {}", e.getMessage());

            return false;
        }

        return true;
    }

    public List<Tag> getTags() {
        try {
            return getQueryData(
                    apiClient
                            .fave()
                            .getTags(userActor),
                    GetTagsResponse.class
            ).getItems();
        } catch (ApiException | ClientException e) {
            log.error("Get tags error: {}", e.getMessage());
        }

        return Collections.emptyList();
    }

    public List<Page> getTagPages(Tag tag) {
        try {
            return getQueryData(
                    apiClient
                            .fave()
                            .getPages(userActor)
                            .count(getTagPagesQuerySize())
                            .type(GetPagesType.GROUPS)
                            .tagId(tag.getId())
                            .fields(UserGroupFields.ID, UserGroupFields.NAME),
                    GetPagesResponse.class
            ).getItems();
        } catch (ApiException | ClientException e) {
            log.error("Get pages error: {}", e.getMessage());
        }

        return Collections.emptyList();
    }

    public List<Topic> getGroupTopics(GroupFull group) {
        try {
            return getQueryData(
                    apiClient
                            .board()
                            .getTopics(userActor, group.getId())
                            .extended(false),
                    GetTopicsResponse.class
            ).getItems();
        } catch (ApiException | ClientException e) {
            log.error("Get topics error: {}", e.getMessage());
        }

        return Collections.emptyList();
    }

    public List<TopicComment> getTopicComments(GroupFull group, Topic topic) {
        try {
            Integer count = getQueryData(
                    apiClient
                            .board()
                            .getComments(userActor, group.getId(), topic.getId())
                            .count(1),
                    GetCommentsResponse.class
            ).getCount();

            return getQueryData(
                    apiClient
                            .board()
                            .getComments(userActor, group.getId(), topic.getId())
                            .offset(Math.floorDiv(count > 0 ? count - 1 : 0, getTopicCommentsQuerySize()) * getTopicCommentsQuerySize())
                            .count(getTopicCommentsQuerySize() * 2),
                    GetCommentsResponse.class
            ).getItems();
        } catch (ApiException | ClientException e) {
            log.error("Get latest topic comment error: {}", e.getMessage());
        }

        return Collections.emptyList();
    }

    public Integer createTopicComment(GroupFull group, Topic topic, String message) {
        try {
            return getQueryData(
                    apiClient
                            .board()
                            .createComment(userActor, group.getId(), topic.getId())
                            .fromGroup(false)
                            .guid(String.format("%d%d%d", userActor.getId(), group.getId(), topic.getId()))
                            .message(message),
                    Integer.class
            );
        } catch (ApiException | ClientException e) {
            log.error("Post topic comment error: {}", e.getMessage());
        }

        return 0;
    }

    public List<WallpostFull> getGroupWallPosts(GroupFull group, GetFilter getFilter) {
        try {
            return getQueryData(
                    apiClient
                            .wall()
                            .get(userActor)
                            .filter(getFilter)
                            .count(getGroupWallPostsQuerySize())
                            .ownerId(-group.getId()),
                    GetResponse.class
            ).getItems();
        } catch (ApiException | ClientException e) {
            log.error("Get wall post error: {}", e.getMessage());
        }

        return Collections.emptyList();
    }

    public PostResponse createWallPost(GroupFull group, String message) {
        try {
            return getQueryData(
                    apiClient
                            .wall()
                            .post(userActor)
                            .ownerId(-group.getId())
                            .fromGroup(false)
                            .friendsOnly(false)
                            .signed(true)
                            .guid(String.format("%d%d", userActor.getId(), group.getId()))
                            .markAsAds(false)
                            .message(message),
                    PostResponse.class
            );
        } catch (ApiException | ClientException e) {
            log.error("Group {} post message error: {}", group, e.getMessage());
        }

        return new PostResponse();
    }
}
