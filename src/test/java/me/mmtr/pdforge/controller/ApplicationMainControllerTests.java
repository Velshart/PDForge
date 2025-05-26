package me.mmtr.pdforge.controller;

import com.mongodb.client.gridfs.model.GridFSFile;
import me.mmtr.pdforge.model.User;
import me.mmtr.pdforge.repository.UserRepository;
import me.mmtr.pdforge.service.PdfService;
import me.mmtr.pdforge.service.UserServiceImplementation;
import org.bson.BsonObjectId;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ApplicationMainController.class)
public class ApplicationMainControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserServiceImplementation userService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PdfService pdfService;

    @Test
    @WithMockUser(username = "Test")
    public void shouldCorrectlyReturnHomePage() throws Exception {
        User testUser = new User("1", "Test", "password");
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(testUser));

        ObjectId testObjectId = new ObjectId("64e8c4f1f5a4c9453a6c2b91");
        Document metadata = new Document("delta", "some delta");
        GridFSFile mockFile = new GridFSFile(
                new BsonObjectId(testObjectId),
                "Test",
                5L,
                1048576,
                Date.from(Instant.now()),
                metadata
        );

        when(pdfService.getAsGridFSFile(eq(testUser.getId()), any(ObjectId.class)))
                .thenReturn(mockFile);

        mockMvc.perform(get("/home")
                        .param("objectId", testObjectId.toString()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("updatedDocument"))
                .andExpect(model().attributeExists("delta"))
                .andExpect(view().name("home"));
    }

    @Test
    public void shouldReturnLoginViewWithoutParameters() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(model().attributeDoesNotExist("error", "logout"));
    }

    @Test
    public void shouldReturnLoginViewWithAnError() throws Exception {
        mockMvc.perform(get("/login").param("error", "error"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(model().attributeExists("error"))
                .andExpect(model().attributeDoesNotExist("logout"))
                .andExpect(model().attribute("error", "Incorrect username or password provided"));
    }

    @Test
    public void shouldReturnLoginViewWithLogoutMessage() throws Exception {
        mockMvc.perform(get("/login").param("logout", "logout"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(model().attributeExists("logout"))
                .andExpect(model().attributeDoesNotExist("error"))
                .andExpect(model().attribute("logout", "logout"));
    }

    @Test
    public void shouldReturnRegistrationViewWithAnAttribute() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("user"))
                .andExpect(model().attribute("user", new User()));
    }

    @Test
    public void shouldReturnAnErrorWhenUserAlreadyExists() throws Exception {
        Mockito.when(userRepository.findByUsername(anyString()))
                .thenReturn(Optional.of(new User("1",
                        "username",
                        "password"
                )));

        mockMvc.perform(post("/register")
                        .param("username", "username")
                        .param("password", "password")
                )
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("user"))
                .andExpect(model().attributeHasFieldErrors("user", "username"));
    }

    @Test
    public void shouldSuccessfullyRegisterUserAndRedirectToLoginPage() throws Exception {
        Mockito.when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/register")
                        .flashAttr("user", new User())
                        .param("username", "username")
                        .param("password", "password")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }


    @TestConfiguration
    static class TestSecurityConfiguration {
        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http.authorizeHttpRequests(authorizeRequests ->
                            authorizeRequests
                                    .requestMatchers("/home").permitAll()
                                    .requestMatchers("/login").permitAll()
                                    .requestMatchers("/register").permitAll()
                    )
                    .csrf(AbstractHttpConfigurer::disable);

            return http.build();
        }
    }
}
