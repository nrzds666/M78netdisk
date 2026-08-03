package com.m78.netdisk.common.client;

import com.m78.netdisk.common.client.RagClient;
import com.m78.netdisk.common.config.RagServiceProperties;
import com.m78.netdisk.common.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagClientTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private StorageService storageService;
    private RagServiceProperties properties;
    private RagClient ragClient;

    @BeforeEach
    void setUp() {
        properties = new RagServiceProperties();
        properties.setEnabled(true);
        properties.setUrl("http://localhost:8000");
        ragClient = new RagClient(restTemplate, storageService, properties);
    }

    // ── indexFile ──────────────────────────────────────────

    @Test
    void indexFile_whenDisabled_doesNotCallRest() {
        properties.setEnabled(false);
        ragClient.indexFile("test.pdf", new byte[]{1, 2, 3}, 1L);
        verifyNoInteractions(restTemplate);
    }

    @Test
    void indexFile_whenSuccess_logsChunks() {
        Map<String, Object> response = Map.of("status", "success", "chunks", 5);
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class))).thenReturn(response);

        ragClient.indexFile("test.pdf", "hello".getBytes(), 1L);
        verify(restTemplate).postForObject(contains("/upload"), any(), eq(Map.class));
    }

    @Test
    void indexFile_whenNotSuccess_logsWarning() {
        Map<String, Object> response = Map.of("status", "error");
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class))).thenReturn(response);

        ragClient.indexFile("test.pdf", "hello".getBytes(), 1L);
        verify(restTemplate).postForObject(contains("/upload"), any(), eq(Map.class));
    }

    @Test
    void indexFile_whenRestThrows_logsError() {
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(new RuntimeException("connection refused"));

        // should not throw
        ragClient.indexFile("test.pdf", "hello".getBytes(), 1L);
    }

    // ── query ──────────────────────────────────────────────

    @Test
    void query_whenDisabled_returnsEmpty() {
        properties.setEnabled(false);
        List<Map<String, Object>> results = ragClient.query("hello", 3, 1L);
        assertTrue(results.isEmpty());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void query_whenSuccess_returnsResults() {
        List<Map<String, Object>> mockResults = List.of(
                Map.of("content", "abc", "source", "doc1.pdf"),
                Map.of("content", "def", "source", "doc2.pdf"));
        Map<String, Object> response = Map.of("results", mockResults);
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class))).thenReturn(response);

        List<Map<String, Object>> results = ragClient.query("hello", 3, 1L);
        assertEquals(2, results.size());
        assertEquals("abc", results.get(0).get("content"));
    }

    @Test
    void query_whenNullResponse_returnsEmpty() {
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class))).thenReturn(null);
        assertTrue(ragClient.query("hello", 3, 1L).isEmpty());
    }

    @Test
    void query_whenThrows_returnsEmpty() {
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(new RuntimeException("timeout"));
        assertTrue(ragClient.query("hello", 3, 1L).isEmpty());
    }

    @Test
    void query_usesPropertiesForParams() {
        properties.setEnableRewrite(false);
        properties.setEnableHybrid(false);
        properties.setEnableRerank(true);
        properties.setEnableCompress(true);

        List<Map<String, Object>> mockResults = List.of(Map.of("content", "x"));
        Map<String, Object> response = Map.of("results", mockResults);
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class))).thenReturn(response);

        ragClient.query("test", 5, 1L);
        verify(restTemplate).postForObject(contains("/query"), any(), eq(Map.class));
    }

    // ── deleteDocumentByFileName ───────────────────────────

    @Test
    void deleteDocumentByFileName_whenDisabled_noOp() {
        properties.setEnabled(false);
        ragClient.deleteDocumentByFileName("test.pdf");
        verifyNoInteractions(restTemplate);
    }

    @Test
    void deleteDocumentByFileName_whenFound_deletes() {
        List<Map<String, Object>> docs = List.of(
                Map.of("id", "doc-1", "name", "other.pdf"),
                Map.of("id", "doc-2", "name", "test.pdf"));
        Map<String, Object> listResponse = Map.of("documents", docs);
        when(restTemplate.getForObject(contains("/documents"), eq(Map.class))).thenReturn(listResponse);

        ragClient.deleteDocumentByFileName("test.pdf");
        verify(restTemplate).delete(contains("/documents/doc-2"));
    }

    @Test
    void deleteDocumentByFileName_whenNotFound_noDelete() {
        Map<String, Object> listResponse = Map.of("documents", List.of(
                Map.of("id", "doc-1", "name", "other.pdf")));
        when(restTemplate.getForObject(contains("/documents"), eq(Map.class))).thenReturn(listResponse);

        ragClient.deleteDocumentByFileName("test.pdf");
        verify(restTemplate, never()).delete(anyString());
    }

    // ── deleteDocument ─────────────────────────────────────

    @Test
    void deleteDocument_whenDisabled_noOp() {
        properties.setEnabled(false);
        ragClient.deleteDocument("doc-1");
        verifyNoInteractions(restTemplate);
    }

    @Test
    void deleteDocument_callsRest() {
        ragClient.deleteDocument("doc-1");
        verify(restTemplate).delete(contains("/documents/doc-1"));
    }

    // ── downloadFromStorage ────────────────────────────────

    @Test
    void downloadFromStorage_whenNullKey_returnsNull() {
        assertNull(ragClient.downloadFromStorage(null));
    }

    @Test
    void downloadFromStorage_whenBlankKey_returnsNull() {
        assertNull(ragClient.downloadFromStorage("  "));
    }

    @Test
    void downloadFromStorage_whenSuccess_returnsBytes() throws Exception {
        byte[] expected = "hello world".getBytes();
        InputStream is = new ByteArrayInputStream(expected);
        when(storageService.getInputStream("key1")).thenReturn(is);

        byte[] result = ragClient.downloadFromStorage("key1");
        assertArrayEquals(expected, result);
    }

    @Test
    void downloadFromStorage_whenThrows_returnsNull() throws Exception {
        when(storageService.getInputStream("key1"))
                .thenThrow(new RuntimeException("not found"));
        assertNull(ragClient.downloadFromStorage("key1"));
    }
}
