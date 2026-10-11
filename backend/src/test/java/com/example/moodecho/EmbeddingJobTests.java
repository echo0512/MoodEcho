package com.example.moodecho;

import com.example.moodecho.dto.CreateDiaryRequest;
import com.example.moodecho.entity.Diary;
import com.example.moodecho.entity.DiaryEmbeddingJob;
import com.example.moodecho.mapper.*;
import com.example.moodecho.service.*;
import com.example.moodecho.service.impl.DiaryServiceImpl;
import com.example.moodecho.task.DiaryEmbeddingTask;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmbeddingJobTests {
    private DiaryEmbeddingJob job(int attempts) {
        DiaryEmbeddingJob job = new DiaryEmbeddingJob();
        job.setDiaryId(10L);
        job.setUserId(2L);
        job.setRevision(3L);
        job.setAttempts(attempts);
        job.setClaimToken("claim-token");
        return job;
    }

    private DiaryEmbeddingJobService queue(DiaryEmbeddingJobMapper mapper) {
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return new DiaryEmbeddingJobService(mapper, mock(EmbeddingService.class), manager);
    }

    @Test void claimCreatesUniqueReceiptAndIncrementsAttempts() {
        var mapper = mock(DiaryEmbeddingJobMapper.class);
        var snapshot = job(1);
        when(mapper.nextJob(5)).thenReturn(snapshot);
        when(mapper.claim(eq(10L), eq(3L), anyString(), anyInt())).thenReturn(1);
        var queue = queue(mapper);
        var claimed = queue.claimNext();
        assertEquals(2, claimed.getAttempts());
        assertNotEquals("claim-token", claimed.getClaimToken());
        queue.complete(claimed);
        verify(mapper).finish(same(claimed), eq("ready"), isNull(), eq(0));
    }

    @Test void retriesBackOffAndStopAfterFiveAttempts() {
        var mapper = mock(DiaryEmbeddingJobMapper.class);
        var queue = queue(mapper);
        var first = job(1);
        var second = job(2);
        var last = job(5);
        queue.retry(first);
        queue.retry(second);
        queue.retry(last);
        verify(mapper).finish(first, "pending", "GENERATION_FAILED", 15);
        verify(mapper).finish(second, "pending", "GENERATION_FAILED", 30);
        verify(mapper).finish(last, "failed", "GENERATION_FAILED", 240);
    }

    @Test void unconfiguredWorkerLeavesPersistentTasksUntouched() {
        var queue = mock(DiaryEmbeddingJobService.class);
        var index = mock(DiaryEmbeddingService.class);
        var provider = mock(EmbeddingService.class);
        new DiaryEmbeddingTask(queue, index, provider).processNext();
        verifyNoInteractions(queue, index);
    }

    @Test void workerAcknowledgesSuccessAndRetriesFailureUsingClaimedRevision() {
        var queue = mock(DiaryEmbeddingJobService.class);
        var index = mock(DiaryEmbeddingService.class);
        var provider = mock(EmbeddingService.class);
        when(provider.isConfigured()).thenReturn(true);
        var first = job(1);
        when(queue.claimNext()).thenReturn(first);
        var worker = new DiaryEmbeddingTask(queue, index, provider);
        worker.processNext();
        verify(index).upsertDiaryEmbedding(2L, 10L);
        verify(queue).complete(first);
        verify(queue, never()).retry(any());
        reset(queue, index);
        var retry = job(2);
        when(queue.claimNext()).thenReturn(retry);
        doThrow(new RuntimeException("temporary provider failure")).when(index).upsertDiaryEmbedding(2L, 10L);
        worker.processNext();
        verify(queue).retry(retry);
        verify(queue, never()).complete(any());
    }

    @Test void savingDiaryOnlyEnqueuesAndQueueFailureIsNotSilenced() {
        var diaries = mock(DiaryMapper.class);
        var images = mock(DiaryImageService.class);
        var index = mock(DiaryEmbeddingService.class);
        var queue = mock(DiaryEmbeddingJobService.class);
        Diary saved = new Diary();
        saved.setId(10L);
        saved.setUserId(2L);
        when(diaries.selectById(10L)).thenReturn(saved);
        when(images.listDiaryImages(2L, 10L)).thenReturn(List.of());
        doAnswer(invocation -> { ((Diary) invocation.getArgument(0)).setId(10L); return 1; })
                .when(diaries).insert(any(Diary.class));
        var service = new DiaryServiceImpl(diaries, mock(DiaryImageMapper.class), images,
                index, queue, mock(ResonancePostService.class));
        var request = new CreateDiaryRequest();
        request.setContent("一篇普通的日记");
        request.setRecordDate(LocalDate.of(2026, 10, 8));
        assertEquals(10L, service.createDiary(2L, request).getId());
        verify(queue).enqueue(2L, 10L);
        verifyNoInteractions(index);
        doThrow(new RuntimeException("database failure")).when(queue).enqueue(2L, 10L);
        assertThrows(RuntimeException.class, () -> service.createDiary(2L, request));
    }
}
