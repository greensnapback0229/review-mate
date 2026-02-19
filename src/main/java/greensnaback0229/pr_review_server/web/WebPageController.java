package greensnaback0229.pr_review_server.web;

import greensnaback0229.pr_review_server.auth.ApiKeyService;
import greensnaback0229.pr_review_server.auth.CustomOAuth2User;
import greensnaback0229.pr_review_server.auth.dto.ApiKeyStatusResponse;
import greensnaback0229.pr_review_server.auth.entity.User;
import greensnaback0229.pr_review_server.feature.FeatureRegistryLoader;
import greensnaback0229.pr_review_server.feature.dto.FeatureDefinition;
import greensnaback0229.pr_review_server.review.ReviewHistoryService;
import greensnaback0229.pr_review_server.review.dto.RepositoryStatsResponse;
import greensnaback0229.pr_review_server.review.dto.ReviewSummaryDto;
import greensnaback0229.pr_review_server.tenant.UserRepositoryService;
import greensnaback0229.pr_review_server.tenant.entity.UserRepository;
import greensnaback0229.pr_review_server.tenant.repository.UserRepositoryJpaRepository;
import greensnaback0229.pr_review_server.usage.UsageService;
import greensnaback0229.pr_review_server.usage.dto.UsageSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebPageController {

    private final ApiKeyService apiKeyService;
    private final ReviewHistoryService reviewHistoryService;
    private final UsageService usageService;
    private final UserRepositoryService userRepositoryService;
    private final UserRepositoryJpaRepository userRepositoryJpaRepository;
    private final FeatureRegistryLoader featureRegistryLoader;

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
        User user = principal.getUser();
        Long userId = user.getId();

        List<UserRepository> repositories = userRepositoryService.findActiveRepositoriesByUserId(userId);

        Page<ReviewSummaryDto> recentReviews = reviewHistoryService.getReviewHistory(
                userId, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));

        UsageSummary usage = usageService.getCurrentMonthUsage(userId);

        model.addAttribute("user", user);
        model.addAttribute("repositories", repositories);
        model.addAttribute("recentReviews", recentReviews.getContent());
        model.addAttribute("usage", usage);
        return "dashboard";
    }

    @GetMapping("/repositories/{repositoryId}")
    public String repositoryDetail(
            @AuthenticationPrincipal CustomOAuth2User principal,
            @PathVariable Long repositoryId,
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        Long userId = principal.getUser().getId();

        UserRepository repository = userRepositoryService.findActiveRepositoriesByUserId(userId)
                .stream()
                .filter(r -> r.getRepositoryId().equals(repositoryId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        Page<ReviewSummaryDto> reviews = reviewHistoryService.getReviewsByRepository(
                userId, repositoryId,
                PageRequest.of(page, 20, Sort.by(Sort.Direction.DESC, "createdAt")));

        RepositoryStatsResponse stats = reviewHistoryService.getRepositoryStats(userId, repositoryId);

        model.addAttribute("repository", repository);
        model.addAttribute("reviews", reviews);
        model.addAttribute("stats", stats);
        return "repositories/detail";
    }

    @GetMapping("/settings/repositories/{repositoryId}")
    public String repositorySettings(
            @AuthenticationPrincipal CustomOAuth2User principal,
            @PathVariable Long repositoryId,
            Model model) {

        Long userId = principal.getUser().getId();

        UserRepository repository = userRepositoryJpaRepository
                .findByUserIdAndRepositoryId(userId, repositoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        model.addAttribute("repository", repository);
        return "settings/repositories/detail";
    }

    @GetMapping("/settings/repositories/{repositoryId}/features")
    public String repositoryFeatures(
            @AuthenticationPrincipal CustomOAuth2User principal,
            @PathVariable Long repositoryId,
            Model model) {

        Long userId = principal.getUser().getId();

        UserRepository repository = userRepositoryJpaRepository
                .findByUserIdAndRepositoryId(userId, repositoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        Map<String, FeatureDefinition> features = Collections.emptyMap();
        String loadError = null;

        try {
            features = featureRegistryLoader.loadFromRepository(repository.getRepoFullName(), null);
        } catch (Exception e) {
            log.warn("Feature registry not found for {}: {}", repository.getRepoFullName(), e.getMessage());
            loadError = ".github/pr-review/feature-registry.yml 파일을 찾을 수 없습니다.";
        }

        model.addAttribute("repository", repository);
        model.addAttribute("features", features);
        model.addAttribute("loadError", loadError);
        return "settings/repositories/features";
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
