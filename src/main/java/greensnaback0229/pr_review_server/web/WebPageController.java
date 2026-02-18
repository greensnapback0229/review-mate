package greensnaback0229.pr_review_server.web;

import greensnaback0229.pr_review_server.auth.ApiKeyService;
import greensnaback0229.pr_review_server.auth.CustomOAuth2User;
import greensnaback0229.pr_review_server.auth.dto.ApiKeyStatusResponse;
import greensnaback0229.pr_review_server.auth.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class WebPageController {

    private final ApiKeyService apiKeyService;

    @GetMapping("/")
    public String index(@AuthenticationPrincipal CustomOAuth2User principal) {
        if (principal != null) {
            return "redirect:/dashboard";
        }
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal CustomOAuth2User principal, Model model) {
        model.addAttribute("user", principal.getUser());
        return "dashboard";
    }

    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal CustomOAuth2User principal, Model model) {
        User user = principal.getUser();
        ApiKeyStatusResponse apiKeyStatus = apiKeyService.getApiKeyStatus(user.getId());

        model.addAttribute("user", user);
        model.addAttribute("apiKeyStatus", apiKeyStatus);
        return "profile";
    }

    @GetMapping("/settings/api-key")
    public String apiKeySettings(@AuthenticationPrincipal CustomOAuth2User principal, Model model) {
        User user = principal.getUser();
        ApiKeyStatusResponse apiKeyStatus = apiKeyService.getApiKeyStatus(user.getId());

        model.addAttribute("user", user);
        model.addAttribute("apiKeyStatus", apiKeyStatus);
        return "settings/api-key";
    }
}
