package com.campuspulse.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DataSource dataSource;

    @Test
    @DisplayName("GET /health/live returns HTTP 200 with status UP")
    void livenessEndpoint_shouldReturnOk() throws Exception {
        mockMvc.perform(get("/health/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));
    }

    @Test
    @DisplayName("GET /health returns HTTP 200 when database connectivity is healthy")
    void healthEndpoint_whenDatabaseIsReachable_shouldReturnOk() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute(anyString())).thenReturn(true);

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.service", is("CampusPulse")))
                .andExpect(jsonPath("$.database", is("UP")));
    }

    @Test
    @DisplayName("GET /health returns HTTP 503 when database connection fails")
    void healthEndpoint_whenDatabaseFails_shouldReturnServiceUnavailable() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused"));

        mockMvc.perform(get("/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status", is("DEGRADED")))
                .andExpect(jsonPath("$.service", is("CampusPulse")))
                .andExpect(jsonPath("$.database", is("DOWN")));
    }
}
