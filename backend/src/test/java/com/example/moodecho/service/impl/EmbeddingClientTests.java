package com.example.moodecho.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class EmbeddingClientTests {
    private OpenAICompatibleEmbeddingService client(RestClient.Builder builder) {
        var client = new OpenAICompatibleEmbeddingService(builder.build());
        ReflectionTestUtils.setField(client, "enabled", true);
        ReflectionTestUtils.setField(client, "baseUrl", "https://embedding.test/v1");
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "model", "test-model");
        return client;
    }

    @Test void mapsOutOfOrderResponseByIndex() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = client(builder);
        server.expect(requestTo("https://embedding.test/v1/embeddings"))
                .andExpect(content().json("{\"model\":\"test-model\",\"input\":[\"甲\",\"乙\"],\"encoding_format\":\"float\"}"))
                .andRespond(withSuccess("{\"data\":[{\"index\":1,\"embedding\":[0,1]},{\"index\":0,\"embedding\":[1,0]}]}", MediaType.APPLICATION_JSON));
        assertEquals(List.of(List.of(1d, 0d), List.of(0d, 1d)), client.embedTexts(List.of("甲", "乙")));
        server.verify();
    }

    @Test void splitsRequestsAtTenInputs() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = client(builder);
        String rows = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> "{\"index\":" + i + ",\"embedding\":[1,0]}")
                .collect(java.util.stream.Collectors.joining(","));
        server.expect(requestTo("https://embedding.test/v1/embeddings"))
                .andRespond(withSuccess("{\"data\":[" + rows + "]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://embedding.test/v1/embeddings"))
                .andRespond(withSuccess("{\"data\":[{\"index\":0,\"embedding\":[1,0]}]}", MediaType.APPLICATION_JSON));
        assertEquals(11, client.embedTexts(Collections.nCopies(11, "段落")).size());
        server.verify();
    }

    @Test void rejectsMalformedVectorInsteadOfConvertingToZero() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = client(builder);
        server.expect(requestTo("https://embedding.test/v1/embeddings"))
                .andRespond(withSuccess("{\"data\":[{\"index\":0,\"embedding\":[\"oops\",1]}]}", MediaType.APPLICATION_JSON));
        assertThrows(RuntimeException.class, () -> client.embedText("段落"));
        server.verify();
    }
}
