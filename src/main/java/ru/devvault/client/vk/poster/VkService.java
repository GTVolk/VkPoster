package ru.devvault.client.vk.poster;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.vk.api.sdk.client.AbstractQueryBuilder;
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
import java.util.stream.Collectors;

import static java.util.Objects.nonNull;

@Slf4j
@Service
@RequiredArgsConstructor
public class VkService {

    public static final Integer CAPTCHA_ERROR_CODE = 14;

    private final Gson gson;
    private final ClientProperties clientProperties;

    private VkApiClient apiClient;
    private UserActor userActor;

    private String captchaSid;
    private String captchaKey;

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

    private void fillCaptchaSidAndKey(MyError e) {
        captchaSid = e.getCaptchaSid();
        log.warn("Captcha image URL: {}", e.getCaptchaImg());

        System.out.print("Enter captcha code: ");

        Scanner scanner = new Scanner(System.in);
        captchaKey = scanner.next();
    }

    private <T, R> void addCaptchaDataToQuery(AbstractQueryBuilder<T, R> queryBuilder) {
        if (nonNull(captchaSid) && nonNull(captchaKey)) {
            queryBuilder.captchaSid(captchaSid);
            queryBuilder.captchaKey(captchaKey);

            captchaSid = null;
            captchaKey = null;
        }
    }

    private <T, R> R getQueryData(AbstractQueryBuilder<T, R> query, Class<R> responseType) throws ApiException, ClientException {
        try {
            addCaptchaDataToQuery(query);
            return execute(query.executeAsString(), responseType);
        } catch (MyApiException e) {
            if (e.getCode().equals(CAPTCHA_ERROR_CODE)) {
                fillCaptchaSidAndKey(e.getError());
                return getQueryData(query, responseType);
            }

            throw e;
        }
    }

    public Boolean authorize() {
        try {
            TransportClient transportClient = new HttpTransportClient();
            apiClient = new VkApiClient(transportClient);
            userActor = new UserActor(clientProperties.getUserId(), clientProperties.getAccessToken());
        } catch (Exception e) {
            log.error("Authorization exception: {}", e.getMessage());

            return false;
        }

        return true;
    }

    public List<Integer> getTags() {
        try {
            return getQueryData(
                    apiClient
                            .fave()
                            .getTags(userActor),
                    GetTagsResponse.class
            ).getItems().stream()
                    .filter(tag -> clientProperties.getTags().contains(tag.getName()))
                    .map(Tag::getId)
                    .collect(Collectors.toList());
        } catch (ApiException | ClientException e) {
            log.error("Get tags error: {}", e.getMessage());
        }

        return Collections.emptyList();
    }

    public List<Page> getTagPages(Integer tagId) {
        try {
            return getQueryData(
                    apiClient
                            .fave()
                            .getPages(userActor)
                            .count(clientProperties.getTagPagesQuerySize())
                            .type(GetPagesType.GROUPS)
                            .tagId(tagId)
                            .fields(UserGroupFields.ID, UserGroupFields.NAME),
                    GetPagesResponse.class
            ).getItems();
        } catch (ApiException | ClientException e) {
            log.error("Get pages error: {}", e.getMessage());
        }

        return Collections.emptyList();
    }

    public List<Topic> getGroupTopics(Integer groupId) {
        try {
            return getQueryData(
                    apiClient
                            .board()
                            .getTopics(userActor, groupId)
                            .extended(false),
                    GetTopicsResponse.class
            ).getItems();
        } catch (ApiException | ClientException e) {
            log.error("Get topics error: {}", e.getMessage());
        }

        return Collections.emptyList();
    }

    public TopicComment getTopicComment(Integer groupId, Integer topicId) {
        try {
            Integer count = getQueryData(
                    apiClient
                            .board()
                            .getComments(userActor, groupId, topicId)
                            .count(1),
                    GetCommentsResponse.class
            ).getCount();

            return getQueryData(
                    apiClient
                            .board()
                            .getComments(userActor, groupId, topicId)
                            .offset(Math.floorDiv(count > 0 ? count - 1 : 0, clientProperties.getGroupTopicQuerySize()) * clientProperties.getGroupTopicQuerySize())
                            .count(clientProperties.getGroupTopicQuerySize() * 2),
                    GetCommentsResponse.class
            ).getItems().stream()
                    .filter(i -> i.getText().contains(clientProperties.getPostMessageQuery()))
                    .findAny()
                    .orElse(null);
        } catch (ApiException | ClientException e) {
            log.error("Get latest topic comment error: {}", e.getMessage());
        }

        return null;
    }

    public boolean createTopicComment(Integer groupId, Integer topicId) {
        try {
            return getQueryData(
                    apiClient
                            .board()
                            .createComment(userActor, groupId, topicId)
                            .fromGroup(false)
                            .guid(clientProperties.getUserId().toString() + groupId.toString() + topicId.toString())
                            .message(clientProperties.getPostMessage()),
                    Integer.class
            ) > 0;
        } catch (ApiException | ClientException e) {
            log.error("Post topic comment error: {}", e.getMessage());
        }

        return false;
    }

    public WallpostFull getGroupPost(Integer groupId, GetFilter getFilter) {
        try {
            return getQueryData(
                    apiClient
                            .wall()
                            .get(userActor)
                            .filter(getFilter)
                            .count(clientProperties.getGroupPostQuerySize())
                            .ownerId(-groupId),
                    GetResponse.class
            ).getItems().stream()
                    .filter(i -> i.getText().contains(clientProperties.getPostMessageQuery()))
                    .findAny()
                    .orElse(null);
        } catch (ApiException | ClientException e) {
            log.error("Get wall post error: {}", e.getMessage());
        }

        return null;
    }

    public boolean postWallMessage(Integer groupId) {
        try {
            return getQueryData(
                    apiClient
                            .wall()
                            .post(userActor)
                            .ownerId(-groupId)
                            .fromGroup(false)
                            .friendsOnly(false)
                            .signed(true)
                            .guid(clientProperties.getUserId().toString() + groupId.toString())
                            .markAsAds(false)
                            .message(clientProperties.getPostMessage()),
                    PostResponse.class
            ).getPostId() > 0;
        } catch (ApiException | ClientException e) {
            log.error("Post group message error: {}", e.getMessage());
        }

        return false;
    }
}
