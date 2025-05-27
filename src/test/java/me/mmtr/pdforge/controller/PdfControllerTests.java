package me.mmtr.pdforge.controller;

import com.mongodb.client.gridfs.model.GridFSFile;
import me.mmtr.pdforge.model.User;
import me.mmtr.pdforge.repository.UserRepository;
import me.mmtr.pdforge.service.PdfService;
import org.bson.BsonObjectId;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PdfController.class)
public class PdfControllerTests {

    @MockitoBean
    private PdfService pdfService;

    @MockitoBean
    private UserRepository userRepository;

    @Autowired
    private MockMvc mockMvc;

    private ObjectId testObjectId;

    private User testUser;

    @BeforeEach
    public void setUp() {
        testUser = new User("1", "test", "secret password");
        testObjectId = new ObjectId("64e8c4f1f5a4c9453a6c2b91");

        when(userRepository.findByUsername("test"))
                .thenReturn(Optional.of(new User("1", "test", "secret password")));
    }

    @Test
    @WithMockUser(username = "test")
    public void shouldCorrectlySaveNewDocument() throws Exception {
        mockMvc.perform(post("/pdf/new")
                        .param("filename", "test.pdf")
                        .param("delta", "some delta")
                        .param("htmlContent", "some html content")
                ).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/home"));

        verify(pdfService, times(1)).saveAsPdf(
                "1",
                "test.pdf",
                "some html content",
                "some delta"
        );

        verify(pdfService, never()).deleteGridFSFile(any(ObjectId.class));

    }

    @Test
    @WithMockUser("test")
    public void shouldCorrectlyDeleteExistingDocumentAndSaveUpdatedVersion() throws Exception {

        mockMvc.perform(post("/pdf/new")
                        .param("filename", "test.pdf")
                        .param("delta", "some delta")
                        .param("htmlContent", "some html content")
                        .param("objectId", testObjectId.toString())
                ).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/home"));

        verify(pdfService, times(1)).saveAsPdf(
                "1",
                "test.pdf",
                "some html content",
                "some delta"
        );

        verify(pdfService, times(1)).deleteGridFSFile(testObjectId);
    }

    @Test
    @WithMockUser("test")
    public void shouldCorrectlyReturnListOfUsersSavedDocuments() throws Exception {
        List<GridFSFile> files = List.of(new GridFSFile(
                new BsonObjectId(testObjectId),
                "Test",
                5L,
                1048576,
                Date.from(Instant.now()),
                new Document()
        ));
        when(pdfService.getUserGridFSFiles(testUser.getId())).thenReturn(files);

        mockMvc.perform(get("/pdf/user-documents"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("files", files))
                .andExpect(view().name("user-documents"));

        verify(pdfService, times(1)).getUserGridFSFiles(testUser.getId());
    }

    @Test
    public void shouldCorrectlyDeletePdfDocument() throws Exception {
        mockMvc.perform(post("/pdf/delete")
                        .param("objectId", testObjectId.toString())
                ).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/pdf/user-documents"));

        verify(pdfService, times(1)).deleteGridFSFile(testObjectId);
    }

    @Test
    public void shouldCorrectlyViewPdfDocument() throws Exception {
        String filename = "test.pdf";
        byte[] testPdfBytes = "pdf content".getBytes();

        when(pdfService.getAsByteArray(testObjectId)).thenReturn(testPdfBytes);

        mockMvc.perform(get("/pdf/view")
                        .param("objectId", testObjectId.toString())
                        .param("filename", filename))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string(
                        "Content-Disposition",
                        "inline; filename=\"" + filename + "\"")
                )
                .andExpect(content().bytes(testPdfBytes));

        verify(pdfService, times(1)).getAsByteArray(testObjectId);

    }

    @TestConfiguration
    static class TestSecurityConfiguration {
        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http.authorizeHttpRequests(authorizeRequests ->
                            authorizeRequests
                                    .requestMatchers("/pdf/**").permitAll()
                    )
                    .csrf(AbstractHttpConfigurer::disable);

            return http.build();
        }
    }
}

