package com.xinyu.esportsticketing.controller;

import com.xinyu.esportsticketing.entity.User;
import com.xinyu.esportsticketing.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void shouldLoginSuccessfully() throws Exception {

        User user = new User();
        user.setId(1);
        user.setUsername("leo_wang");
        user.setEmail("leo@example.com");
        user.setPasswordHash("secret-hash");

        when(userService.login("leo_wang", "Demo123!"))
                .thenReturn(user);

        mockMvc.perform(post("/api/users/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "leo_wang",
                                  "password": "Demo123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("leo_wang"))
                .andExpect(jsonPath("$.email").value("leo@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void shouldReturnUnauthorizedWhenPasswordIsInvalid() throws Exception {

        when(userService.login("leo_wang", "WrongPassword"))
                .thenThrow(new IllegalArgumentException(
                        "Invalid username or password"
                ));

        mockMvc.perform(post("/api/users/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {
                              "username": "leo_wang",
                              "password": "WrongPassword"
                            }
                            """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnUsersWithoutPasswordHash() throws Exception {

        User user = new User();
        user.setId(1);
        user.setUsername("leo_wang");
        user.setEmail("leo_wang@example.com");
        user.setPasswordHash("secret-hash");

        when(userService.getAllUsers())
                .thenReturn(List.of(user));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].username").value("leo_wang"))
                .andExpect(jsonPath("$[0].email").value("leo_wang@example.com"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].createdAt").doesNotExist());
    }
}