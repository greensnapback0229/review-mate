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
import greensnaback0229.pr_review_server.feature.FeatureRegistry;
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
	private final FeatureRegistry featureRegistry;
	private final FeatureResolver featureResolver;
	private final CodeCollector codeCollector;
	private final PromptBuilder promptBuilder;
	private final LlmClient llmClient;
	private final ReviewAggregator reviewAggregator;

	/**
	 * PR 리뷰 전체 프로세스 실행
	 *
	 * @param repoFullName 저장소 풀네임 (예: owner/repo)
	 * @param prNumber PR 번호
	 * @param prTitle PR 제목
	 * @param prBody PR 본문
	 * @param baseBranch Base 브랜치명
	 * @param headBranch Head 브랜치명 (PR 브랜치)
	 * @return 최종 병합된 리뷰 결과
	 */
	public String reviewPullRequest(String repoFullName, int prNumber, String prTitle,
		String prBody, String baseBranch, String headBranch) {
		log.info("Starting PR review for {}/#{}", repoFullName, prNumber);

		try {
			// 1. Feature Registry 초기화 (PR 브랜치에서 읽기)
			featureRegistry.initialize(repoFullName, null, headBranch);

			// 2. PR 파싱
			List<String> changedFiles = codeCollector.getChangedFilePaths(repoFullName, prNumber);
			PrContext prContext = prParser.parse(prTitle, prBody, changedFiles);
			log.info("Parsed PR context: main features = {}, related features = {}",
				prContext.getMainFeatures(), prContext.getRelatedFeatures());

			// 3. 각 기능별 리뷰 수행
			List<AggregatedReview> reviews = new ArrayList<>();

			// Main features 리뷰
			for (String feature : prContext.getMainFeatures()) {
				AggregatedReview review = reviewFeature(repoFullName, prNumber, baseBranch,
					feature, prContext, changedFiles);
				if (review != null) {
					reviews.add(review);
				}
			}

			// Related features 리뷰
			for (String feature : prContext.getRelatedFeatures()) {
				AggregatedReview review = reviewFeature(repoFullName, prNumber, baseBranch,
					feature, prContext, changedFiles);
				if (review != null) {
					reviews.add(review);
				}
			}

			// 4. 리뷰 결과 병합
			String finalReview = reviewAggregator.mergeReviews(reviews);
			log.info("Completed PR review for {}/#{}", repoFullName, prNumber);

			return finalReview;

		} catch (Exception e) {
			log.error("Failed to review PR {}/{}: {}", repoFullName, prNumber, e.getMessage(), e);
			return "❌ 리뷰 중 오류가 발생했습니다: " + e.getMessage();
		}
	}

	/**
	 * 단일 기능에 대한 리뷰 수행
	 *
	 * @param repoFullName 저장소 풀네임
	 * @param prNumber PR 번호
	 * @param baseBranch Base 브랜치
	 * @param feature 기능 이름
	 * @param prContext PR 컨텍스트
	 * @param changedFiles 변경된 파일 목록
	 * @return 집계된 리뷰 결과
	 */
	private AggregatedReview reviewFeature(String repoFullName, int prNumber, String baseBranch,
		String feature, PrContext prContext, List<String> changedFiles) {
		try {
			log.info("Reviewing feature: {}", feature);

			// 1. Feature 해석
			ResolvedFeature resolvedFeature = featureResolver.resolve(feature)
				.orElse(null);

			if (resolvedFeature == null) {
				log.warn("Feature not found in registry: {}", feature);
				return null;
			}

			// 2. 관련 파일 필터링
			List<String> filteredFiles = featureResolver.filterRelatedFiles(feature, changedFiles);
			if (filteredFiles.isEmpty()) {
				log.warn("No related files found for feature: {}", feature);
				return null;
			}

			// 3. 코드 수집
			FeatureDefinition definition = resolvedFeature.getDefinition();
			List<String> coreFilePaths = definition.getCoreFiles();

			CollectedCode collectedCode = codeCollector.collectAll(
				repoFullName, prNumber, baseBranch, filteredFiles, coreFilePaths);

			// 4. CollectedCode를 Map으로 변환
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

			// 5. 프롬프트 생성
			String systemPrompt = promptBuilder.buildSystemPrompt();
			String initialPrompt = promptBuilder.buildInitialPrompt(
				resolvedFeature, changedFilesMap, coreFilesMap);

			// 6. LLM 리뷰 요청
			ReviewResponse reviewResponse = llmClient.startReview(systemPrompt, initialPrompt);

			// 7. 추가 파일 요청 처리 (필요시)
			while (reviewResponse.isNeedMoreContext()) {
				log.info("LLM requested more context: {}", reviewResponse.getRequestedFiles());

				// 추가 파일 수집
				List<String> additionalFiles = reviewResponse.getRequestedFiles();
				// TODO: 추가 파일 수집 및 2차 리뷰
				// 현재는 1차 리뷰만 수행
				break;
			}

			// 8. 리뷰 집계
			return reviewAggregator.aggregate(feature, reviewResponse);

		} catch (Exception e) {
			log.error("Failed to review feature {}: {}", feature, e.getMessage(), e);
			return null;
		}
	}
}
