package com.mtchlvr.hotel.web;

import com.mtchlvr.hotel.model.AppUser;
import com.mtchlvr.hotel.model.CustomerRegistrationApplication;
import com.mtchlvr.hotel.repo.CustomerRegistrationApplicationRepository;
import com.mtchlvr.hotel.repo.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.*;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@Controller
@RequestMapping("/super-admin/registrations")
public class SuperAdminRegistrationController {
    private final CustomerRegistrationApplicationRepository applications;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JavaMailSender mailSender;
    private final String mailUsername;
    private final Path uploadRoot;

    public SuperAdminRegistrationController(CustomerRegistrationApplicationRepository applications,
            UserRepository users, PasswordEncoder encoder, JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String mailUsername,
            @Value("${va.hotel.identity-upload-dir:./private-uploads/identity}") String uploadDir) {
        this.applications=applications;this.users=users;this.encoder=encoder;
        this.mailSender=mailSender;this.mailUsername=mailUsername;
        this.uploadRoot=Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @GetMapping
    public String queue(Model model) {
        model.addAttribute("applications",applications.findAllByStatusOrderByCreatedAtAsc("PENDING_APPROVAL"));
        model.addAttribute("rejectedApplications",applications.findAllByStatusOrderByCreatedAtAsc("REJECTED"));
        return "super-admin-registrations";
    }

    @GetMapping("/{id}/id/{side}")
    @ResponseBody
    public ResponseEntity<Resource> identityImage(@PathVariable Long id,@PathVariable String side) {
        CustomerRegistrationApplication a=applications.findById(id).orElseThrow();
        String stored="front".equals(side)?a.getFrontDocumentPath():"back".equals(side)?a.getBackDocumentPath():null;
        if(stored==null) return ResponseEntity.badRequest().build();
        try {
            Path file=Paths.get(stored).toAbsolutePath().normalize();
            if(!file.startsWith(uploadRoot) || !Files.isRegularFile(file)) return ResponseEntity.notFound().build();
            String type=Files.probeContentType(file);
            MediaType media="image/png".equals(type)?MediaType.IMAGE_PNG:MediaType.IMAGE_JPEG;
            return ResponseEntity.ok().contentType(media)
                    .header(HttpHeaders.CACHE_CONTROL,"no-store, no-cache, must-revalidate")
                    .header(HttpHeaders.PRAGMA,"no-cache")
                    .header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\"identity-image\"")
                    .body(new FileSystemResource(file));
        } catch(Exception ex) { return ResponseEntity.notFound().build(); }
    }

    @PostMapping("/{id}/approve")
    @Transactional
    public String approve(@PathVariable Long id,org.springframework.security.core.Authentication auth,
            RedirectAttributes flash) {
        CustomerRegistrationApplication a=applications.findById(id).orElse(null);
        if(a==null || !"PENDING_APPROVAL".equals(a.getStatus())) {
            flash.addFlashAttribute("error","Pending registration application not found.");
            return "redirect:/super-admin/registrations";
        }
        if(users.existsByEmailIgnoreCase(a.getEmail())) {
            a.decide("REJECTED","An account already exists for this email.",auth.getName());
            applications.save(a);
            flash.addFlashAttribute("error","The email already belongs to an account. Application marked for rejection.");
            return "redirect:/super-admin/registrations";
        }
        String customerCode=generateCustomerCode();
        AppUser customer=new AppUser(a.getEmail(),a.getPasswordHash(),
                a.getFirstName()+" "+a.getLastName(),"CUSTOMER",true);
        customer.setCustomerCode(customerCode);
        users.save(customer);
        a.decide("APPROVED","Identity reviewed and approved.",auth.getName());
        applications.save(a);
        notifyApplicant(a.getEmail(),"Your mtchlvr hotel account is approved",
                "Your registration and identity document have been approved. You may now sign in to mtchlvr hotel.");
        flash.addFlashAttribute("success","Customer account approved and activated.");
        return "redirect:/super-admin/registrations";
    }

    @PostMapping("/{id}/reject")
    @Transactional
    public String reject(@PathVariable Long id,@RequestParam String reason,
            org.springframework.security.core.Authentication auth,RedirectAttributes flash) {
        CustomerRegistrationApplication a=applications.findById(id).orElse(null);
        String clean=reason==null?"":reason.trim();
        if(a==null || !"PENDING_APPROVAL".equals(a.getStatus()) || clean.isBlank() || clean.length()>1000) {
            flash.addFlashAttribute("error","Enter a rejection reason and select a pending application.");
            return "redirect:/super-admin/registrations";
        }
        a.decide("REJECTED",clean,auth.getName());
        applications.save(a);
        notifyApplicant(a.getEmail(),"mtchlvr hotel registration update",
                "Your registration was not approved. Reason: "+clean+" If you believe this is an error, contact the hotel administrator.");
        flash.addFlashAttribute("success","Registration rejected. The applicant has been notified.");
        return "redirect:/super-admin/registrations";
    }

    private String generateCustomerCode() {
        String code;
        do { code=String.format(Locale.ROOT,"mtchlvr-%04d-5201314",
                ThreadLocalRandom.current().nextInt(10000)); }
        while(users.existsByCustomerCode(code));
        return code;
    }
    private void notifyApplicant(String email,String subject,String body) {
        if(mailUsername.isBlank()) return;
        try {
            SimpleMailMessage m=new SimpleMailMessage();
            m.setFrom(mailUsername);m.setTo(email);m.setSubject(subject);m.setText(body);mailSender.send(m);
        } catch(Exception ignored) { /* Decision is persisted even if notification delivery is temporarily unavailable. */ }
    }
}
