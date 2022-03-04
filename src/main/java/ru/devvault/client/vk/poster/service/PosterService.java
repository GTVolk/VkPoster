package ru.devvault.client.vk.poster.service;

import com.vk.api.sdk.objects.board.Topic;
import com.vk.api.sdk.objects.board.TopicComment;
import com.vk.api.sdk.objects.fave.Tag;
import com.vk.api.sdk.objects.groups.GroupFull;
import com.vk.api.sdk.objects.wall.GetFilter;
import com.vk.api.sdk.objects.wall.WallpostFull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.devvault.client.vk.poster.configuration.ClientProperties;

import java.util.List;
import java.util.stream.Collectors;

import static java.lang.Boolean.FALSE;
import static java.util.Objects.isNull;

@Slf4j
@Service
@RequiredArgsConstructor
public class PosterService {

    private final ClientProperties clientProperties;
    private final VkService vkService;

    public Boolean authorizeClient() {
        var authResult = FALSE;
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

    private TopicComment queryComment(GroupFull group, Topic topic) {
        return vkService.getTopicComments(group, topic).stream()
                .filter(i -> i.getText().contains(clientProperties.getPostMessageQuery()))
                .findAny()
                .orElse(null);
    }

    private void sendTopicComments(GroupFull group) throws InterruptedException {
        if (FALSE.equals(clientProperties.getPostToGroupsTopics())) return;

        var excludedTopics = clientProperties.getExcludedGroupsTopics().get(group.getId());
        for (var topic : vkService.getGroupTopics(group)) {
            if (isNull(excludedTopics) || !excludedTopics.contains(topic.getId())) {

                log.info("Selected topic: {}", topic);

                var comment = queryComment(group, topic);

                if (isNull(comment)) {
                    if (vkService.createTopicComment(group, topic, clientProperties.getPostMessage()) > 0) {
                        log.info("Topic comment posted! Group: {}, topic: {}", group, topic);
                    } else {
                        log.error("Topic comment is not created! Group: {}, topic: {}", group, topic);
                    }
                } else {
                    log.info("Topic comment already exists! Group: {}, topic: {}, comment: {}", group, topic, comment);
                }

                Thread.sleep(clientProperties.getQueryInterval() * 3L);
            } else {
                log.info("Processing topic excluded! Group: {}, topic: {}", group, topic);
            }

            Thread.sleep(clientProperties.getQueryInterval());
        }
    }

    private WallpostFull queryWallPost(GroupFull group, GetFilter filter) {
        return vkService.getGroupWallPosts(group, filter).stream()
                .filter(i -> i.getText().contains(clientProperties.getPostMessageQuery()))
                .findAny()
                .orElse(null);
    }

    private void sendGroupMessage(GroupFull group) throws InterruptedException {
        if (FALSE.equals(clientProperties.getPostToGroups())) return;

        if (!clientProperties.getExcludedGroups().contains(group.getId())) {
            var post = queryWallPost(group, GetFilter.SUGGESTS);

            if (isNull(post)) {
                post = queryWallPost(group, GetFilter.ALL);
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

            Thread.sleep(clientProperties.getQueryInterval() * 3L);
        } else {
            log.info("Processing group excluded! Group: {}", group);
        }

        Thread.sleep(clientProperties.getQueryInterval());
    }

    private List<Tag> queryTags() {
        return vkService.getTags().stream()
                .filter(tag -> clientProperties.getTags().contains(tag.getName()))
                .collect(Collectors.toList());
    }

    public Integer process() throws InterruptedException {
        if (FALSE.equals(authorizeClient())) {
            log.error("Failed to authorize API requests. Check your settings!");
            return 1;
        }

        vkService.setTagPagesQuerySize(clientProperties.getTagPagesQuerySize());
        vkService.setTopicCommentsQuerySize(clientProperties.getGroupTopicQuerySize());
        vkService.setGroupWallPostsQuerySize(clientProperties.getGroupPostQuerySize());

        for (var tag : queryTags()) {

            log.info("Selected tag: {}", tag);

            for (var page : vkService.getTagPages(tag)) {
                var group = page.getGroup();

                log.info("Selected group: {}", group);

                sendTopicComments(group);
                sendGroupMessage(group);
            }
        }

        return 0;
    }
}
