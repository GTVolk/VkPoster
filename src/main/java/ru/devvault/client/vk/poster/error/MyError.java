package ru.devvault.client.vk.poster.error;

import com.google.gson.annotations.SerializedName;
import com.vk.api.sdk.objects.base.Error;

import java.util.Objects;

public class MyError extends Error {
    @SerializedName("captcha_sid")
    private String captchaSid;

    @SerializedName("captcha_img")
    private String captchaImg;

    public void setCaptchaSid(String captchaSid) {
        this.captchaSid = captchaSid;
    }

    public void setCaptchaImg(String captchaImg) {
        this.captchaImg = captchaImg;
    }

    public String getCaptchaSid() {
        return captchaSid;
    }

    public String getCaptchaImg() {
        return captchaImg;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        var error = (MyError) o;
        return Objects.equals(((MyError) o).getErrorText(), error.getErrorText()) &&
                Objects.equals(((MyError) o).getErrorMsg(), error.getErrorMsg()) &&
                Objects.equals(((MyError) o).getRequestParams(), error.getRequestParams()) &&
                Objects.equals(((MyError) o).getErrorCode(), error.getErrorCode()) &&
                Objects.equals(((MyError) o).getErrorSubcode(), error.getErrorSubcode()) &&
                Objects.equals(((MyError) o).getCaptchaSid(), error.getCaptchaSid()) &&
                Objects.equals(((MyError) o).getCaptchaImg(), error.getCaptchaImg());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getErrorText(),
                getErrorSubcode(),
                getRequestParams(),
                getErrorCode(),
                getErrorMsg(),
                getCaptchaSid(),
                getCaptchaImg()
        );
    }
}
