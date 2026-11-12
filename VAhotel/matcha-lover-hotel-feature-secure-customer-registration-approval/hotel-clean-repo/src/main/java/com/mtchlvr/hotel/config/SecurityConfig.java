package com.mtchlvr.hotel.config;

import com.mtchlvr.hotel.repo.UserRepository;
import com.mtchlvr.hotel.service.CaptchaService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return email -> users.findByEmailIgnoreCase(email)
            .map(u -> User.withUsername(u.getEmail())
                .password(u.getPasswordHash())
                .roles(u.getRole())
                .build())
            .orElseThrow(() -> new UsernameNotFoundException("Account not found"));
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, CaptchaService captcha) throws Exception {

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/rooms/*/availability").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/chat").permitAll()
                .requestMatchers(
                    "/", "/customer-portal", "/rooms", "/register", "/register/**", "/login", "/manager-portal",
                    "/css/**", "/js/**", "/images/**", "/room-photos/**", "/error", "/favicon.ico"
                ).permitAll()
                // Online-only operations must be checked before the shared manager routes.
                .requestMatchers(
                    "/manager/reservation-requests",
                    "/manager/reservations/*/status",
                    "/manager/pencil-bookings/**"
                ).hasAnyRole("MANAGER", "ONLINE_MANAGER")

                // Walk-in booking and payment operations belong to the On-Site Manager.
                .requestMatchers("/manager/walk-ins", "/manager/walk-ins/**")
                    .hasAnyRole("MANAGER", "ONSITE_MANAGER")

                // Shared manager workspace. MANAGER remains supported for legacy accounts.
                .requestMatchers("/manager/**")
                    .hasAnyRole("MANAGER", "ONLINE_MANAGER", "ONSITE_MANAGER")
                .requestMatchers("/super-admin/**").hasRole("SUPER_ADMIN")
                .requestMatchers(
                    "/customer/**", "/reservations/**"
                ).hasRole("CUSTOMER")
                .anyRequest().authenticated()
            )
            .headers(headers -> headers.cacheControl(Customizer.withDefaults()))
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler((request, response, authentication) -> {
                    java.util.Set<String> roles = authentication.getAuthorities().stream()
                        .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                        .collect(java.util.stream.Collectors.toSet());
                    String portal = request.getParameter("portal");

                    // A selected portal is not merely a visual choice: the authenticated
                    // account must have the matching role before entering that workspace.
                    if (portal != null && !portal.isBlank()) {
                        boolean allowed = false;
                        if ("customer".equals(portal)) {
                            allowed = roles.contains("ROLE_CUSTOMER");
                        } else if ("online-manager".equals(portal)) {
                            allowed = roles.contains("ROLE_ONLINE_MANAGER") || roles.contains("ROLE_MANAGER");
                        } else if ("onsite-manager".equals(portal)) {
                            allowed = roles.contains("ROLE_ONSITE_MANAGER") || roles.contains("ROLE_MANAGER");
                        } else if ("super-admin".equals(portal)) {
                            allowed = roles.contains("ROLE_SUPER_ADMIN");
                        }

                        if (!allowed) {
                            new org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler()
                                .logout(request, response, authentication);
                            String safePortal = java.net.URLEncoder.encode(
                                portal, java.nio.charset.StandardCharsets.UTF_8);
                            response.sendRedirect(request.getContextPath()
                                + "/login?portal=" + safePortal + "&roleError");
                            return;
                        }
                    }

                    String destination;
                    if (roles.contains("ROLE_SUPER_ADMIN")) {
                        destination = "/super-admin";
                    } else if (roles.contains("ROLE_MANAGER")
                            || roles.contains("ROLE_ONLINE_MANAGER")
                            || roles.contains("ROLE_ONSITE_MANAGER")) {
                        destination = "/manager";
                    } else {
                        destination = "/customer/reservations";
                    }
                    response.sendRedirect(request.getContextPath() + destination);
                })
                .failureHandler((request, response, exception) -> {
                    String portal = request.getParameter("portal");
                    String destination = request.getContextPath() + "/login?error";
                    if (portal != null && !portal.isBlank()) {
                        destination += "&portal=" + java.net.URLEncoder.encode(
                            portal, java.nio.charset.StandardCharsets.UTF_8);
                    }
                    response.sendRedirect(destination);
                })
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .permitAll()
            )
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/chat"));

        http.addFilterBefore(
            new CaptchaLoginFilter(captcha),
            UsernamePasswordAuthenticationFilter.class
        );

        return http.build();
    }
}
