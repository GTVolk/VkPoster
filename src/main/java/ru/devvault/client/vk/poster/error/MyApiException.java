package ru.devvault.client.vk.poster.error;

import com.vk.api.sdk.exceptions.ApiException;

public class MyApiException extends ApiException {
    private final transient MyError error;

    public MyApiException(Integer code, String description, String message, MyError error) {
        super(code, description, message);
        this.error = error;
    }

    public static MyApiException of(ApiException source, MyError error) {
        return new MyApiException(source.getCode(), source.getDescription(), source.getMessage(), error);
    }

    public static MyApiException of(ApiException source) {
        return of(source, null);
    }

    public MyError getError() {
        return error;
    }
}
