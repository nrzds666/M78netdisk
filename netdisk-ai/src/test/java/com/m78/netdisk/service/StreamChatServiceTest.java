package com.m78.netdisk.service;

import com.m78.netdisk.common.client.RagClient;
import com.m78.netdisk.common.config.RagServiceProperties;
import com.m78.netdisk.domain.ChatRequest;
import com.m78.netdisk.tool.NetdiskTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StreamChatServiceTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;
    @Mock
    private ChatClient chatClient;
    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock
    private ChatClient.StreamResponseSpec streamSpec;
    @Mock
    private NetdiskTools netdiskTools;
    @Mock
    private AiDocumentService aiDocumentService;
    @Mock
    private RagClient ragClient;
    @Mock
    private RagServiceProperties ragProperties;

    private StreamChatService service;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.defaultTools(netdiskTools)).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(ragProperties.isEnabled()).thenReturn(false);
        service = new StreamChatService(chatClientBuilder, netdiskTools, aiDocumentService, ragClient, ragProperties);

        lenient().when(chatClient.prompt()).thenReturn(requestSpec);
        lenient().when(requestSpec.system(anyString())).thenReturn(requestSpec);
        lenient().when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        lenient().when(requestSpec.user(anyString())).thenReturn(requestSpec);
        lenient().when(requestSpec.toolContext(any())).thenReturn(requestSpec);
        lenient().when(requestSpec.stream()).thenReturn(streamSpec);
    }

    @Test
    void streamChat_normalMessage_returnsTextStream() {
        when(streamSpec.content()).thenReturn(Flux.just("Hello", " ", "World", "!"));

        AtomicReference<String> received = new AtomicReference<>("");
        Flux<String> result = service.streamChat("你好", 1L, null, null);

        StepVerifier.create(result)
                .expectNext("Hello")
                .expectNext(" ")
                .expectNext("World")
                .expectNext("!")
                .verifyComplete();

        verify(requestSpec).user("你好");
        verify(requestSpec).system(argThat((String s) -> s.contains("M78 网盘")));
    }

    @Test
    void streamChat_withHistory_includesHistory() {
        ChatRequest.HistoryMessage h1 = new ChatRequest.HistoryMessage();
        h1.setRole("user");
        h1.setContent("之前的问题");
        ChatRequest.HistoryMessage h2 = new ChatRequest.HistoryMessage();
        h2.setRole("assistant");
        h2.setContent("之前的回答");
        List<ChatRequest.HistoryMessage> history = List.of(h1, h2);

        when(streamSpec.content()).thenReturn(Flux.just("响应"));

        service.streamChat("新问题", 1L, history, null);

        verify(requestSpec).messages(argThat((List<org.springframework.ai.chat.messages.Message> list) -> list.size() == 2));
        verify(requestSpec).user("新问题");
    }

    @Test
    void streamChat_withDocContext_injectsDocContent() {
        ChatRequest.DocContext docContext = new ChatRequest.DocContext("doc-123", "测试文档.md", "md", 0);
        when(aiDocumentService.readTempDocContent("doc-123", 1L)).thenReturn("文档内容");
        when(streamSpec.content()).thenReturn(Flux.just("响应"));

        service.streamChat("修改第三段", 1L, null, docContext);

        verify(requestSpec).user(argThat((String prompt) ->
                prompt.contains("文档内容") && prompt.contains("修改第三段")));
    }

    @Test
    void streamChat_withExpiredDocContext_fallsBack() {
        ChatRequest.DocContext docContext = new ChatRequest.DocContext("expired-doc", "测试.md", "md", 1);
        when(aiDocumentService.readTempDocContent("expired-doc", 1L)).thenReturn(null);
        when(streamSpec.content()).thenReturn(Flux.just("普通回复"));

        service.streamChat("继续修改", 1L, null, docContext);

        // docContext 已过期，降级为普通消息
        verify(requestSpec).user("继续修改");
    }

    @Test
    void streamChat_withHighRound_usesSummaryMode() {
        ChatRequest.DocContext docContext = new ChatRequest.DocContext("doc-123", "测试文档.md", "md", 5);
        when(aiDocumentService.readTempDocContent("doc-123", 1L)).thenReturn("文档内容");
        when(streamSpec.content()).thenReturn(Flux.just("响应"));

        service.streamChat("改格式", 1L, null, docContext);

        verify(requestSpec).user(argThat((String prompt) ->
                prompt.contains("用户修改指令")));
    }
}
