package com.xinyu.esportsticketing.dto;

import com.xinyu.esportsticketing.entity.User;

public class LoginResponse {

    private Integer id;
    private String username;
    private String email;

    public LoginResponse(Integer id, String username, String email) {
        this.id = id;
        this.username = username;
        this.email = email;
    }

    public Integer getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public static LoginResponse fromEntity(User user) {
        return new LoginResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail()
        );
    }
}