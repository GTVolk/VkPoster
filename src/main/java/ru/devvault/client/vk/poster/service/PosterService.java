package ru.devvault.client.vk.poster.service;

import com.vk.api.sdk.objects.board.Topic;
import com.vk.api.sdk.objects.board.TopicComment;
import com.vk.api.sdk.objects.fave.Page;
import com.vk.api.sdk.objects.fave.Tag;
import com.vk.api.sdk.objects.groups.GroupFull;
import com.vk.api.sdk.objects.wall.GetFilter;
import com.vk.api.sdk.objects.wall.WallpostFull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.devvault.client.vk.poster.configuration.ClientProperties;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

@Slf4j
@Service
@RequiredArgsConstructor
public class PosterService {

    private final ClientProperties clientProperties;
    private final VkService vkService;

    public Boolean authorizeClient() {
        Boolean authResult = Boolean.FALSE;
        switch (clientProperties.getAuthType()) {
            case CODE_FLOW:
                log.info("Trying to authorize by code flow");
                authResult = vkService.authorize(
                        clientProperties.getAppId(),
                        clientProperties.getSecretKey(),
                        clientProperties.getRedirectUri(),
                        clientProperties.getAuthorizationCode()
                );
                break;
            case TOKEN:
                log.info("Trying to authorize by access token");
                authResult = vkService.authorize(
                        clientProperties.getAppId(),
                        clientProperties.getRedirectUri(),
                        clientProperties.getUserId(),
                        clientProperties.getAccessToken()
                );
                break;
            default:
                log.error("Unknown auth type");
        }

        return authResult;
    }

    private void sendTopicComments(GroupFull group) throws InterruptedException {
        if (Boolean.FALSE.equals(clientProperties.getPostToGroupsTopics())) return;

        Set<Integer> excludedTopics = clientProperties.getExcludedGroupsTopics().get(group.getId());
        for (Topic topic : vkService.getGroupTopics(group)) {
            if (isNull(excludedTopics) || !excludedTopics.contains(topic.getId())) {

                log.info("Selected topic: {}", topic);

                TopicComment comment = vkService.getTopicComments(group, topic).stream()
                        .filter(i -> i.getText().contains(clientProperties.getPostMessageQuery()))
                        .findAny()
                        .orElse(null);
                if (isNull(comment)) {
                    if (vkService.createTopicComment(group, topic, clientProperties.getPostMessage()) > 0) {
                        log.info("Topic comment posted! Group: {}, topic: {}", group, topic);
                    } else {
                        log.error("Topic comment is not created! Group: {}, topic: {}", group, topic);
                    }
                } else {
                    log.info("Topic comment already exists! Group: {}, topic: {}, comment: {}", group, topic, comment);
                }

                Thread.sleep(clientProperties.getQueryInterval() * 3);
            } else {
                log.info("Processing topic excluded! Group: {}, topic: {}", group, topic);
            }

            Thread.sleep(clientProperties.getQueryInterval());
        }
    }

    private void sendGroupMessage(GroupFull group) throws InterruptedException {
        if (Boolean.FALSE.equals(clientProperties.getPostToGroups())) return;

        if (!clientProperties.getExcludedGroups().contains(group.getId())) {
            WallpostFull post = vkService.getGroupWallPosts(group, GetFilter.SUGGESTS).stream()
                    .filter(i -> i.getText().contains(clientProperties.getPostMessageQuery()))
                    .findAny()
                    .orElse(null);
            if (isNull(post)) {
                post = vkService.getGroupWallPosts(group, GetFilter.ALL).stream()
                        .filter(i -> i.getText().contains(clientProperties.getPostMessageQuery()))
                        .findAny()
                        .orElse(null);
            }
            if (isNull(post)) {
                if (vkService.createWallPost(group, clientProperties.getPostMessage()).getPostId() > 0) {
                    log.info("Group message posted! Group: {}", group);
                } else {
                    log.error("Post is not created! Group: {}", group);
                }
            } else {
                log.info("Post already exists! Group: {}, post: {}", group, post);
            }

            Thread.sleep(clientProperties.getQueryInterval() * 3);
        } else {
            log.info("Processing group excluded! Group: {}", group);
        }

        Thread.sleep(clientProperties.getQueryInterval());
    }

    public Integer process() throws InterruptedException {
        if (Boolean.FALSE.equals(authorizeClient())) {
            log.error("Failed to authorize API requests. Check your settings!");
            return 1;
        }

        vkService.setTagPagesQuerySize(clientProperties.getTagPagesQuerySize());
        vkService.setTopicCommentsQuerySize(clientProperties.getGroupTopicQuerySize());
        vkService.setGroupWallPostsQuerySize(clientProperties.getGroupPostQuerySize());

        List<Tag> tags = vkService.getTags().stream()
                .filter(tag -> clientProperties.getTags().contains(tag.getName()))
                .collect(Collectors.toList());

        for (Tag tag : tags) {

            log.info("Selected tag: {}", tag);

            for (Page page : vkService.getTagPages(tag)) {
                GroupFull group = page.getGroup();

                log.info("Selected group: {}", group);

                sendTopicComments(group);
                sendGroupMessage(group);
            }
        }

        return 0;
    }
}
