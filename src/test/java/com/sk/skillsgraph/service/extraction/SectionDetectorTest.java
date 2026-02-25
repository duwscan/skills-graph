package com.sk.skillsgraph.service.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sk.skillsgraph.config.AppConstants;
import com.sk.skillsgraph.service.extraction.ExtractionTypes.DocumentAnalysis;
import com.sk.skillsgraph.service.extraction.ExtractionTypes.SectionInfo;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SectionDetectorTest {

    private final SectionDetector detector = new SectionDetector();

    @Test
    void detectsJobDescriptionSectionsAndWeights() {
        String text = """
                Requirements
                Python and Docker required.

                Responsibilities
                Build data pipelines.

                Nice to have
                Kubernetes experience.
                """;

        DocumentAnalysis analysis = detector.detect(text);

        assertEquals("jd", analysis.documentType());
        assertEquals(3, analysis.sections().size());
        Map<String, SectionInfo> byType = analysis.sections().stream()
                .collect(Collectors.toMap(SectionInfo::type, section -> section));
        assertTrue(byType.containsKey("requirements"));
        assertTrue(byType.containsKey("responsibilities"));
        assertTrue(byType.containsKey("nice_to_have"));
        assertEquals(AppConstants.SECTION_WEIGHT_JD_REQUIREMENTS, byType.get("requirements").weight());
        assertEquals(AppConstants.SECTION_WEIGHT_JD_RESPONSIBILITIES, byType.get("responsibilities").weight());
        assertEquals(AppConstants.SECTION_WEIGHT_JD_NICE_TO_HAVE, byType.get("nice_to_have").weight());
    }

    @Test
    void detectsCvSectionsAndWeights() {
        String text = """
                Skills
                Java, Spring Boot, PostgreSQL

                Experience
                Built APIs and workers

                Projects
                Graph extraction platform
                """;

        DocumentAnalysis analysis = detector.detect(text);

        assertEquals("cv", analysis.documentType());
        Map<String, SectionInfo> byType = analysis.sections().stream()
                .collect(Collectors.toMap(SectionInfo::type, section -> section));
        assertTrue(byType.containsKey("skills"));
        assertTrue(byType.containsKey("experience"));
        assertTrue(byType.containsKey("projects"));
        assertEquals(AppConstants.SECTION_WEIGHT_CV_SKILLS, byType.get("skills").weight());
        assertEquals(AppConstants.SECTION_WEIGHT_CV_EXPERIENCE, byType.get("experience").weight());
        assertEquals(AppConstants.SECTION_WEIGHT_CV_PROJECTS, byType.get("projects").weight());
    }

    @Test
    void returnsGenericForUnstructuredText() {
        String text = "We need software engineers with backend and cloud experience.";
        DocumentAnalysis analysis = detector.detect(text);

        assertEquals("generic", analysis.documentType());
        assertTrue(analysis.sections().isEmpty());
    }

    @Test
    void sectionOffsetsAreIncreasingAndValid() {
        String text = """
                Requirements
                Python and Java.

                Responsibilities
                Ship production services.
                """;

        DocumentAnalysis analysis = detector.detect(text);
        assertFalse(analysis.sections().isEmpty());
        int previousStart = -1;
        for (SectionInfo section : analysis.sections()) {
            assertTrue(section.startOffset() >= 0);
            assertTrue(section.endOffset() > section.startOffset());
            assertTrue(section.startOffset() > previousStart);
            previousStart = section.startOffset();
        }
    }
}
