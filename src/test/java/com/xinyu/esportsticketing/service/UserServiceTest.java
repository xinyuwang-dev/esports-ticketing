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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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

    @Test
    void shouldRegisterNewUser() {

        when(userRepository.findByUsername("new_user"))
                .thenReturn(Optional.empty());

        when(userRepository.findByEmail("new@example.com"))
                .thenReturn(Optional.empty());

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.register(
                "new_user",
                "new@example.com",
                "Hello123!"
        );

        assertEquals("new_user", result.getUsername());
        assertEquals("new@example.com", result.getEmail());

        assertTrue(
                new BCryptPasswordEncoder()
                        .matches("Hello123!", result.getPasswordHash())
        );
    }

    @Test
    void shouldRejectDuplicateUsername() {

        User existingUser = new User();

        when(userRepository.findByUsername("new_user"))
                .thenReturn(Optional.of(existingUser));

        assertThrows(
                IllegalArgumentException.class,
                () -> userService.register(
                        "new_user",
                        "new@example.com",
                        "Hello123!"
                )
        );
    }

    @Test
    void shouldRejectDuplicateEmail() {

        when(userRepository.findByUsername("new_user"))
                .thenReturn(Optional.empty());

        User existingUser = new User();

        when(userRepository.findByEmail("new@example.com"))
                .thenReturn(Optional.of(existingUser));

        assertThrows(
                IllegalArgumentException.class,
                () -> userService.register(
                        "new_user",
                        "new@example.com",
                        "Hello123!"
                )
        );
    }
}