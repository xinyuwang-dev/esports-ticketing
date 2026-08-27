package com.xinyu.esportsticketing.service;

import com.xinyu.esportsticketing.entity.User;
import com.xinyu.esportsticketing.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void shouldLoginWithValidCredentials() {

        User user = new User();
        user.setUsername("leo_wang");
        user.setPasswordHash(
                new BCryptPasswordEncoder().encode("Demo123!")
        );

        when(userRepository.findByUsername("leo_wang"))
                .thenReturn(Optional.of(user));

        User result = userService.login(
                "leo_wang",
                "Demo123!"
        );

        assertEquals(user, result);
    }

    @Test
    void shouldRejectInvalidPassword() {

        User user = new User();
        user.setUsername("leo_wang");
        user.setPasswordHash(
                new BCryptPasswordEncoder().encode("Demo123!")
        );

        when(userRepository.findByUsername("leo_wang"))
                .thenReturn(Optional.of(user));

        assertThrows(
                IllegalArgumentException.class,
                () -> userService.login("leo_wang", "WrongPassword")
        );
    }

    @Test
    void shouldRejectUnknownUsername() {

        when(userRepository.findByUsername("unknown_user"))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> userService.login("unknown_user", "Demo123!")
        );
    }
}