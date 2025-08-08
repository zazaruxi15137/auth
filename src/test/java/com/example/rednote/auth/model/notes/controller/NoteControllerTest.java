package com.example.rednote.auth.model.notes.controller;
import com.example.rednote.auth.model.notes.dto.NoteRequestDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.Arrays;

@SpringBootTest
@AutoConfigureMockMvc
public class NoteControllerTest {

    
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    // 1. 测试图片上传接口
    @Test
    @WithMockUser(authorities = "sys:data:upload")
    void testPublishNoteImage() throws Exception {
        MockMultipartFile image1 = new MockMultipartFile(
                "images", "test1.jpg", "image/jpeg",
                new ClassPathResource("test-image.jpg").getInputStream());
        // 替换为你的测试图片路径
        mockMvc.perform(multipart("/api/notes/image")
                        .file(image1)
                        .param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());
    }

    // 2. 测试笔记上传接口
    @Test
    @WithMockUser(authorities = "sys:data:upload")
    void testPublishNote() throws Exception {
        // 构造你的requestDto
        var request = new NoteRequestDto();
        request.setTitle("test");
        request.setAuthorId(1L);
        request.setContent("test-content");
        request.setImagesUrls(Arrays.asList("Apple.jpg","Banana.jpg", "Orange.jpg"));

        mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());
    }

    // 3. 测试根据id查笔记
    @Test
    @WithMockUser(authorities = "sys:data:view")
    void testSearchNoteById() throws Exception {
        // 假设数据库里id=1的笔记已存在
        mockMvc.perform(get("/api/notes/{noteId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());
    }

    // 4. 测试分页查
    @Test
    @WithMockUser(authorities = "sys:data:view")
    void testSearchNoteByPage() throws Exception {
        mockMvc.perform(get("/api/users/{userId}/notes", 1L)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").exists());
    }

    // 5. 测试图片访问接口
    @Test
    @WithMockUser(authorities = "sys:data:view")
    void testGetImage() throws Exception {
        // 保证有图片 test.jpg 已经在文件夹下
        mockMvc.perform(get("/api/notes/image/{userId}/{filename}", 1L, "test.jpg"))
                .andExpect(status().isNotFound());
    }
}
    