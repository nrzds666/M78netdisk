package com.m78.netdisk.listener;

import com.m78.netdisk.common.client.RagClient;
import com.m78.netdisk.file.event.FilePermanentlyDeletedEvent;
import com.m78.netdisk.file.event.FilePermanentlyDeletedEvent.DeletedItemInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagDeletionListenerTest {

    @Mock
    private RagClient ragClient;
    private RagDeletionListener listener;

    @BeforeEach
    void setUp() {
        listener = new RagDeletionListener(ragClient);
    }

    @Test
    void onFilePermanentlyDeleted_whenFile_deletesFromRag() {
        DeletedItemInfo item = new DeletedItemInfo(1L, "doc.pdf", "oss/doc.pdf", false);
        FilePermanentlyDeletedEvent event = new FilePermanentlyDeletedEvent(
                this, 100L, List.of(1L), List.of(item));

        listener.onFilePermanentlyDeleted(event);

        verify(ragClient).deleteDocumentByFileName("doc.pdf");
    }

    @Test
    void onFilePermanentlyDeleted_whenDirectory_skipsRag() {
        DeletedItemInfo dir = new DeletedItemInfo(2L, "myFolder", null, true);
        FilePermanentlyDeletedEvent event = new FilePermanentlyDeletedEvent(
                this, 100L, List.of(2L), List.of(dir));

        listener.onFilePermanentlyDeleted(event);

        verify(ragClient, never()).deleteDocumentByFileName(anyString());
    }

    @Test
    void onFilePermanentlyDeleted_whenEmptyItems_noOp() {
        FilePermanentlyDeletedEvent event = new FilePermanentlyDeletedEvent(
                this, 100L, List.of(), List.of());

        listener.onFilePermanentlyDeleted(event);

        verifyNoInteractions(ragClient);
    }

    @Test
    void onFilePermanentlyDeleted_whenNullItems_noOp() {
        FilePermanentlyDeletedEvent event = new FilePermanentlyDeletedEvent(
                this, 100L, List.of(1L), null);

        listener.onFilePermanentlyDeleted(event);

        verifyNoInteractions(ragClient);
    }

    @Test
    void onFilePermanentlyDeleted_whenDeleteThrows_doesNotPropagate() {
        DeletedItemInfo item = new DeletedItemInfo(3L, "err.pdf", "oss/err.pdf", false);
        FilePermanentlyDeletedEvent event = new FilePermanentlyDeletedEvent(
                this, 100L, List.of(3L), List.of(item));
        doThrow(new RuntimeException("RAG down"))
                .when(ragClient).deleteDocumentByFileName("err.pdf");

        // should not throw
        listener.onFilePermanentlyDeleted(event);
    }

    @Test
    void onFilePermanentlyDeleted_multipleItems_processesAll() {
        DeletedItemInfo f1 = new DeletedItemInfo(1L, "a.pdf", "k1", false);
        DeletedItemInfo dir = new DeletedItemInfo(2L, "folder", null, true);
        DeletedItemInfo f2 = new DeletedItemInfo(3L, "b.pdf", "k2", false);
        FilePermanentlyDeletedEvent event = new FilePermanentlyDeletedEvent(
                this, 100L, List.of(1L, 2L, 3L), List.of(f1, dir, f2));

        listener.onFilePermanentlyDeleted(event);

        verify(ragClient).deleteDocumentByFileName("a.pdf");
        verify(ragClient).deleteDocumentByFileName("b.pdf");
        verify(ragClient, never()).deleteDocumentByFileName(eq("folder"));
    }
}
