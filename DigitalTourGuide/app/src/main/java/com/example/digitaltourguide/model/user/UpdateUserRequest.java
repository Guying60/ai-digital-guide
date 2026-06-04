package com.example.digitaltourguide.model.user;

public class UpdateUserRequest {
    private String nickname;
    private String userSetting;
    private Integer gender;
    private Integer age;
    private String avatarUrl;

    public UpdateUserRequest(String nickname, String userSetting, Integer gender, Integer age,String avatarUrl) {
        this.nickname = nickname;
        this.userSetting = userSetting;
        this.gender = gender;
        this.age = age;
        this.avatarUrl=avatarUrl;
    }

    public UpdateUserRequest() {}

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getUserSetting() {
        return userSetting;
    }

    public void setUserSetting(String userSetting) {
        this.userSetting = userSetting;
    }

    public Integer getGender() {
        return gender;
    }

    public void setGender(Integer gender) {
        this.gender = gender;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }
}
