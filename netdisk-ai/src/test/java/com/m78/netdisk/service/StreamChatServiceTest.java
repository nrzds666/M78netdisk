package com.m78.netdisk.service;

import com.m78.netdisk.common.client.RagClient;
import com.m78.netdisk.common.config.RagServiceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StreamChatServiceTest {

    @Mock
    private RagClient ragClient;
    @Mock
    private ChatClient.Builder chatClientBuilder;
    @Mock
    private ChatClient chatClient;
    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock
    private ChatClient.StreamResponseSpec streamSpec;
    @Mock
    private RagServiceProperties properties;
    @Mock
    private AiDocumentService aiDocumentService;

    private StreamChatService service;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(properties.getTopK()).thenReturn(3);
        service = new StreamChatService(chatClientBuilder, ragClient, properties, aiDocumentService);

        lenient().when(chatClient.prompt()).thenReturn(requestSpec);
        lenient().when(requestSpec.user(anyString())).thenReturn(requestSpec);
        lenient().when(requestSpec.stream()).thenReturn(streamSpec);
    }

    @Test
    void streamChat_whenRagReturnsResults_injectsContext() {
        List<Map<String, Object>> ragResults = List.of(
                Map.of("content", "RAG content here", "source", "doc.pdf"));
        when(ragClient.query("hello", 3, 1L)).thenReturn(ragResults);
        when(streamSpec.content()).thenReturn(Flux.just("RAG-augmented reply"));

        Flux<String> result = service.streamChat("hello", 1L, null, null);

        StepVerifier.create(result)
                .expectNext("[RAG:ON]")
                .expectNext("RAG-augmented reply")
                .verifyComplete();

        verify(ragClient).query("hello", 3, 1L);
        verify(requestSpec).user(contains("doc.pdf"));
    }

    @Test
    void streamChat_whenRagReturnsEmpty_noInjection() {
        when(ragClient.query("hello", 3, 1L)).thenReturn(Collections.emptyList());
        when(streamSpec.content()).thenReturn(Flux.just("plain reply"));

        Flux<String> result = service.streamChat("hello", 1L, null, null);

        StepVerifier.create(result)
                .expectNext("[RAG:OFF]")
                .expectNext("plain reply")
                .verifyComplete();

        verify(ragClient).query("hello", 3, 1L);
        verify(requestSpec).user("hello");
    }

    @Test
    void streamChat_whenRagThrows_fallsBack() {
        when(ragClient.query("hello", 3, 1L)).thenThrow(new RuntimeException("RAG down"));
        when(streamSpec.content()).thenReturn(Flux.just("fallback reply"));

        Flux<String> result = service.streamChat("hello", 1L, null, null);

        StepVerifier.create(result)
                .expectNext("[RAG:OFF]")
                .expectNext("fallback reply")
                .verifyComplete();

        verify(requestSpec).user("hello");
    }
}
