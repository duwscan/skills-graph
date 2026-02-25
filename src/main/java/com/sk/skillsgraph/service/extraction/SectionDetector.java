package com.sk.skillsgraph.service.extraction;

import com.sk.skillsgraph.config.AppConstants;
import com.sk.skillsgraph.service.extraction.ExtractionTypes.DocumentAnalysis;
import com.sk.skillsgraph.service.extraction.ExtractionTypes.SectionInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SectionDetector {

    private static final Pattern HEADING_PATTERN = Pattern.compile(
            "(?im)^(?:#{1,6}\\s*)?([A-Za-z][A-Za-z \\-_/]{2,40})\\s*:?[ \\t]*$"
    );

    public DocumentAnalysis detect(String text) {
        if (text == null || text.isBlank()) {
            return new DocumentAnalysis("generic", List.of());
        }

        List<HeadingMatch> matches = new ArrayList<>();
        Matcher matcher = HEADING_PATTERN.matcher(text);
        while (matcher.find()) {
            String heading = matcher.group(1).trim();
            String sectionType = classifyHeading(heading);
            if (sectionType != null) {
                matches.add(new HeadingMatch(heading, sectionType, matcher.start(), matcher.end()));
            }
        }

        if (matches.isEmpty()) {
            return new DocumentAnalysis("generic", List.of());
        }

        int jdCount = 0;
        int cvCount = 0;
        for (HeadingMatch match : matches) {
            if (match.sectionType.startsWith("jd_")) {
                jdCount++;
            } else if (match.sectionType.startsWith("cv_")) {
                cvCount++;
            }
        }
        String documentType = jdCount == cvCount ? "generic" : (jdCount > cvCount ? "jd" : "cv");

        List<SectionInfo> sections = new ArrayList<>();
        for (int i = 0; i < matches.size(); i++) {
            HeadingMatch current = matches.get(i);
            int sectionStart = current.headingEnd;
            int sectionEnd = i + 1 < matches.size() ? matches.get(i + 1).headingStart : text.length();
            if (sectionEnd <= sectionStart) {
                continue;
            }

            String resolvedType = resolveTypeForDocument(current.sectionType, documentType);
            sections.add(new SectionInfo(
                    current.heading,
                    resolvedType,
                    weightFor(resolvedType),
                    sectionStart,
                    sectionEnd
            ));
        }

        if (sections.isEmpty()) {
            return new DocumentAnalysis("generic", List.of());
        }
        return new DocumentAnalysis(documentType, sections);
    }

    private String classifyHeading(String heading) {
        String normalized = heading.toLowerCase(Locale.ROOT).replaceAll("[^a-z ]", " ").trim();
        if (normalized.contains("requirement") || normalized.contains("qualification")) {
            return "jd_requirements";
        }
        if (normalized.contains("responsibilit") || normalized.contains("duties")) {
            return "jd_responsibilities";
        }
        if (normalized.contains("nice to have") || normalized.contains("preferred")) {
            return "jd_nice_to_have";
        }
        if (normalized.contains("about us") || normalized.contains("about team") || normalized.contains("company")) {
            return "jd_company";
        }
        if (normalized.contains("benefit") || normalized.contains("perks")) {
            return "jd_benefits";
        }
        if (normalized.equals("skills") || normalized.contains("skill set")) {
            return "cv_skills";
        }
        if (normalized.contains("experience") || normalized.contains("employment")) {
            return "cv_experience";
        }
        if (normalized.contains("project")) {
            return "cv_projects";
        }
        if (normalized.contains("education") || normalized.contains("certification")) {
            return "cv_education";
        }
        if (normalized.contains("summary") || normalized.contains("objective") || normalized.contains("profile")) {
            return "cv_summary";
        }
        return null;
    }

    private String resolveTypeForDocument(String detectedType, String documentType) {
        if ("generic".equals(documentType)) {
            if (detectedType.startsWith("jd_")) {
                return detectedType.substring(3);
            }
            if (detectedType.startsWith("cv_")) {
                return detectedType.substring(3);
            }
            return detectedType;
        }

        if ("jd".equals(documentType) && detectedType.startsWith("jd_")) {
            return detectedType.substring(3);
        }
        if ("cv".equals(documentType) && detectedType.startsWith("cv_")) {
            return detectedType.substring(3);
        }
        return "generic";
    }

    private double weightFor(String sectionType) {
        return switch (sectionType) {
            case "requirements" -> AppConstants.SECTION_WEIGHT_JD_REQUIREMENTS;
            case "responsibilities" -> AppConstants.SECTION_WEIGHT_JD_RESPONSIBILITIES;
            case "nice_to_have" -> AppConstants.SECTION_WEIGHT_JD_NICE_TO_HAVE;
            case "company" -> AppConstants.SECTION_WEIGHT_JD_COMPANY;
            case "benefits" -> AppConstants.SECTION_WEIGHT_JD_BENEFITS;
            case "skills" -> AppConstants.SECTION_WEIGHT_CV_SKILLS;
            case "experience" -> AppConstants.SECTION_WEIGHT_CV_EXPERIENCE;
            case "projects" -> AppConstants.SECTION_WEIGHT_CV_PROJECTS;
            case "education" -> AppConstants.SECTION_WEIGHT_CV_EDUCATION;
            case "summary" -> AppConstants.SECTION_WEIGHT_CV_SUMMARY;
            default -> 1.0D;
        };
    }

    private record HeadingMatch(
            String heading,
            String sectionType,
            int headingStart,
            int headingEnd
    ) {
    }
}
