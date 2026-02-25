package greensnaback0229.pr_review_server.installation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import greensnaback0229.pr_review_server.auth.entity.User;
import greensnaback0229.pr_review_server.auth.repository.UserJpaRepository;
import greensnaback0229.pr_review_server.installation.dto.InstallationWebhookPayload;
import greensnaback0229.pr_review_server.installation.entity.PendingInstallation;
import greensnaback0229.pr_review_server.installation.repository.PendingInstallationJpaRepository;
import greensnaback0229.pr_review_server.tenant.entity.UserRepository;
import greensnaback0229.pr_review_server.tenant.repository.UserRepositoryJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InstallationHandler {

    private final UserJpaRepository userJpaRepository;
    private final UserRepositoryJpaRepository userRepositoryJpaRepository;
    private final PendingInstallationJpaRepository pendingInstallationJpaRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void handleCreated(InstallationWebhookPayload payload) {
        Long installationId = payload.getInstallation().getId();
        List<InstallationWebhookPayload.RepositoryInfo> repositories = payload.getRepositories();

        if (repositories == null || repositories.isEmpty()) {
            log.warn("No repositories in installation created event, installationId={}", installationId);
            return;
        }

        // org 설치의 경우 account.id가 org ID이므로 sender.id(실제 설치자)를 우선 사용
        Long lookupGithubId = resolveInstallerGithubId(payload);

        Optional<User> userOpt = userJpaRepository.findByGithubId(lookupGithubId);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            connectRepositories(user.getId(), installationId, repositories);
            log.info("Connected {} repositories for userId={}, installationId={}",
                    repositories.size(), user.getId(), installationId);
        } else {
            savePending(lookupGithubId, installationId, repositories);
            log.warn("User not found for github_id={}, saved to pending_installations", lookupGithubId);
        }
    }

    @Transactional
    public void handleDeleted(InstallationWebhookPayload payload) {
        Long installationId = payload.getInstallation().getId();

        int deactivated = userRepositoryJpaRepository.deactivateByInstallationId(installationId);
        log.info("Deactivated {} repositories for installationId={}", deactivated, installationId);

        // pending도 삭제
        pendingInstallationJpaRepository.findByInstallationId(installationId)
                .ifPresent(pending -> {
                    pendingInstallationJpaRepository.delete(pending);
                    log.info("Deleted pending installation for installationId={}", installationId);
                });
    }

    @Transactional
    public void handleRepositoriesAdded(InstallationWebhookPayload payload) {
        Long installationId = payload.getInstallation().getId();
        List<InstallationWebhookPayload.RepositoryInfo> added = payload.getRepositoriesAdded();

        if (added == null || added.isEmpty()) {
            return;
        }

        // org 설치의 경우 account.id가 org ID이므로 sender.id(실제 설치자)를 우선 사용
        Long lookupGithubId = resolveInstallerGithubId(payload);

        // 1. sender.id(또는 account.id)로 직접 조회
        Optional<User> userOpt = userJpaRepository.findByGithubId(lookupGithubId);
        if (userOpt.isPresent()) {
            connectRepositories(userOpt.get().getId(), installationId, added);
            log.info("Added {} repositories for userId={}, installationId={}",
                    added.size(), userOpt.get().getId(), installationId);
            return;
        }

        // 2. installationId로 기존 user_repositories 역조회 (이미 연결된 레포가 있는 케이스)
        Optional<greensnaback0229.pr_review_server.tenant.entity.UserRepository> existingRepo =
                userRepositoryJpaRepository.findFirstByInstallationId(installationId);
        if (existingRepo.isPresent()) {
            Long userId = existingRepo.get().getUserId();
            connectRepositories(userId, installationId, added);
            log.info("Added {} repositories for userId={} via installationId={} reverse lookup (lookupId={})",
                    added.size(), userId, installationId, lookupGithubId);
            return;
        }

        // 3. 사용자 미등록 → pending 저장
        savePending(lookupGithubId, installationId, added);
        log.warn("User not found for github_id={} during repositories_added, saved to pending", lookupGithubId);
    }

    @Transactional
    public void handleRepositoriesRemoved(InstallationWebhookPayload payload) {
        Long installationId = payload.getInstallation().getId();
        List<InstallationWebhookPayload.RepositoryInfo> removed = payload.getRepositoriesRemoved();

        if (removed == null || removed.isEmpty()) {
            return;
        }

        for (InstallationWebhookPayload.RepositoryInfo repo : removed) {
            int deactivated = userRepositoryJpaRepository
                    .deactivateByRepositoryIdAndInstallationId(repo.getId(), installationId);
            log.info("Deactivated repository: repoId={}, fullName={}, installationId={}, count={}",
                    repo.getId(), repo.getFullName(), installationId, deactivated);
        }
    }

    @Transactional
    public void convertPendingToActive(Long userId, PendingInstallation pending) {
        try {
            List<InstallationWebhookPayload.RepositoryInfo> repositories = objectMapper.readValue(
                    pending.getRepositories(),
                    objectMapper.getTypeFactory().constructCollectionType(
                            List.class, InstallationWebhookPayload.RepositoryInfo.class));

            connectRepositories(userId, pending.getInstallationId(), repositories);
            log.info("Converted pending installation to active: userId={}, installationId={}, repos={}",
                    userId, pending.getInstallationId(), repositories.size());
        } catch (JsonProcessingException e) {
            log.error("Failed to parse pending repositories JSON: {}", e.getMessage(), e);
        }
    }

    /**
     * org 설치 시 account.id는 org ID이므로, sender가 존재하면 sender.id(실제 설치자)를 반환.
     * 개인 설치 또는 sender가 없으면 account.id를 반환.
     */
    private Long resolveInstallerGithubId(InstallationWebhookPayload payload) {
        InstallationWebhookPayload.Account account = payload.getInstallation().getAccount();
        InstallationWebhookPayload.Sender sender = payload.getSender();

        if ("Organization".equals(account.getType()) && sender != null && sender.getId() != null) {
            log.debug("Org installation: using sender.id={} instead of account.id={}",
                    sender.getId(), account.getId());
            return sender.getId();
        }
        return account.getId();
    }

    private void connectRepositories(Long userId, Long installationId,
                                     List<InstallationWebhookPayload.RepositoryInfo> repositories) {
        for (InstallationWebhookPayload.RepositoryInfo repo : repositories) {
            // UPSERT: 기존 연결이 있으면 재활성화, 없으면 신규 생성
            Optional<UserRepository> existingOpt = userRepositoryJpaRepository
                    .findByUserIdAndRepositoryId(userId, repo.getId());

            if (existingOpt.isPresent()) {
                UserRepository existing = existingOpt.get();
                existing.setIsActive(true);
                userRepositoryJpaRepository.save(existing);
                log.info("Reactivated repository: userId={}, repoId={}, fullName={}",
                        userId, repo.getId(), repo.getFullName());
            } else {
                UserRepository userRepo = UserRepository.builder()
                        .userId(userId)
                        .repositoryId(repo.getId())
                        .repoFullName(repo.getFullName())
                        .installationId(installationId)
                        .isActive(true)
                        .build();
                userRepositoryJpaRepository.save(userRepo);
                log.info("Connected new repository: userId={}, repoId={}, fullName={}",
                        userId, repo.getId(), repo.getFullName());
            }
        }
    }

    private void savePending(Long githubId, Long installationId,
                             List<InstallationWebhookPayload.RepositoryInfo> repositories) {
        try {
            String reposJson = objectMapper.writeValueAsString(repositories);

            // UPSERT: 기존 pending이 있으면 업데이트
            Optional<PendingInstallation> existingOpt = pendingInstallationJpaRepository
                    .findByInstallationId(installationId);

            if (existingOpt.isPresent()) {
                pendingInstallationJpaRepository.delete(existingOpt.get());
            }

            PendingInstallation pending = PendingInstallation.builder()
                    .githubId(githubId)
                    .installationId(installationId)
                    .repositories(reposJson)
                    .build();
            pendingInstallationJpaRepository.save(pending);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize repositories to JSON: {}", e.getMessage(), e);
        }
    }
}
