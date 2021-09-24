package ru.devvault.client.vk.poster;

import com.vk.api.sdk.exceptions.ApiException;

public class MyApiException extends ApiException {

    private MyError error;

    public MyApiException(Integer code, String description, String message, MyError error) {
        super(code, description, message);

        this.error = error;
    }

    public MyApiException(Integer code, String description, String message) {
        super(code, description, message);

        this.error = null;
    }

    public MyApiException(Integer code, String message) {
        super(code, message);

        this.error = null;
    }

    public MyApiException(Integer code, String message, MyError error) {
        super(code, message);

        this.error = error;
    }

    public MyApiException(String message) {
        super(message);

        this.error = null;
    }

    public MyApiException(String message, MyError error) {
        super(message);

        this.error = error;
    }

    public static MyApiException of(ApiException source) {
        return new MyApiException(source.getCode(), source.getDescription(), source.getMessage());
    }

    public void setError(MyError error) {
        this.error = error;
    }

    public MyError getError() {
        return error;
    }
}
