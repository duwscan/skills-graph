package com.sk.skillsgraph.service.extraction;

import com.sk.skillsgraph.config.AppConstants;
import com.sk.skillsgraph.service.extraction.ExtractionTypes.Chunk;
import com.sk.skillsgraph.service.extraction.ExtractionTypes.DocumentAnalysis;
import com.sk.skillsgraph.service.extraction.ExtractionTypes.SectionInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class DocumentChunker {

    private static final Pattern SPLIT_PATTERN = Pattern.compile("\\n\\n+|\\n#|\\n---+\\n?");
    private static final Pattern WORD_PATTERN = Pattern.compile("\\S+");

    private final TokenEstimator tokenEstimator;

    public DocumentChunker(TokenEstimator tokenEstimator) {
        this.tokenEstimator = tokenEstimator;
    }

    public List<Chunk> chunk(String text, DocumentAnalysis analysis) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<Chunk> chunks = new ArrayList<>();
        int nextIndex = 0;

        if (analysis == null || analysis.sections().isEmpty()) {
            return slidingWindowChunks(text, 0, null, 0);
        }

        for (SectionInfo section : analysis.sections()) {
            int safeStart = Math.max(0, section.startOffset());
            int safeEnd = Math.min(text.length(), section.endOffset());
            if (safeEnd <= safeStart) {
                continue;
            }

            String sectionText = text.substring(safeStart, safeEnd).trim();
            if (sectionText.isBlank()) {
                continue;
            }

            List<Chunk> sectionChunks;
            if (tokenEstimator.estimateTokens(sectionText) > AppConstants.CHUNK_MAX_TOKENS) {
                sectionChunks = slidingWindowChunks(sectionText, safeStart, section, nextIndex);
            } else {
                sectionChunks = mergeStructuredBlocks(sectionText, safeStart, section, nextIndex);
            }
            chunks.addAll(sectionChunks);
            nextIndex = chunks.size();
        }

        if (chunks.isEmpty()) {
            return slidingWindowChunks(text, 0, null, 0);
        }
        return chunks;
    }

    private List<Chunk> mergeStructuredBlocks(String sectionText, int globalOffset, SectionInfo section, int startIndex) {
        List<Block> blocks = splitBlocks(sectionText);
        if (blocks.isEmpty()) {
            return List.of();
        }

        List<Chunk> chunks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        int chunkStart = -1;
        int chunkEnd = -1;
        int index = startIndex;

        for (Block block : blocks) {
            if (block.text().isBlank()) {
                continue;
            }
            String mergedCandidate = buffer.isEmpty() ? block.text() : buffer + "\n\n" + block.text();
            int candidateTokens = tokenEstimator.estimateTokens(mergedCandidate);

            if (buffer.length() > 0 && candidateTokens > AppConstants.CHUNK_TARGET_TOKENS) {
                chunks.add(buildChunk(buffer.toString(), index++, chunkStart, chunkEnd, section));
                buffer.setLength(0);
                chunkStart = -1;
                chunkEnd = -1;
            }

            if (tokenEstimator.estimateTokens(block.text()) > AppConstants.CHUNK_MAX_TOKENS) {
                if (buffer.length() > 0) {
                    chunks.add(buildChunk(buffer.toString(), index++, chunkStart, chunkEnd, section));
                    buffer.setLength(0);
                    chunkStart = -1;
                    chunkEnd = -1;
                }
                List<Chunk> fallback = slidingWindowChunks(block.text(), globalOffset + block.start(), section, index);
                chunks.addAll(fallback);
                index = startIndex + chunks.size();
                continue;
            }

            if (buffer.isEmpty()) {
                chunkStart = globalOffset + block.start();
            }
            if (buffer.length() > 0) {
                buffer.append("\n\n");
            }
            buffer.append(block.text());
            chunkEnd = globalOffset + block.end();
        }

        if (buffer.length() > 0) {
            chunks.add(buildChunk(buffer.toString(), index, chunkStart, chunkEnd, section));
        }
        return chunks;
    }

    private List<Block> splitBlocks(String sectionText) {
        List<Block> blocks = new ArrayList<>();
        Matcher matcher = SPLIT_PATTERN.matcher(sectionText);
        int cursor = 0;
        while (matcher.find()) {
            int start = cursor;
            int end = matcher.start();
            if (end > start) {
                String text = sectionText.substring(start, end).trim();
                if (!text.isBlank()) {
                    blocks.add(new Block(text, start, end));
                }
            }
            cursor = matcher.end();
        }
        if (cursor < sectionText.length()) {
            String tail = sectionText.substring(cursor).trim();
            if (!tail.isBlank()) {
                blocks.add(new Block(tail, cursor, sectionText.length()));
            }
        }
        return blocks;
    }

    private List<Chunk> slidingWindowChunks(String text, int globalOffset, SectionInfo section, int startIndex) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<WordSpan> spans = collectWordSpans(text);
        if (spans.isEmpty()) {
            return List.of(buildChunk(text.trim(), startIndex, globalOffset, globalOffset + text.length(), section));
        }

        int wordsPerChunk = Math.max(40, (int) Math.floor(AppConstants.CHUNK_TARGET_TOKENS / 1.3D));
        int overlapWords = Math.max(1, (int) Math.floor(AppConstants.CHUNK_OVERLAP_TOKENS / 1.3D));

        List<Chunk> chunks = new ArrayList<>();
        int startWord = 0;
        int index = startIndex;
        while (startWord < spans.size()) {
            int endWord = Math.min(startWord + wordsPerChunk, spans.size());
            endWord = advanceToSentenceBoundary(spans, endWord);

            int localStart = spans.get(startWord).start();
            int localEnd = spans.get(endWord - 1).end();
            String chunkText = text.substring(localStart, localEnd).trim();

            chunks.add(buildChunk(
                    chunkText,
                    index++,
                    globalOffset + localStart,
                    globalOffset + localEnd,
                    section
            ));

            if (endWord >= spans.size()) {
                break;
            }

            int nextStart = endWord - overlapWords;
            if (nextStart <= startWord) {
                nextStart = endWord;
            }
            startWord = nextStart;
        }
        return chunks;
    }

    private int advanceToSentenceBoundary(List<WordSpan> spans, int endWord) {
        int lookaheadLimit = Math.min(endWord + 30, spans.size());
        for (int i = endWord - 1; i < lookaheadLimit; i++) {
            String token = spans.get(i).word();
            if (token.endsWith(".") || token.endsWith("!") || token.endsWith("?")) {
                return i + 1;
            }
        }
        return endWord;
    }

    private List<WordSpan> collectWordSpans(String text) {
        List<WordSpan> spans = new ArrayList<>();
        Matcher matcher = WORD_PATTERN.matcher(text);
        while (matcher.find()) {
            spans.add(new WordSpan(matcher.group(), matcher.start(), matcher.end()));
        }
        return spans;
    }

    private Chunk buildChunk(String text, int index, int startOffset, int endOffset, SectionInfo section) {
        String normalized = text == null ? "" : text.trim();
        return new Chunk(
                normalized,
                index,
                Math.max(0, startOffset),
                Math.max(startOffset, endOffset),
                tokenEstimator.estimateTokens(normalized),
                section
        );
    }

    private record Block(String text, int start, int end) {
    }

    private record WordSpan(String word, int start, int end) {
    }
}
