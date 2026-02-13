package greensnaback0229.pr_review_server.auth;

import greensnaback0229.pr_review_server.auth.entity.User;
import greensnaback0229.pr_review_server.auth.repository.UserJpaRepository;
import greensnaback0229.pr_review_server.comment.repository.ReviewContextJpaRepository;
import greensnaback0229.pr_review_server.feature.repository.FeatureMemoryJpaRepository;
import greensnaback0229.pr_review_server.feature.repository.RepositoryJpaRepository;
import greensnaback0229.pr_review_server.installation.InstallationHandler;
import greensnaback0229.pr_review_server.installation.entity.PendingInstallation;
import greensnaback0229.pr_review_server.installation.repository.PendingInstallationJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserJpaRepository userJpaRepository;
    private final EncryptionService encryptionService;
    private final RepositoryJpaRepository repositoryJpaRepository;
    private final FeatureMemoryJpaRepository featureMemoryJpaRepository;
    private final ReviewContextJpaRepository reviewContextJpaRepository;
    private final PendingInstallationJpaRepository pendingInstallationJpaRepository;
    private final InstallationHandler installationHandler;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String accessToken = userRequest.getAccessToken().getTokenValue();
        return processOAuth2User(oAuth2User.getAttributes(), accessToken);
    }

    @Transactional
    public CustomOAuth2User processOAuth2User(Map<String, Object> attributes, String accessToken) {
        Long githubId = ((Number) attributes.get("id")).longValue();
        String login = (String) attributes.get("login");
        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");
        String avatarUrl = (String) attributes.get("avatar_url");

        String encryptedToken = encryptionService.encrypt(accessToken);

        boolean isFirstUser = userJpaRepository.count() == 0;
        String role = isFirstUser ? "ADMIN" : "USER";

        User user = userJpaRepository.findByGithubId(githubId)
                .map(existing -> {
                    existing.setEmail(email);
                    existing.setAvatarUrl(avatarUrl);
                    existing.setGithubToken(encryptedToken);
                    return userJpaRepository.save(existing);
                })
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .githubId(githubId)
                            .githubLogin(login)
                            .email(email)
                            .name(name)
                            .avatarUrl(avatarUrl)
                            .githubToken(encryptedToken)
                            .role(role)
                            .build();
                    return userJpaRepository.save(newUser);
                });

        if (isFirstUser && user.getRole().equals("ADMIN")) {
            migrateAnonymousData(user.getId());
        }

        // F12: pending_installations → user_repositories 자동 연결
        convertPendingInstallations(githubId, user.getId());

        log.info("OAuth2 login: githubId={}, login={}, role={}", githubId, login, user.getRole());
        return new CustomOAuth2User(user, attributes);
    }

    private void convertPendingInstallations(Long githubId, Long userId) {
        List<PendingInstallation> pendings = pendingInstallationJpaRepository.findByGithubId(githubId);
        if (!pendings.isEmpty()) {
            for (PendingInstallation pending : pendings) {
                installationHandler.convertPendingToActive(userId, pending);
            }
            pendingInstallationJpaRepository.deleteAll(pendings);
            log.info("Converted {} pending installations for userId={}", pendings.size(), userId);
        }
    }

    private void migrateAnonymousData(Long userId) {
        log.info("Migrating anonymous data to userId={}", userId);
        repositoryJpaRepository.updateUserIdWhereNull(userId);
        featureMemoryJpaRepository.updateUserIdWhereNull(userId);
        reviewContextJpaRepository.updateUserIdWhereNull(userId);
    }
}
