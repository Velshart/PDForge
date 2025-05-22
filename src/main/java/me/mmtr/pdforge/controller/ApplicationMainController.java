package me.mmtr.pdforge.controller;

import com.mongodb.client.gridfs.model.GridFSFile;
import me.mmtr.pdforge.model.User;
import me.mmtr.pdforge.repository.UserRepository;
import me.mmtr.pdforge.service.PdfService;
import me.mmtr.pdforge.service.UserServiceImplementation;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.Objects;

@Controller
public class ApplicationMainController {
    private final UserServiceImplementation userService;
    private final UserRepository userRepository;

    private final PdfService pdfService;

    public ApplicationMainController(UserServiceImplementation userService, UserRepository userRepository, PdfService pdfService) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.pdfService = pdfService;
    }

    @GetMapping("/home")
    public String home(Principal principal,
                       @RequestParam(required = false) ObjectId updatedDocumentObjectId, Model model) {

        User principalUser = userRepository.findByUsername(principal.getName()).orElseThrow();

        GridFSFile updatedDocument = null;
        String delta = null;
        if (updatedDocumentObjectId != null) {
            updatedDocument = pdfService.getAsGridFSFile(principalUser.getId(), updatedDocumentObjectId);
            delta = Objects.requireNonNull(
                    pdfService.getAsGridFSFile(principalUser.getId(),
                            updatedDocumentObjectId).getMetadata()
            ).get("delta").toString();
        }

        model.addAttribute(
                "updatedDocument",
                updatedDocument
        );

        model.addAttribute("delta", delta);
        return "home";
    }

    @GetMapping("/login")
    public String login(Model model,
                        @RequestParam(value = "error", required = false) String error,
                        @RequestParam(value = "logout", required = false) String logout) {

        if (error != null) {
            model.addAttribute("error", "Incorrect username or password provided");
        }
        if (logout != null) {
            model.addAttribute("logout", logout);
        }
        return "login";
    }

    @GetMapping("/register")
    public String registrationForm(Model model) {
        User user = new User();
        model.addAttribute("user", user);
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(@ModelAttribute("user") User user, BindingResult bindingResult, Model model) {
        User existingUser = userRepository.findByUsername(user.getUsername()).orElse(null);

        if (existingUser != null && existingUser.getUsername() != null && !existingUser.getUsername().isEmpty()) {
            bindingResult.rejectValue("username", "exists", "This username is already in use");
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("user", user);
            return "register";
        }

        userService.registerUser(user.getUsername(), user.getPassword());
        return "redirect:/login";
    }
}
