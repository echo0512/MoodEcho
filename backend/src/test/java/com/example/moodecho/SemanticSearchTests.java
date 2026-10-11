package com.example.moodecho;

import com.example.moodecho.entity.*;
import com.example.moodecho.mapper.*;
import com.example.moodecho.service.EmbeddingService;
import com.example.moodecho.service.impl.DiaryEmbeddingServiceImpl;
import com.example.moodecho.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SemanticSearchTests {
    @Test void lexicalCoverageWeightsRareEvidenceWithoutRewardingRepetition() {
        var scorer = new DiaryLexicalScorer("考试 焦虑", List.of("考试", "考试", "焦虑"));
        assertTrue(scorer.coverage("焦虑") > scorer.coverage("考试"));
        assertEquals(scorer.coverage("考试"), scorer.coverage("考试 考试 考试"));
        assertEquals(0, scorer.coverage("今天吃饭"));
        assertEquals(1, scorer.coverage("考试和焦虑"), 1e-12);
        assertEquals(0, new DiaryLexicalScorer("考试", List.of()).coverage("考，试"));
        assertEquals(1, new DiaryLexicalScorer("Java 项目", List.of()).coverage("JAVA项目"), 1e-12);
    }

    @Test void longDiaryAggregationNormalizesEachChunkAndRejectsInvalidDimensions() {
        List<Double> mean = VectorUtils.normalizedMean(List.of(List.of(100d, 0d), List.of(0d, 1d)));
        assertEquals(Math.sqrt(0.5), mean.get(0), 1e-12);
        assertEquals(mean.get(0), mean.get(1));
        assertThrows(IllegalArgumentException.class, () -> VectorUtils.normalizedMean(List.of(List.of(1d), List.of(1d, 2d))));
        assertThrows(IllegalArgumentException.class, () -> VectorUtils.normalizedMean(List.of(List.of(Double.NaN))));
        VectorUtils.validate(VectorUtils.normalizedMean(List.of(List.of(1d, 0d), List.of(-1d, 0d))));
    }

    @Test void strongSemanticMatchBeatsIncidentalLiteralButExactTitleStillWins() {
        Diary incidental = diary(1, "今天在书店看到一本叫焦虑的小说，没有翻开就走了。");
        Diary relevant = diary(2, "担心明天答辩时回答不上问题，紧张得翻来覆去睡不着。");
        var diaries = mock(DiaryMapper.class);
        var indexes = mock(DiaryEmbeddingMapper.class);
        var model = mock(EmbeddingService.class);
        when(model.getModelName()).thenReturn("test");
        when(model.embedText(anyString())).thenReturn(List.of(1d, 0d));
        when(diaries.selectList(any())).thenReturn(List.of(incidental, relevant));
        when(indexes.selectList(any())).thenReturn(List.of(
                index(incidental, List.of(new DiaryVectorIndex.Chunk(incidental.getContent(), List.of(0d, 1d)))),
                index(relevant, List.of(new DiaryVectorIndex.Chunk(relevant.getContent(), List.of(1d, 0d))))));
        var service = new DiaryEmbeddingServiceImpl(diaries, indexes, model, mock(PlatformTransactionManager.class));
        assertEquals(2L, service.semanticSearch(1L, "焦虑", 1).get(0).getId());
        incidental.setTitle("焦虑");
        assertEquals(1L, service.semanticSearch(1L, "焦虑", 1).get(0).getId());
    }

    @Test void fusionScoresAllCandidatesBeforeTopKAndUsesPartialQueryEvidence() {
        Diary generic = diary(1, "这一天心里有点难受，不太想说话。");
        Diary specific = diary(2, "考试之前非常焦虑，担心自己表现不好。");
        var diaries = mock(DiaryMapper.class);
        var indexes = mock(DiaryEmbeddingMapper.class);
        var model = mock(EmbeddingService.class);
        when(model.getModelName()).thenReturn("test");
        when(model.embedText(anyString())).thenReturn(List.of(1d, 0d));
        when(diaries.selectList(any())).thenReturn(List.of(generic, specific));
        when(indexes.selectList(any())).thenReturn(List.of(
                index(generic, List.of(new DiaryVectorIndex.Chunk(generic.getContent(), List.of(0.8d, 0.6d)))),
                index(specific, List.of(new DiaryVectorIndex.Chunk(specific.getContent(), List.of(0.78d, Math.sqrt(1 - 0.78 * 0.78)))))));
        var service = new DiaryEmbeddingServiceImpl(diaries, indexes, model, mock(PlatformTransactionManager.class));
        assertEquals(2L, service.semanticSearch(1L, "考试焦虑", 1).get(0).getId());
        verify(diaries, times(1)).selectList(any());
        // 词项重合本身不绕过向量召回门槛。
        when(indexes.selectList(any())).thenReturn(List.of());
        assertTrue(service.semanticSearch(1L, "考试焦虑", 1).isEmpty());
    }

    @Test void meaningfulShortDiaryNeedsHigherSimilarity() {
        Diary shortDiary = diary(1, "想家了");
        var diaries = mock(DiaryMapper.class);
        var indexes = mock(DiaryEmbeddingMapper.class);
        var model = mock(EmbeddingService.class);
        when(model.getModelName()).thenReturn("test");
        when(model.embedText(anyString())).thenReturn(List.of(1d, 0d));
        when(diaries.selectList(any())).thenReturn(List.of(shortDiary));
        var service = new DiaryEmbeddingServiceImpl(diaries, indexes, model, mock(PlatformTransactionManager.class));
        when(indexes.selectList(any())).thenReturn(List.of(index(shortDiary,
                List.of(new DiaryVectorIndex.Chunk("想家了", List.of(0.6d, 0.8d))))));
        assertTrue(service.semanticSearch(1L, "思念家人", 10).isEmpty());
        when(indexes.selectList(any())).thenReturn(List.of(index(shortDiary,
                List.of(new DiaryVectorIndex.Chunk("想家了", List.of(0.8d, 0.6d))))));
        assertEquals(1L, service.semanticSearch(1L, "思念家人", 10).get(0).getId());
    }

    @Test void longDiaryIndexesAndFindsRelatedWithoutSendingUnboundedText() {
        var diaries = mock(DiaryMapper.class);
        var indexes = mock(DiaryEmbeddingMapper.class);
        var model = mock(EmbeddingService.class, CALLS_REAL_METHODS);
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        Diary longDiary = diary(1, "很长的日记内容。".repeat(1000));
        longDiary.setTitle("很长的标题".repeat(100));
        when(diaries.selectOne(any())).thenReturn(longDiary);
        when(model.getModelName()).thenReturn("test");
        when(model.embedText(anyString())).thenAnswer(call -> {
            assertTrue(((String) call.getArgument(0)).length() <= 350);
            return List.of(1d, 0d);
        });
        var service = new DiaryEmbeddingServiceImpl(diaries, indexes, model, manager);
        service.upsertDiaryEmbedding(1L, 1L);
        var saved = org.mockito.ArgumentCaptor.forClass(DiaryEmbedding.class);
        verify(indexes).insert(saved.capture());
        var snapshot = DiaryVectorIndex.fromJson(saved.getValue().getEmbedding());
        assertEquals(List.of(1d, 0d), snapshot.whole());
        assertTrue(snapshot.chunks().size() > 20);
        assertTrue(service.findRelatedDiaries(1L, 1L, 5).isEmpty());
    }

    @Test void rejectsOversizedQueryBeforeReadingDiariesOrCallingProvider() {
        var diaries = mock(DiaryMapper.class);
        var indexes = mock(DiaryEmbeddingMapper.class);
        var model = mock(EmbeddingService.class);
        var service = new DiaryEmbeddingServiceImpl(diaries, indexes, model, mock(PlatformTransactionManager.class));
        assertThrows(IllegalArgumentException.class, () -> service.semanticSearch(1L, "长".repeat(501), 10));
        verifyNoInteractions(diaries, indexes, model);
    }

    @Test void preservesTopicsAndRemovesImages() {
        assertEquals(List.of("答辩很紧张", "和朋友吵架", "火锅很好吃"), DiaryChunkUtils.chunks(
                "<p>答辩很紧张</p><p>和朋友吵架<img src='x'></p><p>火锅很好吃</p>"));
        assertTrue(DiaryChunkUtils.chunks("<p><img src='x'></p>").isEmpty());
        assertEquals(List.of("只是一句短日记"), DiaryChunkUtils.chunks("只是一句短日记"));
    }

    @Test void longParagraphHasBoundedOverlappingChunks() {
        String text = "甲".repeat(1000);
        List<String> chunks = DiaryChunkUtils.chunks(text);
        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.length() <= 350));
        assertEquals(1000, chunks.stream().mapToInt(String::length).sum() - 50 * (chunks.size() - 1));
        assertNotEquals(DiaryChunkUtils.fingerprint("", List.of("甲", "乙")),
                DiaryChunkUtils.fingerprint("", List.of("甲 乙")));
    }

    @Test void rejectsIncompatibleAndInvalidVectors() {
        assertThrows(IllegalArgumentException.class, () -> VectorUtils.cosineSimilarity(List.of(1d, 0d), List.of(1d, 0d, 100d)));
        assertThrows(IllegalArgumentException.class, () -> VectorUtils.validate(List.of(Double.NaN)));
        assertThrows(IllegalArgumentException.class, () -> VectorUtils.validate(List.of(0d, 0d)));
        assertThrows(IllegalArgumentException.class, () -> DiaryVectorIndex.fromJson("[1,0]"));
    }

    private Diary diary(long id, String content) {
        Diary diary = new Diary();
        diary.setId(id); diary.setUserId(1L); diary.setDeleted(0); diary.setStatus(1);
        diary.setContent(content); diary.setRecordDate(LocalDate.of(2026, 5, 1));
        return diary;
    }
    private DiaryEmbedding index(Diary diary, List<DiaryVectorIndex.Chunk> chunks) {
        DiaryEmbedding index = new DiaryEmbedding();
        index.setDiaryId(diary.getId()); index.setUserId(1L); index.setModelName("test");
        var texts = new java.util.ArrayList<>(DiaryChunkUtils.chunks(diary.getContent()));
        String title = TextCleanUtils.cleanDiaryContent(diary.getTitle());
        if (!title.isBlank() && !texts.contains(title)) texts.add(0, title);
        index.setContentHash(DiaryChunkUtils.fingerprint(diary.getTitle(), texts));
        index.setEmbedding(new DiaryVectorIndex(1, 2, List.of(0d, 1d), chunks).toJson());
        return index;
    }

    @Test void retrievesMinorTopicOnceWithMatchingExcerptAndSkipsStaleIndex() {
        DiaryMapper diaries = mock(DiaryMapper.class);
        DiaryEmbeddingMapper indexes = mock(DiaryEmbeddingMapper.class);
        EmbeddingService model = mock(EmbeddingService.class, CALLS_REAL_METHODS);
        when(model.getModelName()).thenReturn("test");
        when(model.embedText(anyString())).thenReturn(List.of(1d, 0d));
        Diary diary = diary(1, "<p>答辩前感觉压力很大</p><p>和朋友的矛盾让我很难受</p><p>晚上吃饭时感觉很开心</p>");
        DiaryEmbedding index = index(diary, List.of(
                new DiaryVectorIndex.Chunk("答辩前感觉压力很大", List.of(0d, 1d)),
                new DiaryVectorIndex.Chunk("和朋友的矛盾让我很难受", List.of(1d, 0d)),
                new DiaryVectorIndex.Chunk("晚上吃饭时感觉很开心", List.of(0.8d, 0.2d))));
        when(indexes.selectList(any())).thenReturn(List.of(index));
        when(diaries.selectList(any())).thenReturn(List.of(diary));
        DiaryEmbeddingServiceImpl service = new DiaryEmbeddingServiceImpl(diaries, indexes, model, mock(PlatformTransactionManager.class));
        var results = service.semanticSearch(1L, "人际关系不顺利", 10);
        assertEquals(1, results.size());
        assertEquals("和朋友的矛盾让我很难受", results.get(0).getSummary());
        assertEquals(1d, results.get(0).getScore());
        diary.setContent("现在没有矛盾了");
        assertTrue(service.semanticSearch(1L, "人际关系不顺利", 10).isEmpty());
        diary.setContent("<p>答辩前感觉压力很大</p><p>和朋友的矛盾让我很难受</p><p>晚上吃饭时感觉很开心</p>");
        index.setModelName("old-model");
        assertTrue(service.semanticSearch(1L, "人际关系不顺利", 10).isEmpty());
    }

    @Test void ranksByRelevanceAndSkipsDifferentDimensions() {
        DiaryMapper diaries = mock(DiaryMapper.class);
        DiaryEmbeddingMapper indexes = mock(DiaryEmbeddingMapper.class);
        EmbeddingService model = mock(EmbeddingService.class, CALLS_REAL_METHODS);
        when(model.getModelName()).thenReturn("test");
        when(model.embedText(anyString())).thenReturn(List.of(1d, 0d));
        Diary older = diary(1, "目标主题");
        Diary newer = diary(2, "这段文字和目标话题有点关系");
        newer.setRecordDate(LocalDate.of(2026, 6, 1));
        Diary incompatible = diary(3, "不兼容的向量");
        DiaryEmbedding otherDimensions = index(incompatible, List.of(new DiaryVectorIndex.Chunk("不兼容的向量", List.of(1d, 0d, 100d))));
        otherDimensions.setEmbedding(new DiaryVectorIndex(1, 3, List.of(1d, 0d, 100d),
                List.of(new DiaryVectorIndex.Chunk("不兼容的向量", List.of(1d, 0d, 100d)))).toJson());
        when(indexes.selectList(any())).thenReturn(List.of(
                index(newer, List.of(new DiaryVectorIndex.Chunk("这段文字和目标话题有点关系", List.of(0.8d, 0.6d)))),
                otherDimensions,
                index(older, List.of(new DiaryVectorIndex.Chunk("目标主题", List.of(1d, 0d))))));
        when(diaries.selectList(any())).thenReturn(List.of(older, newer, incompatible));
        var service = new DiaryEmbeddingServiceImpl(diaries, indexes, model, mock(PlatformTransactionManager.class));
        assertEquals(List.of(1L, 2L), service.semanticSearch(1L, "目标主题", 10).stream().map(result -> result.getId()).toList());
        assertEquals(1, service.semanticSearch(1L, "目标主题", 1).size());
    }

    @Test void projectTitleMatchReturnsBodyInsteadOfRepeatingTitle() {
        String content = "今天回头看了一下之前写的功能，发现它已经不只是一些零散代码了。虽然还很粗糙，但它确实开始像一个真正的产品了。";
        Diary diary = diary(1, "<p>" + content + "</p>");
        diary.setTitle("项目一点点成形");
        var stored = index(diary, List.of(
                new DiaryVectorIndex.Chunk(diary.getTitle(), List.of(1d, 0d)),
                new DiaryVectorIndex.Chunk(content, List.of(0d, 1d))));
        var result = searchSingleDiary(diary, stored, "项目");
        assertEquals(content, result.getSummary());
        assertEquals(diary.getTitle(), result.getTitle());
        // 标题仍可使日记命中，即使正文向量低于检索阈值。
        assertEquals(1d, result.getScore());
    }

    @Test void titleMatchShowsMostRelevantBodyParagraphNotAlwaysFirstParagraph() {
        Diary diary = diary(1, "<p>今天去买菜</p><p>零散功能终于串成了完整的产品</p>");
        diary.setTitle("项目进展");
        var stored = index(diary, List.of(
                new DiaryVectorIndex.Chunk("项目进展", List.of(1d, 0d)),
                new DiaryVectorIndex.Chunk("今天去买菜", List.of(0d, 1d)),
                new DiaryVectorIndex.Chunk("零散功能终于串成了完整的产品", List.of(0.8d, 0.6d))));
        assertEquals("零散功能终于串成了完整的产品", searchSingleDiary(diary, stored, "项目").getSummary());
    }

    @Test void titleOnlyDiaryDoesNotInventOrRepeatBody() {
        Diary diary = diary(1, "<p><img data-image-id=\"3\"></p>");
        diary.setTitle("项目");
        var stored = index(diary, List.of(new DiaryVectorIndex.Chunk("项目", List.of(1d, 0d))));
        assertEquals("", searchSingleDiary(diary, stored, "项目").getSummary());
    }

    private com.example.moodecho.vo.SemanticDiaryResultVO searchSingleDiary(
            Diary diary, DiaryEmbedding stored, String query) {
        var diaries = mock(DiaryMapper.class);
        var indexes = mock(DiaryEmbeddingMapper.class);
        var model = mock(EmbeddingService.class);
        when(model.getModelName()).thenReturn("test");
        when(model.embedText(query)).thenReturn(List.of(1d, 0d));
        when(diaries.selectList(any())).thenReturn(List.of(diary));
        when(indexes.selectList(any())).thenReturn(List.of(stored));
        var service = new DiaryEmbeddingServiceImpl(diaries, indexes, model, mock(PlatformTransactionManager.class));
        var results = service.semanticSearch(1L, query, 10);
        assertEquals(1, results.size());
        return results.get(0);
    }

    @Test void projectSearchRejectsPlaceholderEvenWithHigherVectorScore() {
        Diary project = diary(1, "之前写的零散功能终于组成了可以使用的软件产品。");
        project.setTitle("项目一点点成形");
        Diary placeholder = diary(2, "正文");
        placeholder.setTitle("标题");
        var diaries = mock(DiaryMapper.class);
        var indexes = mock(DiaryEmbeddingMapper.class);
        var model = mock(EmbeddingService.class);
        when(model.getModelName()).thenReturn("test");
        when(model.embedText("项目")).thenReturn(List.of(1d, 0d));
        when(diaries.selectList(any())).thenReturn(List.of(project, placeholder));
        when(indexes.selectList(any())).thenReturn(List.of(
                index(project, List.of(new DiaryVectorIndex.Chunk(project.getTitle(), List.of(0.2d, 0.98d)),
                        new DiaryVectorIndex.Chunk(project.getContent(), List.of(0d, 1d)))),
                index(placeholder, List.of(new DiaryVectorIndex.Chunk("标题", List.of(1d, 0d)),
                        new DiaryVectorIndex.Chunk("正文", List.of(1d, 0d))))));
        var service = new DiaryEmbeddingServiceImpl(diaries, indexes, model, mock(PlatformTransactionManager.class));
        var results = service.semanticSearch(1L, "项目", 10);
        assertEquals(List.of(1L), results.stream().map(result -> result.getId()).toList());
        assertEquals(project.getContent(), results.get(0).getSummary());
        assertNull(results.get(0).getScore(), "文字命中不能伪装成高向量分数");
        when(diaries.selectList(any())).thenReturn(List.of(placeholder));
        assertTrue(service.semanticSearch(1L, "项目", 10).isEmpty(), "不能为了有结果而返回无信息的短文本");
    }

    @Test void semanticExpansionStillFindsContextWithoutLiteralQuery() {
        Diary diary = diary(1, "之前写的零散功能终于组成了可以使用的软件产品。");
        diary.setTitle("逐渐成形");
        var stored = index(diary, List.of(new DiaryVectorIndex.Chunk(diary.getTitle(), List.of(0d, 1d)),
                new DiaryVectorIndex.Chunk(diary.getContent(), List.of(0.8d, 0.6d))));
        assertEquals(diary.getContent(), searchSingleDiary(diary, stored, "项目").getSummary());
    }

    @Test void directMatchesWorkWithoutIndexAndDuringProviderFailure() {
        Diary project = diary(1, "今天推进了项目，完成了一个功能。");
        var diaries = mock(DiaryMapper.class);
        var indexes = mock(DiaryEmbeddingMapper.class);
        var model = mock(EmbeddingService.class);
        when(diaries.selectList(any())).thenReturn(List.of(project));
        when(model.embedText(anyString())).thenThrow(new RuntimeException("provider unavailable"));
        var service = new DiaryEmbeddingServiceImpl(diaries, indexes, model, mock(PlatformTransactionManager.class));
        assertEquals(1L, service.semanticSearch(1L, "项目", 10).get(0).getId());
        assertThrows(RuntimeException.class, () -> service.semanticSearch(1L, "朋友", 10));
    }

    @Test void directSearchDoesNotReturnDraftsDeletedOrOtherUsersDiaries() {
        Diary otherUser = diary(1, "项目");
        otherUser.setUserId(2L);
        Diary deleted = diary(2, "项目");
        deleted.setDeleted(1);
        Diary draft = diary(3, "项目");
        draft.setStatus(0);
        var diaries = mock(DiaryMapper.class);
        var indexes = mock(DiaryEmbeddingMapper.class);
        var model = mock(EmbeddingService.class);
        when(diaries.selectList(any())).thenReturn(List.of(otherUser, deleted, draft));
        var service = new DiaryEmbeddingServiceImpl(diaries, indexes, model, mock(PlatformTransactionManager.class));
        assertTrue(service.semanticSearch(1L, "项目", 10).isEmpty());
        verifyNoInteractions(indexes, model);
    }

    @Test void partialGenerationFailureNeverPublishesIndex() {
        DiaryMapper diaries = mock(DiaryMapper.class);
        DiaryEmbeddingMapper indexes = mock(DiaryEmbeddingMapper.class);
        EmbeddingService model = mock(EmbeddingService.class, CALLS_REAL_METHODS);
        when(diaries.selectOne(any())).thenReturn(diary(1, "<p>主题甲</p><p>主题乙</p>"));
        when(model.getModelName()).thenReturn("test");
        when(model.embedText(anyString())).thenReturn(List.of(1d, 0d), List.of(1d, 0d)).thenThrow(new RuntimeException("API unavailable"));
        var service = new DiaryEmbeddingServiceImpl(diaries, indexes, model, mock(PlatformTransactionManager.class));
        assertThrows(RuntimeException.class, () -> service.upsertDiaryEmbedding(1L, 1L));
        verify(indexes, never()).insert(any(DiaryEmbedding.class));
        verify(indexes, never()).updateById(any(DiaryEmbedding.class));
    }

    @Test void completeSnapshotPublishesAndObsoleteRequestIsRejected() {
        DiaryMapper diaries = mock(DiaryMapper.class);
        DiaryEmbeddingMapper indexes = mock(DiaryEmbeddingMapper.class);
        EmbeddingService model = mock(EmbeddingService.class, CALLS_REAL_METHODS);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        Diary diary = diary(1, "<p>主题甲</p><p>主题乙</p>");
        when(diaries.selectOne(any())).thenReturn(diary);
        when(model.getModelName()).thenReturn("test");
        when(model.embedText(anyString())).thenReturn(List.of(1d, 0d));
        var service = new DiaryEmbeddingServiceImpl(diaries, indexes, model, manager);
        service.upsertDiaryEmbedding(1L, 1L);
        var saved = org.mockito.ArgumentCaptor.forClass(DiaryEmbedding.class);
        verify(indexes).insert(saved.capture());
        assertEquals(2, DiaryVectorIndex.fromJson(saved.getValue().getEmbedding()).chunks().size());
        reset(indexes);
        when(diaries.selectOne(any())).thenReturn(diary, diary(1, "已经改过的内容"));
        assertThrows(IllegalStateException.class, () -> service.upsertDiaryEmbedding(1L, 1L));
        verify(indexes, never()).insert(any(DiaryEmbedding.class));
    }
}
