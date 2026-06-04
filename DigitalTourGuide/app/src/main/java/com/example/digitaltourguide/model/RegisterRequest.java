package com.example.digitaltourguide.model;

public class RegisterRequest {
    private String username;
    private String password;
    private String confirmPassword;
    private String nickname;
    public RegisterRequest(String username, String password,String confirmPassword, String nickname) {
        this.username = username;
        this.password = password;
        this.confirmPassword = confirmPassword;
        this.nickname = nickname;
    }

    // Getter
    public String getUsername() { return username; }
    public String getPassword() { return password; }

    public String getConfirmPassword() {return confirmPassword;}

    public String getNickname() { return nickname; }
}
