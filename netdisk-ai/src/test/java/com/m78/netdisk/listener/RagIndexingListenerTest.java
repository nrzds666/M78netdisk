package com.m78.netdisk.listener;

import com.m78.netdisk.common.client.RagClient;
import com.m78.netdisk.common.config.RagServiceProperties;
import com.m78.netdisk.file.domain.po.Item;
import com.m78.netdisk.file.event.FileCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class RagIndexingListenerTest {

    @Mock
    private RagClient ragClient;
    @Mock
    private RagServiceProperties properties;
    private RagIndexingListener listener;

    @BeforeEach
    void setUp() {
        listener = new RagIndexingListener(ragClient, properties);
    }

    @Test
    void onFileCreated_whenSupportedType_indexesFile() {
        when(properties.getSupportedExtensions()).thenReturn(List.of("pdf", "txt"));
        Item item = new Item()
                .setId(1L).setName("report.pdf")
                .setStorageKey("oss/key/report.pdf");
        when(ragClient.downloadFromStorage("oss/key/report.pdf"))
                .thenReturn("content".getBytes());

        listener.onFileCreated(new FileCreatedEvent(this, item));

        verify(ragClient).downloadFromStorage("oss/key/report.pdf");
        verify(ragClient).indexFile(eq("report.pdf"), any(byte[].class), eq(null));
    }

    @Test
    void onFileCreated_whenUnsupportedType_skips() {
        when(properties.getSupportedExtensions()).thenReturn(List.of("pdf", "txt"));
        Item item = new Item()
                .setId(2L).setName("image.png")
                .setStorageKey("oss/key/image.png");

        listener.onFileCreated(new FileCreatedEvent(this, item));

        verify(ragClient, never()).downloadFromStorage(anyString());
        verify(ragClient, never()).indexFile(anyString(), any(), any());
    }

    @Test
    void onFileCreated_whenEmptyContent_skips() {
        when(properties.getSupportedExtensions()).thenReturn(List.of("pdf", "txt"));
        Item item = new Item()
                .setId(3L).setName("empty.txt")
                .setStorageKey("oss/key/empty.txt");
        when(ragClient.downloadFromStorage("oss/key/empty.txt")).thenReturn(new byte[0]);

        listener.onFileCreated(new FileCreatedEvent(this, item));

        verify(ragClient).downloadFromStorage("oss/key/empty.txt");
        verify(ragClient, never()).indexFile(anyString(), any(), any());
    }

    @Test
    void onFileCreated_whenDownloadReturnsNull_skips() {
        when(properties.getSupportedExtensions()).thenReturn(List.of("pdf", "txt"));
        Item item = new Item()
                .setId(4L).setName("missing.pdf")
                .setStorageKey("oss/key/missing.pdf");
        when(ragClient.downloadFromStorage("oss/key/missing.pdf")).thenReturn(null);

        listener.onFileCreated(new FileCreatedEvent(this, item));

        verify(ragClient, never()).indexFile(anyString(), any(), any());
    }

    @Test
    void onFileCreated_whenIndexThrows_doesNotPropagate() {
        when(properties.getSupportedExtensions()).thenReturn(List.of("pdf", "txt"));
        Item item = new Item()
                .setId(5L).setName("bad.pdf")
                .setStorageKey("oss/key/bad.pdf");
        when(ragClient.downloadFromStorage("oss/key/bad.pdf"))
                .thenReturn("content".getBytes());
        doThrow(new RuntimeException("RAG down"))
                .when(ragClient).indexFile(anyString(), any(), any());

        // should not throw
        listener.onFileCreated(new FileCreatedEvent(this, item));
    }

    @Test
    void onFileCreated_supportsVariousExtensions() {
        when(properties.getSupportedExtensions()).thenReturn(List.of("pdf", "java", "py", "yaml", "json", "md", "sql"));
        String[] supported = {"doc.java", "script.py", "config.yaml", "data.json", "notes.md", "query.sql"};
        for (String name : supported) {
            Item item = new Item().setId(1L).setName(name).setStorageKey("k");
            when(ragClient.downloadFromStorage(anyString())).thenReturn("c".getBytes());
            listener.onFileCreated(new FileCreatedEvent(this, item));
        }
        verify(ragClient, times(supported.length)).indexFile(anyString(), any(), any());
    }
}
