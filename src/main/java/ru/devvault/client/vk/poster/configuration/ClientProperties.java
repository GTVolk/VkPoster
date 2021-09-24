package ru.devvault.client.vk.poster.configuration;


import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;
import ru.devvault.client.vk.poster.enums.AuthType;

import javax.validation.constraints.*;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Configuration
@Validated
@ConfigurationProperties(prefix = "ru.devvault.client.vk.poster")
@Getter
@Setter
@ToString
public class ClientProperties {

    @NotNull
    private AuthType authType = AuthType.TOKEN;

    private Integer appId;

    private String secretKey = "";

    @NotBlank
    private String redirectUri = "https://oauth.vk.com/blank.html";

    private String authorizationCode = "";

    private Integer userId;

    private String accessToken = "";

    @NotBlank
    private String postMessage = "";

    @NotBlank
    private String postMessageQuery = "";

    @NotNull
    @Size(min = 1)
    private Set<String> tags = Collections.emptySet();

    @NotNull
    private Boolean postToGroups = Boolean.TRUE;

    @NotNull
    private Boolean postToGroupsTopics = Boolean.FALSE;

    @NotNull
    @Min(1)
    @Max(100)
    private Integer tagPagesQuerySize = 100;

    @NotNull
    @Min(1)
    @Max(100)
    private Integer groupPostQuerySize = 20;

    @NotNull
    @Min(1)
    @Max(100)
    private Integer groupTopicQuerySize = 10;

    @NotNull
    @Min(100)
    private Integer queryInterval = 1000;

    @NotNull
    private Set<Integer> excludedGroups = Collections.emptySet();

    @NotNull
    private Map<Integer, Set<Integer>> excludedGroupsTopics = Collections.emptyMap();
}
