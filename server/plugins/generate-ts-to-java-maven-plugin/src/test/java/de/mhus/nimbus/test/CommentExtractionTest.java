package de.mhus.nimbus.test;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test für das Extrahieren von Kommentaren und javaType-Hints
 */
public class CommentExtractionTest {

    @Test
    public void testExtractCommentAndJavaTypeHint() {
        String originalBody = """
        export interface StepWait {
          kind: 'Wait';
          /** Duration in seconds */
          seconds: number; // javaType: int
        }
        """;

        String[] result = extractCommentAndJavaTypeHint(originalBody, "seconds");

        assertNotNull(result);
        assertEquals(2, result.length);
        assertEquals("// javaType: int", result[0]); // comment
        assertEquals("int", result[1]); // javaTypeHint
    }

    @Test
    public void testExtractCommentAndJavaTypeHint_NoComment() {
        String originalBody = """
        export interface Test {
          name: string;
        }
        """;

        String[] result = extractCommentAndJavaTypeHint(originalBody, "name");

        assertNotNull(result);
        assertEquals(2, result.length);
        assertNull(result[0]); // comment
        assertNull(result[1]); // javaTypeHint
    }

    @Test
    public void testExtractCommentAndJavaTypeHint_CommentNoJavaType() {
        String originalBody = """
        export interface Test {
          count: number; // just a regular comment
        }
        """;

        String[] result = extractCommentAndJavaTypeHint(originalBody, "count");

        assertNotNull(result);
        assertEquals(2, result.length);
        assertEquals("// just a regular comment", result[0]); // comment
        assertNull(result[1]); // javaTypeHint
    }

    // Simuliere die TsParser Methode
    private String[] extractCommentAndJavaTypeHint(String originalBody, String propertyName) {
        if (originalBody == null || propertyName == null) {
            return new String[]{null, null};
        }

        // Search the original body for a line containing this property name and a comment
        String[] lines = originalBody.split("\n");
        for (String line : lines) {
            // Look for property declaration line
            if (line.contains(propertyName + ":") && line.contains("//")) {
                // Extract the comment part (everything after //)
                int commentStart = line.lastIndexOf("//");
                String fullComment = line.substring(commentStart).trim();

                // Extract javaType hint from this comment
                String javaTypeHint = parseJavaTypeHintFromLine(line);

                return new String[]{fullComment, javaTypeHint};
            }
        }

        return new String[]{null, null};
    }

    private String parseJavaTypeHintFromLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }

        // Find the last // comment in the line
        int commentStart = line.lastIndexOf("//");
        if (commentStart == -1) {
            return null;
        }

        String comment = line.substring(commentStart + 2).trim();
        String lowerComment = comment.toLowerCase();

        // Search for "javatype" (case insensitive) followed by : or =
        int javaTypeStart = lowerComment.indexOf("javatype");
        if (javaTypeStart == -1) {
            return null;
        }

        // Find the separator after "javatype"
        int separatorPos = javaTypeStart + "javatype".length();

        // Skip whitespace
        while (separatorPos < lowerComment.length() && Character.isWhitespace(lowerComment.charAt(separatorPos))) {
            separatorPos++;
        }

        // Check for : or =
        if (separatorPos >= lowerComment.length()) {
            return null;
        }

        char separator = lowerComment.charAt(separatorPos);
        if (separator != ':' && separator != '=') {
            return null;
        }

        // Extract the type after the separator
        int typeStart = separatorPos + 1;

        // Skip whitespace after the separator
        while (typeStart < comment.length() && Character.isWhitespace(comment.charAt(typeStart))) {
            typeStart++;
        }

        if (typeStart >= comment.length()) {
            return null;
        }

        String typeStr = comment.substring(typeStart);

        // Remove trailing comments (if any)
        int nextComment = typeStr.indexOf("//");
        if (nextComment != -1) {
            typeStr = typeStr.substring(0, nextComment).trim();
        }

        // Remove trailing whitespace and semicolon
        typeStr = typeStr.replaceAll("[\\s;]*$", "");

        return typeStr.isEmpty() ? null : typeStr;
    }
}
