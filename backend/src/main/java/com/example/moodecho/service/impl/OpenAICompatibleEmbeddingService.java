package com.example.moodecho.service.impl;

import com.example.moodecho.service.EmbeddingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import com.example.moodecho.util.VectorUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible embeddings API 客户端，默认用于阿里云百炼 DashScope 兼容模式。
 */
@Service
public class OpenAICompatibleEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(OpenAICompatibleEmbeddingService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient;

    public OpenAICompatibleEmbeddingService() { this(createClient()); }

    OpenAICompatibleEmbeddingService(RestClient restClient) { this.restClient = restClient; }

    private static RestClient createClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return RestClient.builder().requestFactory(factory).build();
    }

    @Value("${moodecho.embedding.enabled:true}")
    private boolean enabled;

    @Value("${moodecho.embedding.base-url:}")
    private String baseUrl;

    @Value("${moodecho.embedding.api-key:}")
    private String apiKey;

    @Value("${moodecho.embedding.model:text-embedding-v4}")
    private String model;

    @Override
    public List<Double> embedText(String text) {
        return embedTexts(List.of(text)).get(0);
    }

    @Override
    public List<List<Double>> embedTexts(List<String> texts) {
        List<List<Double>> result = new ArrayList<>();
        // DashScope v4 accepts at most ten inputs per call.
        for (int offset = 0; offset < texts.size(); offset += 10) {
            result.addAll(embedBatch(texts.subList(offset, Math.min(offset + 10, texts.size()))));
        }
        return result;
    }

    private List<List<Double>> embedBatch(List<String> texts) {
        if (!isConfigured()) {
            throw new RuntimeException("语义搜索服务暂未配置");
        }

        try {
            String responseBody = restClient.post()
                    .uri(buildEmbeddingUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(apiKey.trim()))
                    .body(Map.of(
                            "model", model,
                            "input", texts,
                            "encoding_format", "float"
                    ))
                    .retrieve()
                    .body(String.class);

            JsonNode response = objectMapper.readTree(responseBody);

            JsonNode data = response == null ? null : response.path("data");
            if (data == null || !data.isArray() || data.size() != texts.size())
                throw new IllegalArgumentException("向量数量不匹配");
            List<List<Double>> ordered = new ArrayList<>(java.util.Collections.nCopies(texts.size(), null));
            for (JsonNode row : data) {
                JsonNode indexNode = row.path("index");
                if (!indexNode.isIntegralNumber()) throw new IllegalArgumentException("向量序号无效");
                int index = indexNode.asInt(-1);
                if (index < 0 || index >= texts.size() || ordered.get(index) != null)
                    throw new IllegalArgumentException("向量序号无效");
                JsonNode values = row.path("embedding");
                if (!values.isArray()) throw new IllegalArgumentException("向量格式无效");
                List<Double> vector = new ArrayList<>();
                for (JsonNode item : values) {
                    if (!item.isNumber()) throw new IllegalArgumentException("向量包含非数字值");
                    vector.add(item.asDouble());
                }
                VectorUtils.validate(vector);
                ordered.set(index, vector);
            }
            return ordered;
        } catch (RestClientResponseException e) {
            log.warn(
                    "语义搜索服务调用失败，status={}，model={}，baseUrl={}",
                    e.getStatusCode(),
                    model,
                    maskBaseUrl()
            );
            throw new RuntimeException("语义搜索服务暂时不可用", e);
        } catch (JsonProcessingException e) {
            log.warn("语义搜索服务响应解析失败，model={}，baseUrl={}", model, maskBaseUrl());
            throw new RuntimeException("语义搜索服务暂时不可用", e);
        } catch (RuntimeException e) {
            if ("语义搜索服务暂未配置".equals(e.getMessage())) {
                throw e;
            }

            log.warn(
                    "语义搜索服务处理失败，type={}，message={}",
                    e.getClass().getSimpleName(),
                    e.getMessage()
            );
            throw new RuntimeException("语义搜索服务暂时不可用", e);
        }
    }

    @Override
    public String getModelName() {
        return model;
    }

    @Override
    public boolean isConfigured() {
        return enabled
                && StringUtils.hasText(baseUrl)
                && StringUtils.hasText(apiKey)
                && StringUtils.hasText(model);
    }

    private String buildEmbeddingUrl() {
        String trimmedBaseUrl = baseUrl.trim();
        if (trimmedBaseUrl.endsWith("/")) {
            trimmedBaseUrl = trimmedBaseUrl.substring(0, trimmedBaseUrl.length() - 1);
        }

        return trimmedBaseUrl + "/embeddings";
    }

    private String maskBaseUrl() {
        if (!StringUtils.hasText(baseUrl)) {
            return "";
        }

        return baseUrl.trim();
    }
}
