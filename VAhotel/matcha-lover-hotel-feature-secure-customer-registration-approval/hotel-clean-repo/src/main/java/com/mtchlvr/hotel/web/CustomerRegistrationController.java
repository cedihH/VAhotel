package com.mtchlvr.hotel.web;

import com.mtchlvr.hotel.model.CustomerRegistrationApplication;
import com.mtchlvr.hotel.repo.CustomerRegistrationApplicationRepository;
import com.mtchlvr.hotel.repo.UserRepository;
import com.mtchlvr.hotel.service.CaptchaService;
import com.mtchlvr.hotel.service.PasswordPolicy;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.Email;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/**
 * Public five-stage customer registration. No AppUser is created until a Super Admin
 * approves the private identity application.
 */
@Controller
public class CustomerRegistrationController {
    private static final String DRAFT = "customerRegistrationDraft";
    private static final String OTP_VERIFIED = "customerRegistrationOtpVerified";
    private static final Set<String> ID_TYPES = Set.of(
            "PHILIPPINE_NATIONAL_ID", "PASSPORT", "DRIVERS_LICENSE",
            "UMID", "PRC_ID", "POSTAL_ID", "VOTERS_CERTIFICATION");
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private final UserRepository users;
    private final CustomerRegistrationApplicationRepository applications;
    private final PasswordEncoder encoder;
    private final CaptchaService captcha;
    private final PasswordPolicy passwordPolicy;
    private final JavaMailSender mailSender;
    private final Path uploadRoot;
    private final String mailUsername;
    private final SecureRandom random = new SecureRandom();

    public CustomerRegistrationController(UserRepository users,
            CustomerRegistrationApplicationRepository applications,
            PasswordEncoder encoder, CaptchaService captcha, PasswordPolicy passwordPolicy, JavaMailSender mailSender,
            @Value("${va.hotel.identity-upload-dir:./private-uploads/identity}") String uploadDir,
            @Value("${spring.mail.username:}") String mailUsername) {
        this.users=users; this.applications=applications; this.encoder=encoder;
        this.captcha=captcha; this.passwordPolicy=passwordPolicy; this.mailSender=mailSender;
        this.uploadRoot=Paths.get(uploadDir).toAbsolutePath().normalize();
        this.mailUsername=mailUsername;
    }

    @GetMapping("/register")
    public String registration(Model model, HttpSession session) {
        model.addAttribute("captchaSiteKey", "");
        model.addAttribute("passwordPolicy", passwordPolicy);
        return "register";
    }

    @PostMapping("/register/start")
    public String start(@RequestParam String firstName, @RequestParam String lastName,
            @RequestParam @Email String email, @RequestParam String password,
            @RequestParam String confirmPassword,
            @RequestParam(name="g-recaptcha-response", required=false) String captchaToken,
            jakarta.servlet.http.HttpServletRequest request, HttpSession session,
            Model model, RedirectAttributes flash) {
        String cleanFirst=clean(firstName), cleanLast=clean(lastName);
        String cleanEmail=clean(email).toLowerCase(Locale.ROOT);
        if(!validName(cleanFirst) || cleanFirst.length()>60 || !validName(cleanLast) || cleanLast.length()>60
                || cleanEmail.length()>160 || !cleanEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
                || !passwordPolicy.isValid(password) || !password.equals(confirmPassword)) {
            flash.addFlashAttribute("error","Check your name, email, and password requirements.");
            preserveRegistrationFields(flash, cleanFirst, cleanLast, cleanEmail);
            return "redirect:/register";
        }
        if(users.existsByEmailIgnoreCase(cleanEmail) || applications.existsByEmailIgnoreCase(cleanEmail)) {
            // Avoid exposing whether an address belongs to an existing account.
            flash.addFlashAttribute("error","We could not start registration with those details. Check the information or try another email address.");
            preserveRegistrationFields(flash, cleanFirst, cleanLast, cleanEmail);
            return "redirect:/register";
        }
        if(!captcha.verify(captchaToken, request.getRemoteAddr())) {
            flash.addFlashAttribute("error","reCAPTCHA verification failed. Please complete the checkbox again.");
            preserveRegistrationFields(flash, cleanFirst, cleanLast, cleanEmail);
            return "redirect:/register";
        }
        if(mailUsername.isBlank()) {
            flash.addFlashAttribute("error","Email delivery is not configured. Please contact the hotel administrator.");
            preserveRegistrationFields(flash, cleanFirst, cleanLast, cleanEmail);
            return "redirect:/register";
        }
        String otp=String.format(Locale.ROOT,"%06d",random.nextInt(1_000_000));
        RegistrationDraft draft=new RegistrationDraft(cleanFirst,cleanLast,cleanEmail,
                encoder.encode(password),encoder.encode(otp),Instant.now().plus(Duration.ofMinutes(5)),Instant.now(),0);
        try {
            sendOtp(cleanEmail,otp);
            session.setAttribute(DRAFT,draft);
            session.setAttribute(OTP_VERIFIED,false);
        } catch(Exception ex) {
            flash.addFlashAttribute("error","We could not send the verification email. Please try again later.");
            preserveRegistrationFields(flash, cleanFirst, cleanLast, cleanEmail);
            return "redirect:/register";
        }
        return "redirect:/register?step=3";
    }

    @PostMapping("/register/resend-otp")
    public String resendOtp(HttpSession session, RedirectAttributes flash) {
        RegistrationDraft d=draft(session);
        if(d==null || Boolean.TRUE.equals(session.getAttribute(OTP_VERIFIED))) return "redirect:/register";
        if(d.resends>=3) {
            flash.addFlashAttribute("error","You have reached the resend limit. Please restart registration.");
            return "redirect:/register";
        }
        if(Duration.between(d.lastSentAt,Instant.now()).getSeconds()<60) {
            flash.addFlashAttribute("error","Please wait 60 seconds before requesting another code.");
            return "redirect:/register?step=3";
        }
        String otp=String.format(Locale.ROOT,"%06d",random.nextInt(1_000_000));
        String otpHash=encoder.encode(otp);
        d.lastSentAt=Instant.now();
        d.resends++;
        try {
            // Keep the current code valid if SMTP rejects the resend.
            sendOtp(d.email,otp);
            d.otpHash=otpHash; d.expiresAt=Instant.now().plus(Duration.ofMinutes(5));
            d.attempts=0;
        } catch(Exception ex) {
            flash.addFlashAttribute("error","We could not send a new code. Please try again.");
        }
        return "redirect:/register?step=3";
    }

    @PostMapping("/register/verify-otp")
    public String verifyOtp(@RequestParam String otp, HttpSession session, RedirectAttributes flash) {
        RegistrationDraft d=draft(session);
        if(d==null) return "redirect:/register";
        if(d.attempts>=5) {
            session.removeAttribute(DRAFT);
            flash.addFlashAttribute("error","Too many incorrect codes. Please restart registration.");
            return "redirect:/register";
        }
        d.attempts++;
        if(Instant.now().isAfter(d.expiresAt) || !otp.matches("\\d{6}") || !encoder.matches(otp,d.otpHash)) {
            flash.addFlashAttribute("error",Instant.now().isAfter(d.expiresAt)
                    ? "That code has expired. Request a new code." : "The code is incorrect. Please try again.");
            return "redirect:/register?step=3";
        }
        session.setAttribute(OTP_VERIFIED,true);
        return "redirect:/register?step=4";
    }

    @PostMapping(value="/register/submit",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public String submit(@RequestParam String idType, @RequestParam MultipartFile frontId,
            @RequestParam MultipartFile backId, @RequestParam(defaultValue="false") boolean acceptTerms,
            @RequestParam(defaultValue="false") boolean acceptPrivacy,
            @RequestParam(defaultValue="false") boolean consentIdentityReview,
            HttpSession session, RedirectAttributes flash) {
        RegistrationDraft d=draft(session);
        if(d==null || !Boolean.TRUE.equals(session.getAttribute(OTP_VERIFIED))) {
            flash.addFlashAttribute("error","Verify your email before submitting identity documents.");
            return "redirect:/register";
        }
        if(!ID_TYPES.contains(idType) || !acceptTerms || !acceptPrivacy || !consentIdentityReview) {
            flash.addFlashAttribute("error","Choose an ID type and accept all required notices.");
            return "redirect:/register?step=4";
        }
        String frontPath=null,backPath=null;
        try {
            Files.createDirectories(uploadRoot);
            frontPath=storeImage(frontId);
            backPath=storeImage(backId);
            CustomerRegistrationApplication application=new CustomerRegistrationApplication(
                    d.email,d.firstName,d.lastName,d.passwordHash,"EMAIL_VERIFIED",
                    Instant.now(),d.lastSentAt,idType,frontPath,backPath,Instant.now());
            applications.save(application);
            session.removeAttribute(DRAFT);
            session.removeAttribute(OTP_VERIFIED);
            flash.addFlashAttribute("success","Your application was submitted. Wait for the Super Admin to review your ID and approve your account before signing in.");
            return "redirect:/register/pending";
        } catch(Exception ex) {
            deleteQuietly(frontPath); deleteQuietly(backPath);
            flash.addFlashAttribute("error","We could not safely store your ID photos. Please upload clear JPG or PNG images again.");
            return "redirect:/register?step=4";
        }
    }

    @GetMapping("/register/pending")
    public String pending(){return "registration-pending";}

    private String storeImage(MultipartFile file) throws IOException {
        if(file==null || file.isEmpty() || file.getSize()>MAX_IMAGE_BYTES) throw new IOException("Invalid image size");
        String type=file.getContentType();
        if(!"image/jpeg".equalsIgnoreCase(type) && !"image/png".equalsIgnoreCase(type))
            throw new IOException("Only JPG and PNG images are accepted");
        BufferedImage image;
        try (InputStream input=file.getInputStream(); ImageInputStream imageInput=ImageIO.createImageInputStream(input)) {
            if(imageInput==null) throw new IOException("Invalid image");
            Iterator<ImageReader> readers=ImageIO.getImageReaders(imageInput);
            if(!readers.hasNext()) throw new IOException("Unsupported image format");
            ImageReader reader=readers.next();
            try {
                reader.setInput(imageInput,true,true);
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<800 || height<500 || width>10000 || height>10000 || (long)width*height>20_000_000L)
                    throw new IOException("Image dimensions are invalid or too large");
                image=reader.read(0);
            } finally {
                reader.dispose();
            }
        }
        if(image==null) throw new IOException("Invalid image");
        String ext="image/png".equalsIgnoreCase(type)?".png":".jpg";
        Path target=uploadRoot.resolve(UUID.randomUUID()+ext).normalize();
        if(!target.startsWith(uploadRoot)) throw new IOException("Invalid file path");
        String format="image/png".equalsIgnoreCase(type)?"png":"jpg";
        // Re-encode images to strip EXIF metadata, including possible GPS coordinates.
        if(!ImageIO.write(image,format,target.toFile())) throw new IOException("Unsupported image format");
        return target.toString();
    }

    private void sendOtp(String email,String otp) {
        SimpleMailMessage message=new SimpleMailMessage();
        message.setFrom(mailUsername); message.setTo(email);
        message.setSubject("mtchlvr hotel email verification code");
        message.setText("Your mtchlvr hotel verification code is "+otp+". It expires in 5 minutes. Do not share this code with anyone.");
        mailSender.send(message);
    }
    private RegistrationDraft draft(HttpSession s){return (RegistrationDraft)s.getAttribute(DRAFT);}
    private String clean(String s){return s==null?"":s.trim();}
    private boolean validName(String name) {
        return name != null && !name.isBlank() && name.matches("[\\p{L}\\p{M}]+(?:[ '\\u2019-][\\p{L}\\p{M}]+)*");
    }
    private void preserveRegistrationFields(RedirectAttributes flash, String first, String last, String email) {
        flash.addFlashAttribute("firstName", first);
        flash.addFlashAttribute("lastName", last);
        flash.addFlashAttribute("email", email);
    }
    private void deleteQuietly(String path){if(path!=null)try{Files.deleteIfExists(Paths.get(path));}catch(IOException ignored){}}

    private static final class RegistrationDraft implements java.io.Serializable {
        final String firstName,lastName,email,passwordHash;
        String otpHash; Instant expiresAt,lastSentAt; int attempts; int resends;
        RegistrationDraft(String f,String l,String e,String p,String o,Instant x,Instant sent,int a){
            firstName=f;lastName=l;email=e;passwordHash=p;otpHash=o;expiresAt=x;lastSentAt=sent;attempts=a;resends=0;
        }
    }
}
