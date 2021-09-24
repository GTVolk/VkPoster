package ru.devvault.client.vk.poster;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum AuthType {
    CODE_FLOW("CODE"),
    TOKEN("TOKEN");

    private final String type;
}
