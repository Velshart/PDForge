package me.mmtr.pdforge.controller;

import me.mmtr.pdforge.model.User;
import me.mmtr.pdforge.repository.UserRepository;
import me.mmtr.pdforge.service.PdfService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

@Controller
@RequestMapping("/pdf")
public class PdfController {

    private final PdfService pdfService;
    private final UserRepository userRepository;

    public PdfController(PdfService pdfService, UserRepository userRepository) {
        this.pdfService = pdfService;
        this.userRepository = userRepository;
    }

    @PostMapping("/new")
    public String newPdfDocument(@RequestParam String filename,
                                 @RequestParam String delta,
                                 @RequestParam String htmlContent,
                                 Principal principal) {
        User principalUser = userRepository.findByUsername(principal.getName()).orElseThrow();

        pdfService.saveAsPdf(
                principalUser.getId(),
                filename,
                htmlContent,
                delta
        );

        pdfService.getAsPdf(filename);
        return "redirect:/home";
    }
}
