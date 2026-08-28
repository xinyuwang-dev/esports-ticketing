package com.xinyu.esportsticketing.controller;

import com.xinyu.esportsticketing.dto.LoginRequest;
import com.xinyu.esportsticketing.dto.LoginResponse;
import com.xinyu.esportsticketing.dto.RegisterRequest;
import com.xinyu.esportsticketing.dto.UserResponse;
import com.xinyu.esportsticketing.entity.User;
import com.xinyu.esportsticketing.exception.DuplicateUserException;
import com.xinyu.esportsticketing.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {

        List<UserResponse> users = userService.getAllUsers()
                .stream()
                .map(UserResponse::fromEntity)
                .toList();

        return ResponseEntity.ok(users);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @RequestBody LoginRequest request) {

        User user = userService.login(
                request.getUsername(),
                request.getPassword()
        );

        return ResponseEntity.ok(
                LoginResponse.fromEntity(user)
        );
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
            @   Valid @RequestBody RegisterRequest request) {

        User user = userService.register(
                request.getUsername(),
                request.getEmail(),
                request.getPassword()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(UserResponse.fromEntity(user));
    }

    @ExceptionHandler(DuplicateUserException.class)
    public ResponseEntity<Void> handleDuplicateUser(
            DuplicateUserException exception) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Void> handleInvalidCredentials(
            IllegalArgumentException exception) {

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .build();
    }
}