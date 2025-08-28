package com.example.rednote.auth.model.notes.controller;


import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Random;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class UserFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Replace with valid tokens for each user (A and B)
    private final String tokenA = "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0dXNlcjEwMCIsInVpZCI6NTIsImF1dGgiOiJbXCJST0xFX1VTRVJcIixcInN5czpkYXRhOnZpZXdcIixcInN5czpwcm9maWxlOmVkaXRcIixcInN5czpkYXRhOnVwbG9hZFwiXSIsInJvbGVzIjoiUk9MRV9VU0VSIiwiaWF0IjoxNzU2MDc2OTYxLCJleHAiOjE3NTYwODQxNjEsImp0aSI6ImJkODQ1MDMyLTcyMmItNDI0NC05M2FlLWFlNzU4YjJlOGE1OCJ9.v3loNAHV-OCvrfJkYlf6jkyu1IUKc5UHI-oom6E9pbw";
    private final String tokenB = "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0dXNlcjIwMCIsInVpZCI6NTMsImF1dGgiOiJbXCJST0xFX1VTRVJcIixcInN5czpkYXRhOnZpZXdcIixcInN5czpwcm9maWxlOmVkaXRcIixcInN5czpkYXRhOnVwbG9hZFwiXSIsInJvbGVzIjoiUk9MRV9VU0VSIiwiaWF0IjoxNzU2MDc3MDIxLCJleHAiOjE3NTYwODQyMjEsImp0aSI6Ijg5MTI1ZDRkLWE2ZjctNGNjYS1hMzkyLTk3NjU2N2FkM2MyOCJ9.734PuA-YUUVXR_soaqgaI7e2GRaAq4kVbx5vC6xgEu8";

    // Assume these user IDs exist in your test DB
    private final Long userAId = 52L;
    private final Long userBId = 53L;

    @BeforeEach
    public void setup() throws Exception {
        // Optionally create users A and B if needed, or ensure they exist
        // Example (if you have an endpoint):
        // mockMvc.perform(post("/api/users/register")...
    }

    @Test
    public void testFollowNoteFeedUnfollowFlow() throws Exception {
        Random r=new Random();
        // 1. A follows B
        mockMvc.perform(post("/api/users/{targetId}/follow", userBId)
                    .header("Authorization", tokenA)
                    .header("Idempotency-Key",r.nextInt(1000)+"ssss" ))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.message").value("Follow success:已关注" + userBId));

        // 2. B posts a note with an image
        MockMultipartFile image1 = new MockMultipartFile(
                "images", "test.jpg", "image/jpeg",
                new ClassPathResource("test.jpg").getInputStream());
        MvcResult postNoteResult = mockMvc.perform(multipart("/api/notes")
                    .file(image1)
                    .param("userId", userBId.toString())
                    .param("title", "Hello World")
                    .param("content", "This is a test note.")
                    .param("isPublic", "true")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .header("Authorization", tokenB)
                    .header("Idempotency-Key",r.nextInt(1000)+"ssss" ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        // Extract the posted note's ID
        String postNoteJson = postNoteResult.getResponse().getContentAsString();
        JsonNode postNoteNode = objectMapper.readTree(postNoteJson);
        Long noteId = postNoteNode.path("data").path("id").asLong();

        // 3. A pulls the feed (first page)
        MvcResult feedResult1 = mockMvc.perform(get("/api/feed")
                    .param("size", "10")
                    .header("Authorization", tokenA)
                    .header("Idempotency-Key",r.nextInt(1000)+"ssss" ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notes").isArray())
                .andExpect(jsonPath("$.data.notes[0].id").value(noteId.intValue()))
                .andReturn();

        // 4. Optionally test pagination: set cursor and noteId to simulate next page
        // (assuming your feed uses cursor/noteId in some way)
        mockMvc.perform(get("/api/feed")
                    .param("cursor", "0")      // adjust per your logic
                    .param("size", "10")
                    .param("noteId", noteId.toString())
                    .header("Authorization", tokenA)
                    .header("Idempotency-Key",r.nextInt(1000)+"ssss" ))
               .andExpect(status().isOk());

        // 5. A unfollows B
        mockMvc.perform(delete("/api/users/{targetId}/follow", userBId)
                    .header("Authorization", tokenA)
                    .header("Idempotency-Key",r.nextInt(1000)+"ssss" ))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.message").value("Unfollow success:已取消关注" + userBId));

        // 6. A pulls feed again — should no longer see B's note
        mockMvc.perform(get("/api/feed")
                    .param("size", "10")
                    .header("Authorization", tokenA)
                    .header("Idempotency-Key",r.nextInt(1000)+"ssss" ))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.data[?(@.id == " + noteId + ")]").doesNotExist());
    }
}
