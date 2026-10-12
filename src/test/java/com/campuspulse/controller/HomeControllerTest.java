package com.campuspulse.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(HomeController.class)
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnIndexViewWithOkStatus() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("appName", "CampusPulse"))
                .andExpect(content().string(containsString("CampusPulse")))
                .andExpect(content().string(containsString("/css/style.css")))
                .andExpect(content().string(containsString("Resolution Flow")))
                .andExpect(content().string(containsString("Submit a Complaint")))
                .andExpect(content().string(containsString("Track Complaint Status")))
                .andExpect(content().string(containsString("Centralized Grievance Registry")))
                .andExpect(content().string(containsString("CAT-ACAD")));
    }
}
