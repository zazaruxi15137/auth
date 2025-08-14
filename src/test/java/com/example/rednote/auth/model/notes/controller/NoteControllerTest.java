package com.example.rednote.auth.model.notes.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class NoteControllerTest {

    
    @Autowired
    private MockMvc mockMvc;

    private String token= "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0dXNlcjIiLCJ1aWQiOjIsImF1dGgiOiJbXCJST0xFX1VTRVJcIixcInN5czpkYXRhOnZpZXdcIixcInN5czpwcm9maWxlOmVkaXRcIixcInN5czpkYXRhOnVwbG9hZFwiXSIsInJvbGVzIjoiUk9MRV9VU0VSIiwiaWF0IjoxNzU1MTI4MDkzLCJleHAiOjE3NTUxMzUyOTMsImp0aSI6IjU5ZGFiYTY3LTMyZDQtNDU1NC1iNWEzLTdkODczMDVjYTNhZCJ9.vs2O2HNSWUenBtvl3MWy2Z6n760gQI3aiZ9TUh2C1FQ";

    //  测试图片上传接口
    @Test
    @WithMockUser(authorities = "sys:data:upload")
    void testPublishNoteImage() throws Exception {
        MockMultipartFile image1 = new MockMultipartFile(
                "images", "test.jpg", "image/jpeg",
                new ClassPathResource("test.jpg").getInputStream());

        // 替换为你的测试图片
        mockMvc.perform(multipart("/api/notes")
                        .file(image1)
                        .param("userId", "1")
                        .param("title", "")
                        .param("content", "")
                        .header("Authorization", token)
                        )
                // .andExpect(status().is(403));
                .andExpect(jsonPath("$.code").value(400));
    }



    //  测试根据id查笔记
    @Test
    @WithMockUser(authorities = "sys:data:view")
    void testSearchNoteById() throws Exception {
        // 假设数据库里id=1的笔记已存在
        mockMvc.perform(get("/api/notes/{noteId}", 1L))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists());
    }

    //  测试分页查
    @Test
    @WithMockUser(authorities = "sys:data:view")
    void testSearchNoteByPage() throws Exception {
        mockMvc.perform(get("/api/users/{userId}/notes", 1L)
                        .param("page", "1000000")
                        .param("size", "2"))
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @WithMockUser(authorities = "sys:data:upload")
    void testFollow() throws Exception {
        mockMvc.perform(post("/api/users/1/follow", 1L)
                        .header("Authorization", token))
                .andExpect(jsonPath("$.code").value(200));
    }


}
    