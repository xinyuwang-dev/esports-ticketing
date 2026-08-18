package com.xinyu.esportsticketing.dto;

public class CreateOrderRequest {

    private Integer userId;
    private Integer categoryId;

    public CreateOrderRequest() {
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }
}