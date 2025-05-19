package me.mmtr.pdforge.controller;

import me.mmtr.pdforge.model.User;
import me.mmtr.pdforge.repository.UserRepository;
import me.mmtr.pdforge.service.PdfService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
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
        return "redirect:/home";
    }

    @GetMapping("/user-documents")
    public String userPdfs(Principal principal, Model model) {
        User principalUser = userRepository.findByUsername(principal.getName()).orElseThrow();
        model.addAttribute("files", pdfService.getUserGridFSFiles(principalUser.getId()));

        return "user-documents";
    }

    @PostMapping("/delete")
    public String deletePdfDocument(@RequestParam String filename, Principal principal) {
        User principalUser = userRepository.findByUsername(principal.getName()).orElseThrow();
        pdfService.deleteGridFSFile(principalUser.getId(), filename);

        return "redirect:/pdf/user-documents";
    }
}
