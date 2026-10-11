package com.example.moodecho.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.moodecho.entity.Diary;
import com.example.moodecho.entity.DiaryEmbedding;
import com.example.moodecho.mapper.DiaryEmbeddingMapper;
import com.example.moodecho.mapper.DiaryMapper;
import com.example.moodecho.service.DiaryEmbeddingService;
import com.example.moodecho.service.EmbeddingService;
import com.example.moodecho.util.DiaryChunkUtils;
import com.example.moodecho.util.DiaryVectorIndex;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.example.moodecho.util.TextCleanUtils;
import com.example.moodecho.util.VectorUtils;
import com.example.moodecho.vo.SemanticDiaryResultVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 日记语义向量业务实现。
 */
@Service
public class DiaryEmbeddingServiceImpl implements DiaryEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(DiaryEmbeddingServiceImpl.class);
    private static final int DEFAULT_SEARCH_LIMIT = 10;
    private static final int DEFAULT_RELATED_LIMIT = 5;
    private static final int MAX_LIMIT = 20;
    @org.springframework.beans.factory.annotation.Value("${moodecho.embedding.min-similarity:0.45}")
    private double minSimilarityScore = 0.45D;
    // 极短片段缺少语义上下文。允许文字直接命中，但不单独作为语义扩展的依据。
    @org.springframework.beans.factory.annotation.Value("${moodecho.embedding.min-semantic-text-length:6}")
    private int minSemanticTextLength = 6;
    @org.springframework.beans.factory.annotation.Value("${moodecho.embedding.short-text-min-similarity:0.65}")
    private double shortTextMinSimilarity = 0.65D;
    @org.springframework.beans.factory.annotation.Value("${moodecho.embedding.lexical-boost:0.12}")
    private double lexicalBoost = 0.12D;
    private static final int MAX_WHOLE_TEXT_LENGTH = 2000;

    private final DiaryMapper diaryMapper;
    private final DiaryEmbeddingMapper diaryEmbeddingMapper;
    private final EmbeddingService embeddingService;
    private final TransactionTemplate transactions;
    public DiaryEmbeddingServiceImpl(
            DiaryMapper diaryMapper,
            DiaryEmbeddingMapper diaryEmbeddingMapper,
            EmbeddingService embeddingService,
            PlatformTransactionManager transactionManager
    ) {
        this.diaryMapper = diaryMapper;
        this.diaryEmbeddingMapper = diaryEmbeddingMapper;
        this.embeddingService = embeddingService;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public void upsertDiaryEmbedding(Long userId, Long diaryId) {
        Diary diary = getOwnActiveFormalDiary(userId, diaryId);
        if (diary == null) {
            deleteDiaryEmbeddingByDiaryId(diaryId);
            return;
        }

        String cleanContent = buildEmbeddingText(diary);
        if (cleanContent.isBlank()) {
            deleteDiaryEmbeddingByDiaryId(diaryId);
            return;
        }

        List<String> chunks = chunksFor(diary);
        String contentHash = DiaryChunkUtils.fingerprint(diary.getTitle(), chunks);
        DiaryEmbedding existing = selectByDiaryId(diaryId);
        String modelName = embeddingService.getModelName();
        if (existing != null && contentHash.equals(existing.getContentHash())
                && modelName.equals(existing.getModelName())) {
            try { DiaryVectorIndex.fromJson(existing.getEmbedding()); return; }
            catch (RuntimeException ignored) { /* Repair invalid indexes too. */ }
        }

        boolean aggregateWhole = cleanContent.length() > MAX_WHOLE_TEXT_LENGTH;
        boolean reuseWhole = chunks.size() == 1 && chunks.get(0).equals(cleanContent);
        ArrayList<String> inputs = new ArrayList<>();
        if (!aggregateWhole) inputs.add(cleanContent);
        if (!reuseWhole) inputs.addAll(chunks);
        List<List<Double>> generated = embeddingService.embedTexts(inputs);
        if (generated == null || generated.size() != inputs.size()) throw new IllegalArgumentException("向量数量不匹配");
        List<Double> whole = aggregateWhole ? VectorUtils.normalizedMean(generated) : generated.get(0);
        VectorUtils.validate(whole);
        ArrayList<DiaryVectorIndex.Chunk> vectors = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            List<Double> vector = reuseWhole ? whole : generated.get(i + (aggregateWhole ? 0 : 1));
            VectorUtils.validate(vector);
            if (vector.size() != whole.size()) throw new IllegalArgumentException("向量维度不匹配");
            vectors.add(new DiaryVectorIndex.Chunk(chunks.get(i), vector));
        }
        String payload = new DiaryVectorIndex(DiaryChunkUtils.VERSION, whole.size(), whole, vectors).toJson();
        // Publish a complete snapshot under the diary lock. A slower old request cannot overwrite new content.
        boolean published = Boolean.TRUE.equals(transactions.execute(status -> {
            LambdaQueryWrapper<Diary> lockedQuery = new LambdaQueryWrapper<>();
            lockedQuery.eq(Diary::getId, diaryId).eq(Diary::getUserId, userId).last("FOR UPDATE");
            Diary latest = diaryMapper.selectOne(lockedQuery);
            if (latest == null || !Integer.valueOf(0).equals(latest.getDeleted())
                    || !Integer.valueOf(1).equals(latest.getStatus())
                    || !contentHash.equals(DiaryChunkUtils.fingerprint(latest.getTitle(), chunksFor(latest)))) {
                return false;
            }
            DiaryEmbedding embedding = selectByDiaryId(diaryId);
            if (embedding == null) embedding = new DiaryEmbedding();
            embedding.setDiaryId(diaryId);
            embedding.setUserId(userId);
            embedding.setEmbedding(payload);
            embedding.setModelName(modelName);
            embedding.setContentHash(contentHash);
            if (embedding.getId() == null) diaryEmbeddingMapper.insert(embedding);
            else diaryEmbeddingMapper.updateById(embedding);
            return true;
        }));
        if (!published) throw new IllegalStateException("日记内容已变化，请重新生成索引");
    }

    @Override
    public void deleteDiaryEmbeddingByDiaryId(Long diaryId) {
        if (diaryId == null) {
            return;
        }

        LambdaQueryWrapper<DiaryEmbedding> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DiaryEmbedding::getDiaryId, diaryId);
        diaryEmbeddingMapper.delete(queryWrapper);
    }

    @Override
    public List<SemanticDiaryResultVO> semanticSearch(Long userId, String query, Integer limit) {
        String cleanQuery = TextCleanUtils.cleanDiaryContent(query);
        if (cleanQuery.length() > 500) throw new IllegalArgumentException("搜索内容不能超过500个字符");
        if (cleanQuery.isBlank()) {
            throw new RuntimeException("请输入搜索内容");
        }

        int resultLimit = normalizeLimit(limit, DEFAULT_SEARCH_LIMIT);
        LambdaQueryWrapper<Diary> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Diary::getUserId, userId).eq(Diary::getDeleted, 0).eq(Diary::getStatus, 1);
        Map<Long, Diary> diaries = diaryMapper.selectList(queryWrapper).stream()
                .filter(diary -> Objects.equals(userId, diary.getUserId())
                        && Integer.valueOf(0).equals(diary.getDeleted())
                        && Integer.valueOf(1).equals(diary.getStatus()))
                .collect(Collectors.toMap(Diary::getId, Function.identity()));
        if (diaries.isEmpty()) return List.of();

        String literalQuery = cleanQuery.toLowerCase(Locale.ROOT);
        Map<Long, Integer> literalRanks = new LinkedHashMap<>();
        Map<Long, SemanticDiaryResultVO> results = new LinkedHashMap<>();
        for (Diary diary : diaries.values()) {
            String title = searchableText(diary.getTitle());
            String body = searchableText(diary.getContent());
            int rank = title.equals(literalQuery) ? 3 : title.contains(literalQuery) ? 2
                    : body.contains(literalQuery) ? 1 : 0;
            if (rank == 0) continue;
            literalRanks.put(diary.getId(), rank);
            SemanticDiaryResultVO result = new SemanticDiaryResultVO();
            result.setId(diary.getId());
            result.setTitle(diary.getTitle());
            result.setRecordDate(diary.getRecordDate());
            result.setSummary(literalSummary(diary.getContent(), literalQuery));
            // 仅文字命中时没有余弦分数，不伪造为100%相似。
            results.put(diary.getId(), result);
        }

        try {
            List<Double> vector = embeddingService.embedText(cleanQuery);
            for (SemanticDiaryResultVO semantic : searchByVector(userId, vector, null, Integer.MAX_VALUE, diaries)) {
                SemanticDiaryResultVO literal = results.get(semantic.getId());
                if (literal != null && searchableText(diaries.get(semantic.getId()).getContent()).contains(literalQuery)) {
                    // 正文明文命中时保留包含搜索词的摘要。
                    literal.setScore(semantic.getScore());
                } else {
                    results.put(semantic.getId(), semantic);
                }
            }
        } catch (RuntimeException e) {
            if (results.isEmpty()) throw e;
            log.warn("语义扩展不可用，返回文字匹配结果，type={}", e.getClass().getSimpleName());
        }

        var lexical = new com.example.moodecho.util.DiaryLexicalScorer(cleanQuery,
                diaries.values().stream().map(d -> searchableText(d.getTitle()) + " " + searchableText(d.getContent())).toList());
        Map<Long, Double> rankingScores = new java.util.HashMap<>();
        for (SemanticDiaryResultVO result : results.values()) {
            int rank = literalRanks.getOrDefault(result.getId(), 0);
            Diary diary = diaries.get(result.getId());
            double coverage = lexical.coverage(searchableText(diary.getTitle()) + " " + searchableText(diary.getContent()));
            double semantic = result.getScore() == null ? minSimilarityScore : result.getScore();
            // 精确标题是明确的导航意图。其他文字命中只加分，不压过强语义匹配。
            rankingScores.put(result.getId(), rank == 3 ? 2D
                    : semantic + lexicalBoost * (0.75 * coverage + (rank == 2 ? 0.25 : 0)));
        }
        List<SemanticDiaryResultVO> ranked = results.values().stream()
                .sorted(Comparator.<SemanticDiaryResultVO>comparingDouble(result -> rankingScores.get(result.getId())).reversed()
                        .thenComparing(SemanticDiaryResultVO::getScore, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(SemanticDiaryResultVO::getRecordDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(SemanticDiaryResultVO::getId, Comparator.reverseOrder()))
                .toList();
        return ranked.stream().limit(resultLimit).toList();
    }

    private String searchableText(String content) {
        return TextCleanUtils.cleanDiaryContent(content).toLowerCase(Locale.ROOT);
    }

    private String literalSummary(String content, String query) {
        String text = TextCleanUtils.cleanDiaryContent(content);
        var matcher = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(query),
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE).matcher(text);
        int match = matcher.find() ? matcher.start() : 0;
        int start = Math.max(0, match - 40);
        if (start > 0 && Character.isLowSurrogate(text.charAt(start))) start--;
        return (start > 0 ? "…" : "") + TextCleanUtils.buildSummary(text.substring(start), 180);
    }

    private boolean isSemanticMatch(String text, double score) {
        long length = text.codePoints().filter(Character::isLetterOrDigit).count();
        // 短日记仍可表达完整意思，用更严格的阈值召回；少于3字仍只允许文字匹配。
        if (length < 3) return false;
        double threshold = length < minSemanticTextLength
                ? Math.max(minSimilarityScore, shortTextMinSimilarity) : minSimilarityScore;
        return score >= threshold;
    }

    @Override
    public List<SemanticDiaryResultVO> findRelatedDiaries(Long userId, Long diaryId, Integer limit) {
        Diary diary = getOwnActiveFormalDiary(userId, diaryId);
        if (diary == null) {
            throw new RuntimeException("日记不存在或无权访问");
        }

        DiaryEmbedding embedding = selectByDiaryId(diaryId);
        List<Double> vector = null;
        if (isCurrent(embedding, diary)) {
            try { vector = DiaryVectorIndex.fromJson(embedding.getEmbedding()).whole(); }
            catch (RuntimeException ignored) { /* Fall back to current text. */ }
        }
        if (vector == null) {
            String content = buildEmbeddingText(diary);
            if (content.isBlank()) return List.of();
            vector = content.length() > MAX_WHOLE_TEXT_LENGTH
                    ? VectorUtils.normalizedMean(embeddingService.embedTexts(chunksFor(diary)))
                    : embeddingService.embedText(content);
        }
        return searchByVector(userId, vector, diaryId, normalizeLimit(limit, DEFAULT_RELATED_LIMIT), null);
    }

    private List<SemanticDiaryResultVO> searchByVector(
            Long userId,
            List<Double> queryVector,
            Long excludeDiaryId,
            int limit,
            Map<Long, Diary> loadedDiaries
    ) {
        LambdaQueryWrapper<DiaryEmbedding> embeddingQuery = new LambdaQueryWrapper<>();
        VectorUtils.validate(queryVector);
        embeddingQuery.eq(DiaryEmbedding::getUserId, userId)
                .eq(DiaryEmbedding::getModelName, embeddingService.getModelName());
        if (excludeDiaryId != null) {
            embeddingQuery.ne(DiaryEmbedding::getDiaryId, excludeDiaryId);
        }

        List<DiaryEmbedding> embeddings = diaryEmbeddingMapper.selectList(embeddingQuery);
        if (embeddings.isEmpty()) {
            return List.of();
        }

        List<Long> diaryIds = embeddings.stream()
                .map(DiaryEmbedding::getDiaryId)
                .filter(Objects::nonNull)
                .toList();

        if (diaryIds.isEmpty()) {
            return List.of();
        }

        LambdaQueryWrapper<Diary> diaryQuery = new LambdaQueryWrapper<>();
        diaryQuery.eq(Diary::getUserId, userId)
                .eq(Diary::getDeleted, 0)
                .eq(Diary::getStatus, 1)
                .in(Diary::getId, diaryIds);

        Map<Long, Diary> diaryMap = loadedDiaries != null ? loadedDiaries : diaryMapper.selectList(diaryQuery)
                .stream()
                .collect(Collectors.toMap(Diary::getId, Function.identity()));

        return embeddings.stream()
                .map(embedding -> toResultVO(embedding, diaryMap.get(embedding.getDiaryId()), queryVector, excludeDiaryId != null))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(SemanticDiaryResultVO::getScore).reversed())
                .limit(limit)
                .toList();
    }

    private SemanticDiaryResultVO toResultVO(
            DiaryEmbedding embedding,
            Diary diary,
            List<Double> queryVector,
            boolean wholeDiary
    ) {
        if (!isCurrent(embedding, diary)) return null;
        try {
            DiaryVectorIndex index = DiaryVectorIndex.fromJson(embedding.getEmbedding());
            double score = Double.NEGATIVE_INFINITY;
            double bodyScore = -1;
            // 标题参与召回和排序，摘要则只取正文。兼容现有索引，无需重新生成向量。
            var bodyChunks = new HashSet<>(DiaryChunkUtils.chunks(diary.getContent()));
            String matchedText = TextCleanUtils.cleanDiaryContent(diary.getContent());
            for (DiaryVectorIndex.Chunk chunk : index.chunks()) {
                double candidate = VectorUtils.cosineSimilarity(queryVector, chunk.vector());
                if (isSemanticMatch(chunk.text(), candidate)) score = Math.max(score, candidate);
                if (bodyChunks.contains(chunk.text()) && candidate > bodyScore) {
                    bodyScore = candidate;
                    matchedText = chunk.text();
                }
            }
            if (wholeDiary) {
                score = VectorUtils.cosineSimilarity(queryVector, index.whole());
                matchedText = TextCleanUtils.cleanDiaryContent(diary.getContent());
            }
            if (score < minSimilarityScore) return null;

            SemanticDiaryResultVO vo = new SemanticDiaryResultVO();
            vo.setId(diary.getId());
            vo.setTitle(diary.getTitle());
            vo.setSummary(TextCleanUtils.buildSummary(matchedText, 180));
            vo.setRecordDate(diary.getRecordDate());
            vo.setScore(VectorUtils.roundScore(score));
            return vo;
        } catch (RuntimeException e) {
            log.warn("跳过无法解析的日记向量，diaryId={}", embedding.getDiaryId());
            return null;
        }
    }

    private Diary getOwnActiveFormalDiary(Long userId, Long diaryId) {
        if (userId == null || diaryId == null) {
            return null;
        }

        LambdaQueryWrapper<Diary> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Diary::getId, diaryId)
                .eq(Diary::getUserId, userId)
                .eq(Diary::getDeleted, 0)
                .eq(Diary::getStatus, 1);

        return diaryMapper.selectOne(queryWrapper);
    }

    private DiaryEmbedding selectByDiaryId(Long diaryId) {
        LambdaQueryWrapper<DiaryEmbedding> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DiaryEmbedding::getDiaryId, diaryId);
        return diaryEmbeddingMapper.selectOne(queryWrapper);
    }

    private List<String> chunksFor(Diary diary) {
        ArrayList<String> chunks = new ArrayList<>(DiaryChunkUtils.chunks(diary.getContent()));
        List<String> titleChunks = DiaryChunkUtils.chunks(diary.getTitle()).stream()
                .filter(chunk -> !chunks.contains(chunk)).distinct().toList();
        chunks.addAll(0, titleChunks);
        return List.copyOf(chunks);
    }

    private boolean isCurrent(DiaryEmbedding embedding, Diary diary) {
        return embedding != null && diary != null
                && embeddingService.getModelName().equals(embedding.getModelName())
                && DiaryChunkUtils.fingerprint(diary.getTitle(), chunksFor(diary)).equals(embedding.getContentHash());
    }

    private String buildEmbeddingText(Diary diary) {
        String title = TextCleanUtils.cleanDiaryContent(diary.getTitle());
        String content = TextCleanUtils.cleanDiaryContent(diary.getContent());

        if (title.isBlank()) {
            return content;
        }

        if (content.isBlank()) {
            return title;
        }

        return title + "\n" + content;
    }

    private int normalizeLimit(Integer limit, int defaultLimit) {
        if (limit == null || limit <= 0) {
            return defaultLimit;
        }

        return Math.min(limit, MAX_LIMIT);
    }
}
