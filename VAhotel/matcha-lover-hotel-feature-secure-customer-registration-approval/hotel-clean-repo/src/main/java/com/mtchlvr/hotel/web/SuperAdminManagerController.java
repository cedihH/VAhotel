package com.mtchlvr.hotel.web;

import com.mtchlvr.hotel.model.AppUser;
import com.mtchlvr.hotel.repo.UserRepository;
import com.mtchlvr.hotel.service.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;
import java.util.Set;

@Controller
public class SuperAdminManagerController {
    private static final Set<String> MANAGER_ROLES = Set.of("MANAGER", "ONLINE_MANAGER", "ONSITE_MANAGER");
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final PasswordPolicy passwordPolicy;

    public SuperAdminManagerController(UserRepository users, PasswordEncoder encoder, PasswordPolicy passwordPolicy) {
        this.users = users;
        this.encoder = encoder;
        this.passwordPolicy = passwordPolicy;
    }

    public static class ManagerForm {
        @NotBlank @Email @Size(max = 160) public String email;
        @NotBlank @Size(max = 120) public String fullName;
        @NotBlank public String role = "ONLINE_MANAGER";
        public String password;
    }

    @GetMapping("/super-admin/managers")
    public String list(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "all") String role, Model model) {
        String term = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        model.addAttribute("q", q);
        model.addAttribute("selectedRole", role);
        model.addAttribute("managers", users.findAll().stream()
                .filter(u -> MANAGER_ROLES.contains(u.getRole()))
                .filter(u -> term.isBlank() || u.getEmail().toLowerCase(Locale.ROOT).contains(term) || u.getFullName().toLowerCase(Locale.ROOT).contains(term))
                .filter(u -> "all".equals(role) || u.getRole().equals(role))
                .toList());
        return "super-admin-managers";
    }

    @GetMapping("/super-admin/managers/new")
    public String createForm(Model model) { model.addAttribute("manager", new ManagerForm()); model.addAttribute("passwordPolicy", passwordPolicy); return "super-admin-manager-form"; }

    @PostMapping("/super-admin/managers")
    public String create(@Valid @ModelAttribute("manager") ManagerForm form, BindingResult errors, Model model, RedirectAttributes flash) {
        validate(form, errors, true);
        if (errors.hasErrors()) { model.addAttribute("passwordPolicy", passwordPolicy); return "super-admin-manager-form"; }
        String email = form.email.trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            errors.rejectValue("email", "duplicate", "An account with this email already exists.");
            model.addAttribute("passwordPolicy", passwordPolicy);
            return "super-admin-manager-form";
        }
        users.save(new AppUser(email, encoder.encode(form.password), form.fullName.trim(), form.role, true));
        flash.addFlashAttribute("success", "Manager account created.");
        return "redirect:/super-admin/managers";
    }

    @GetMapping("/super-admin/managers/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes flash) {
        AppUser user = findManager(id);
        if (user == null) { flash.addFlashAttribute("error", "Manager account not found."); return "redirect:/super-admin/managers"; }
        ManagerForm form = new ManagerForm(); form.email = user.getEmail(); form.fullName = user.getFullName(); form.role = user.getRole();
        model.addAttribute("manager", form); model.addAttribute("managerId", id);
        model.addAttribute("passwordPolicy", passwordPolicy);
        return "super-admin-manager-form";
    }

    @PostMapping("/super-admin/managers/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("manager") ManagerForm form, BindingResult errors, Model model, RedirectAttributes flash) {
        AppUser user = findManager(id);
        if (user == null) { flash.addFlashAttribute("error", "Manager account not found."); return "redirect:/super-admin/managers"; }
        if (form.email == null || !form.email.equalsIgnoreCase(user.getEmail())) errors.rejectValue("email", "immutable", "Email address cannot be changed here.");
        if (form.fullName == null || form.fullName.isBlank()) errors.rejectValue("fullName", "blank", "Name is required.");
        if (!MANAGER_ROLES.contains(form.role)) errors.rejectValue("role", "invalid", "Choose a valid manager role.");
        if (form.password != null && !form.password.isBlank() && !passwordPolicy.isValid(form.password)) errors.rejectValue("password", "weak", "Password does not meet the configured password policy.");
        if (errors.hasErrors()) { model.addAttribute("managerId", id); model.addAttribute("passwordPolicy", passwordPolicy); form.email = user.getEmail(); return "super-admin-manager-form"; }
        user.setFullName(form.fullName.trim()); user.setRole(form.role);
        if (form.password != null && !form.password.isBlank()) user.setPasswordHash(encoder.encode(form.password));
        users.save(user); flash.addFlashAttribute("success", "Manager account updated.");
        return "redirect:/super-admin/managers";
    }

    @PostMapping("/super-admin/managers/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes flash) {
        AppUser user = findManager(id);
        if (user == null) flash.addFlashAttribute("error", "Manager account not found.");
        else { users.delete(user); flash.addFlashAttribute("success", "Manager account deleted."); }
        return "redirect:/super-admin/managers";
    }

    private AppUser findManager(Long id) { return users.findById(id).filter(u -> MANAGER_ROLES.contains(u.getRole())).orElse(null); }
    private void validate(ManagerForm form, BindingResult errors, boolean requirePassword) {
        if (!MANAGER_ROLES.contains(form.role)) errors.rejectValue("role", "invalid", "Choose a valid manager role.");
        if (requirePassword && !passwordPolicy.isValid(form.password)) errors.rejectValue("password", "weak", "Password does not meet the configured password policy.");
    }
}
