package greensnaback0229.pr_review_server.webhook;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import greensnaback0229.pr_review_server.aggregator.ReviewAggregator;
import greensnaback0229.pr_review_server.aggregator.dto.AggregatedReview;
import greensnaback0229.pr_review_server.collector.CodeCollector;
import greensnaback0229.pr_review_server.collector.dto.CollectedCode;
import greensnaback0229.pr_review_server.collector.dto.FileContent;
import greensnaback0229.pr_review_server.feature.FeatureRegistryLoader;
import greensnaback0229.pr_review_server.feature.FeatureResolver;
import greensnaback0229.pr_review_server.feature.dto.FeatureDefinition;
import greensnaback0229.pr_review_server.feature.dto.ResolvedFeature;
import greensnaback0229.pr_review_server.llm.LlmClient;
import greensnaback0229.pr_review_server.llm.dto.ReviewResponse;
import greensnaback0229.pr_review_server.parser.PrParser;
import greensnaback0229.pr_review_server.parser.dto.PrContext;
import greensnaback0229.pr_review_server.prompt.PromptBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * PR 리뷰 전체 워크플로우를 조율하는 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrReviewService {

	private final PrParser prParser;
	private final FeatureRegistryLoader featureRegistryLoader;
	private final FeatureResolver featureResolver;
	private final CodeCollector codeCollector;
	private final PromptBuilder promptBuilder;
	private final LlmClient llmClient;
	private final ReviewAggregator reviewAggregator;

	/**
	 * PR 리뷰 전체 프로세스 실행
	 *
	 * @param repositoryId GitHub Repository ID
	 * @param repoFullName 저장소 풀네임 (예: owner/repo)
	 * @param prNumber PR 번호
	 * @param prTitle PR 제목
	 * @param prBody PR 본문
	 * @param baseBranch Base 브랜치명
	 * @param headBranch Head 브랜치명 (PR 브랜치)
	 * @return 기능별 집계된 리뷰 결과 목록
	 */
	public List<AggregatedReview> reviewPullRequest(Long repositoryId, String repoFullName, int prNumber, String prTitle,
		String prBody, String baseBranch, String headBranch) {
		log.info("Starting PR review for {}/#{} (repositoryId={})", repoFullName, prNumber, repositoryId);

		try {
			// 1. Feature Registry 로드 (PR 브랜치에서 읽기)
			Map<String, FeatureDefinition> featureRegistry =
				featureRegistryLoader.loadFromRepository(repoFullName, null, headBranch);
			log.info("Loaded {} features from registry", featureRegistry.size());

			// 2. PR 파싱
			List<String> changedFiles = codeCollector.getChangedFilePaths(repoFullName, prNumber);
			PrContext prContext = prParser.parse(prTitle, prBody, changedFiles);
			log.info("Parsed PR context: main features = {}, related features = {}",
				prContext.getMainFeatures(), prContext.getRelatedFeatures());

			// 3. 각 기능별 리뷰 수행
			List<AggregatedReview> reviews = new ArrayList<>();

			// Main features 리뷰
			for (String feature : prContext.getMainFeatures()) {
				AggregatedReview review = reviewFeature(repositoryId, repoFullName, prNumber, baseBranch,
					headBranch, feature, prContext, changedFiles, featureRegistry);
				if (review != null) {
					reviews.add(review);
				}
			}

			// Related features 리뷰
			for (String feature : prContext.getRelatedFeatures()) {
				AggregatedReview review = reviewFeature(repositoryId, repoFullName, prNumber, baseBranch,
					headBranch, feature, prContext, changedFiles, featureRegistry);
				if (review != null) {
					reviews.add(review);
				}
			}

			log.info("Completed PR review for {}/#{} with {} feature reviews", repoFullName, prNumber, reviews.size());

			return reviews;

		} catch (Exception e) {
			log.error("Failed to review PR {}/{}: {}", repoFullName, prNumber, e.getMessage(), e);
			return List.of(); // 빈 리스트 반환
		}
	}

	/**
	 * 단일 기능에 대한 리뷰 수행
	 *
	 * @param repositoryId GitHub Repository ID
	 * @param repoFullName 저장소 풀네임
	 * @param prNumber PR 번호
	 * @param baseBranch Base 브랜치 (현재 사용하지 않음)
	 * @param headBranch Head 브랜치 (PR 브랜치 - 실제 코드 수집에 사용)
	 * @param feature 기능 이름
	 * @param prContext PR 컨텍스트
	 * @param changedFiles 변경된 파일 목록
	 * @param featureRegistry 기능 정의 Map
	 * @return 집계된 리뷰 결과
	 */
	private AggregatedReview reviewFeature(Long repositoryId, String repoFullName, int prNumber, String baseBranch,
		String headBranch, String feature, PrContext prContext, List<String> changedFiles,
		Map<String, FeatureDefinition> featureRegistry) {
		try {
			log.info("Reviewing feature: {}", feature);

			// 1. Feature 정의 조회
			FeatureDefinition definition = featureRegistry.get(feature);
			if (definition == null) {
				log.warn("Feature not found in registry: {}", feature);
				return null;
			}

			// 2. Feature Memory 조회
			ResolvedFeature resolvedFeature = featureResolver.resolve(repositoryId, feature, definition)
				.orElse(null);

			if (resolvedFeature == null) {
				log.warn("Failed to resolve feature: {}", feature);
				return null;
			}

			// 3. 관련 파일 필터링
			List<String> filteredFiles = featureResolver.filterRelatedFiles(definition, changedFiles);
			if (filteredFiles.isEmpty()) {
				log.warn("No related files found for feature: {}", feature);
				return null;
			}

			// 4. 코드 수집 (PR의 head 브랜치에서 수집)
			List<String> coreFilePaths = definition.getCoreFiles();

			log.info("Core files from feature registry: {}", coreFilePaths);
			log.info("Filtered changed files: {}", filteredFiles);

			CollectedCode collectedCode = codeCollector.collectAll(
				repoFullName, prNumber, headBranch, filteredFiles, coreFilePaths);

			// 5. CollectedCode를 Map으로 변환
			Map<String, String> changedFilesMap = collectedCode.getChangedFiles().stream()
				.collect(java.util.stream.Collectors.toMap(
					FileContent::getPath,
					FileContent::getDiff
				));

			Map<String, String> coreFilesMap = collectedCode.getCoreFiles().stream()
				.collect(java.util.stream.Collectors.toMap(
					FileContent::getPath,
					FileContent::getContent
				));

			// 6. 프롬프트 생성
			String systemPrompt = promptBuilder.buildSystemPrompt();
			String initialPrompt = promptBuilder.buildInitialPrompt(
				resolvedFeature, changedFilesMap, coreFilesMap);

			// 7. LLM 리뷰 요청
			ReviewResponse reviewResponse = llmClient.startReview(systemPrompt, initialPrompt);

			// 8. 추가 파일 요청 처리 (필요시)
			while (reviewResponse.isNeedMoreContext()) {
				log.info("LLM requested more context: {}", reviewResponse.getRequestedFiles());

				// 추가 파일 수집
				List<String> additionalFiles = reviewResponse.getRequestedFiles();
				// TODO: 추가 파일 수집 및 2차 리뷰
				// 현재는 1차 리뷰만 수행
				break;
			}

			// 9. 리뷰 집계
			return reviewAggregator.aggregate(repositoryId, feature, reviewResponse);

		} catch (Exception e) {
			log.error("Failed to review feature {}: {}", feature, e.getMessage(), e);
			return null;
		}
	}
}
