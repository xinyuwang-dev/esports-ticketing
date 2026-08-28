package com.xinyu.esportsticketing.service;

import com.xinyu.esportsticketing.entity.User;
import com.xinyu.esportsticketing.exception.DuplicateUserException;
import com.xinyu.esportsticketing.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User login(String username, String password) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new IllegalArgumentException("Invalid username or password"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        return user;
    }

    public User register(String username, String email, String password) {

        if (userRepository.findByUsername(username).isPresent()) {
            throw new DuplicateUserException("Username already exists");
        }

        if (userRepository.findByEmail(email).isPresent()) {
            throw new DuplicateUserException("Email already exists");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);

        user.setPasswordHash(
                passwordEncoder.encode(password)
        );

        return userRepository.save(user);
    }
}