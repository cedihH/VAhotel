package com.mtchlvr.hotel.web;

import com.mtchlvr.hotel.service.GroqChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final GroqChatService chatService;

    public ChatController(GroqChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse chat(
            @Valid @RequestBody ChatRequest request,
            Authentication authentication) {

        String message = request.message().trim();
        String normalized = message.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        boolean authenticated = authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName());
        // The legacy MANAGER role retains both manager permission sets.
        boolean onlineManager = authenticated && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER")
                        || a.getAuthority().equals("ROLE_ONLINE_MANAGER"));

        boolean onsiteManager = authenticated && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER")
                        || a.getAuthority().equals("ROLE_ONSITE_MANAGER"));

        boolean manager = onlineManager || onsiteManager;

        // Room browsing is public, just like the /rooms page. Keep the chatbot
        // useful to signed-out visitors without exposing account or manager data.
        if (isRoomBrowseIntent(normalized)) {
            return new ChatResponse(
                    "You can browse the hotel's public room listings here. Sign in when you are ready to make a reservation.",
                    List.of(new ChatAction("navigate", "Browse rooms", "/rooms", false)));
        }

        if (!authenticated) {
            String managerUrl = managerFunction(normalized);

            if (managerUrl != null) {
                return new ChatResponse(
                        "The manager workspace is restricted to authorized hotel managers. Please sign in with a manager account.",
                        List.of(new ChatAction("navigate", "Go to sign in", "/login", false)));
            }

            if (isLoginHelpIntent(normalized)) {
                return new ChatResponse(
                        "Of course! I'd be happy to help you sign in. Open the login page, enter your registered email and password, complete the reCAPTCHA check, then select Sign in. Never share your password in this chat.",
                        List.of(new ChatAction("navigate", "Go to login", "/login", false)));
            }

            if (isRegistrationIntent(normalized)) {
                return new ChatResponse(
                        "Of course! You can create an mtchlvr hotel account by visiting our registration page and following the registration steps.",
                        List.of(new ChatAction("navigate", "Create an account", "/register", false)));
            }

            if (isPrivateAccountIntent(normalized)) {
                return new ChatResponse(
                        "I'd be happy to help. Please sign in first so I can direct you to your private account and reservation information.",
                        List.of(new ChatAction("navigate", "Go to login", "/login", false)));
            }

            // Public guests can ask general hotel questions without signing in.
            return new ChatResponse(chatService.reply(message), List.of());
        }

        // Logout is available to both authenticated roles.
        if (containsAny(normalized, "logout", "log out", "sign out", "signout")) {
            return new ChatResponse(
                    "Would you like to log out of your mtchlvr hotel account?",
                    List.of(new ChatAction("logout", "Confirm logout", "", true)));
        }

        // Manager-only functions are identified before any customer actions.
        String managerUrl = managerFunction(normalized);

        if (managerUrl != null) {
            // Online reservation requests must not be offered to On-Site Managers.
            if (managerUrl.equals("/manager/reservation-requests") && !onlineManager) {
                return new ChatResponse(
                        "Online reservation requests are restricted to the Online Manager workspace.",
                        List.of());
            }

            if (!manager) {
                return new ChatResponse(
                        "Sorry! That function is available only to authorized hotel managers. "
                        + "I can help you browse rooms or manage your own guest account instead.",
                        List.of());
            }

            String label = switch (managerUrl) {
                case "/manager" -> "Open manager dashboard";
                case "/manager/reservation-requests" -> "Open reservation requests";
                case "/manager/room-management" -> "Open room management";
                case "/manager/history" -> "Open reservation history";
                case "/manager/reports" -> "Open guest reports";
                default -> "Open manager page";
            };

            return new ChatResponse(
                    "Certainly! You can open that manager function here:",
                    List.of(new ChatAction("navigate", label, managerUrl, false)));
        }

        // Customer-only functions must not send managers to protected customer pages.
        if (manager && containsAny(normalized,
                "my reservation", "my booking", "my bookings",
                "billing statement", "my bill", "my account", "customer profile",
                "send a report", "submit a report", "report a problem")) {
            return new ChatResponse(
                    "That is a customer account function. Your manager account has a separate management workspace.",
                    List.of());
        }

        if (!manager) {
            if (containsAny(normalized, "my reservation", "my booking",
                    "my bookings", "reservation list", "booking status",
                    "view reservation", "view booking")) {
                return new ChatResponse(
                        "You can view your reservations, their status, and available actions here.",
                        List.of(new ChatAction("navigate", "My reservations", "/customer/reservations", false)));
            }

            if (containsAny(normalized, "billing statement", "my bill",
                    "billing", "room charge", "total cost", "payment statement")) {
                return new ChatResponse(
                        "Your reservation billing statements and room charges are displayed in the Billing statement column on your My Reservations page.",
                        List.of(new ChatAction("navigate", "View billing statements", "/customer/reservations", false)));
            }

            if (containsAny(normalized, "send a report", "submit a report",
                    "report a problem", "complaint", "contact the manager",
                    "guest report")) {
                return new ChatResponse(
                        "You can submit a report and view your conversations with the hotel from My Reservations. Open the Send a report control there.",
                        List.of(new ChatAction("navigate", "Open My Reservations", "/customer/reservations", false)));
            }

            if (containsAny(normalized, "cancel reservation", "cancel booking",
                    "cancel my booking", "cancel my reservation")) {
                return new ChatResponse(
                        "You can request cancellation from My Reservations. Select the reservation and use its Request cancellation button. The request is sent to the manager for review; I cannot cancel it for you.",
                        List.of(new ChatAction("navigate", "View my reservations", "/customer/reservations", false)));
            }

            if (containsAny(normalized, "rating", "ratings", "review",
                    "reviews", "rate my stay")) {
                return new ChatResponse(
                        "I couldn't find an implemented customer ratings or reviews page in the current application. I don't want to send you to a page that doesn't exist.",
                        List.of());
            }
        }

        // Ordinary hotel questions continue through the existing Groq integration.
        return new ChatResponse(chatService.reply(message), List.of());
    }

    private static boolean isLoginHelpIntent(String message) {
        return containsAny(message, "login", "log in", "sign in", "signin",
                "forgot password", "reset password", "can't login",
                "cannot login", "can't sign in", "cannot sign in");
    }

    private static boolean isRegistrationIntent(String message) {
        return containsAny(message, "register", "registration",
                "sign up", "signup", "create account", "new account");
    }

    private static boolean isPrivateAccountIntent(String message) {
        return containsAny(message, "my reservation", "my reservations",
                "my booking", "my bookings", "booking status",
                "my account", "my profile", "cancel my reservation",
                "cancel my booking", "my bill", "my payments",
                "my payment", "my reports", "my report");
    }

    private static boolean isRoomBrowseIntent(String message) {
        return containsAny(message,
                "browse rooms", "browse the rooms", "available rooms",
                "show rooms", "show me rooms", "show me the rooms",
                "view rooms", "view the rooms", "see rooms", "see the rooms",
                "let me see the rooms", "can i see the rooms",
                "room list", "list rooms", "take me to rooms",
                "take me to the rooms", "go to rooms", "go to the rooms",
                "book a room", "reserve a room", "make a reservation");
    }

    private static String managerFunction(String message) {
        if (containsAny(message, "manager dashboard", "management dashboard",
                "manager home", "open dashboard")) {
            return "/manager";
        }

        if (containsAny(message, "reservation requests", "booking requests",
                "approve reservation", "approve booking", "reject reservation",
                "reject booking", "pending requests", "requests to review")) {
            return "/manager/reservation-requests";
        }

        if (containsAny(message, "room management", "manage rooms",
                "edit rooms", "deactivate room", "reactivate room")) {
            return "/manager/room-management";
        }

        if (containsAny(message, "reservation history", "booking history",
                "past reservations", "past bookings", "manager history")) {
            return "/manager/history";
        }

        if (containsAny(message, "all guest reports", "guest reports",
                "manager reports", "manage reports", "review reports")) {
            return "/manager/reports";
        }

        return null;
    }

    private static boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) return true;
        }
        return false;
    }

    public record ChatRequest(
            @NotBlank @Size(max = 1000) String message) {}

    public record ChatAction(
            String id, String label, String url, boolean confirmationRequired) {}

    public record ChatResponse(String reply, List<ChatAction> actions) {}
}
