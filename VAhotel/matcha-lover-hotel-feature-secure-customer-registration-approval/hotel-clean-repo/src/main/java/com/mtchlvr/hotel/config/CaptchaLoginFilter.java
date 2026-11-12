package com.mtchlvr.hotel.config;

import com.mtchlvr.hotel.service.CaptchaService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

public class CaptchaLoginFilter extends OncePerRequestFilter {
    private final CaptchaService captcha;
    public CaptchaLoginFilter(CaptchaService captcha) { this.captcha = captcha; }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("POST".equalsIgnoreCase(request.getMethod()) && "/login".equals(request.getServletPath())) {
            if (!captcha.verify(request.getParameter("g-recaptcha-response"), request.getRemoteAddr())) {
                String portal = request.getParameter("portal");
                String destination = request.getContextPath() + "/login?captchaError";
                if (portal != null && !portal.isBlank()) {
                    destination += "&portal=" + java.net.URLEncoder.encode(
                        portal, java.nio.charset.StandardCharsets.UTF_8);
                }
                response.sendRedirect(destination);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
