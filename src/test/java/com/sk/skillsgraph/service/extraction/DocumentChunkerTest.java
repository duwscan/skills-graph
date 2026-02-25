package com.sk.skillsgraph.service.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sk.skillsgraph.service.extraction.ExtractionTypes.Chunk;
import com.sk.skillsgraph.service.extraction.ExtractionTypes.DocumentAnalysis;
import com.sk.skillsgraph.service.extraction.ExtractionTypes.SectionInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentChunkerTest {

    private final TokenEstimator tokenEstimator = new TokenEstimator();
    private final DocumentChunker chunker = new DocumentChunker(tokenEstimator);

    @Test
    void emptyTextReturnsNoChunks() {
        List<Chunk> chunks = chunker.chunk("", new DocumentAnalysis("generic", List.of()));
        assertTrue(chunks.isEmpty());
    }

    @Test
    void shortTextReturnsSingleChunk() {
        String text = "Python and Spring Boot experience required.";
        List<Chunk> chunks = chunker.chunk(text, new DocumentAnalysis("generic", List.of()));
        assertEquals(1, chunks.size());
        assertTrue(chunks.getFirst().tokenEstimate() > 0);
        assertEquals(0, chunks.getFirst().startOffset());
        assertTrue(chunks.getFirst().endOffset() <= text.length());
    }

    @Test
    void sectionChunkingPreservesSectionMetadata() {
        String text = """
                Requirements
                Python

                Docker

                AWS
                """;
        SectionInfo section = new SectionInfo("Requirements", "requirements", 1.0D, 0, text.length());
        List<Chunk> chunks = chunker.chunk(text, new DocumentAnalysis("jd", List.of(section)));

        assertEquals(1, chunks.size());
        assertNotNull(chunks.getFirst().section());
        assertEquals("requirements", chunks.getFirst().section().type());
    }

    @Test
    void longUnstructuredTextUsesSlidingWindowWithOverlap() {
        String text = buildLongText(3200);
        List<Chunk> chunks = chunker.chunk(text, new DocumentAnalysis("generic", List.of()));

        assertTrue(chunks.size() >= 2);
        for (int i = 1; i < chunks.size(); i++) {
            Chunk previous = chunks.get(i - 1);
            Chunk current = chunks.get(i);
            assertTrue(current.startOffset() < previous.endOffset());
            assertTrue(current.endOffset() > current.startOffset());
        }
    }

    @Test
    void structuredSmallParagraphsAreMerged() {
        String text = """
                Paragraph one about Python.

                Paragraph two about Docker.

                Paragraph three about AWS.
                """;
        SectionInfo section = new SectionInfo("Requirements", "requirements", 1.0D, 0, text.length());
        List<Chunk> chunks = chunker.chunk(text, new DocumentAnalysis("jd", List.of(section)));

        assertEquals(1, chunks.size());
        assertFalse(chunks.getFirst().text().isBlank());
    }

    private String buildLongText(int words) {
        StringBuilder builder = new StringBuilder(words * 12);
        for (int i = 0; i < words; i++) {
            builder.append("python");
            if (i % 20 == 19) {
                builder.append(". ");
            } else {
                builder.append(' ');
            }
        }
        return builder.toString().trim();
    }
}
