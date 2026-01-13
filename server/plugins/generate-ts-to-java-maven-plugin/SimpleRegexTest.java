import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class SimpleRegexTest {
    public static void main(String[] args) {
        String testInput = "  seconds: number; // javaType: int";

        System.out.println("Testing line: " + testInput);

        // Test basic property pattern
        Pattern propPat = Pattern.compile("(?ms)^[\\t ]*(public|private|protected)?[\\t ]*([A-Za-z_$][A-Za-z0-9_$]*)[\\t ]*(\\?)?[\\t ]*:[\\t ]*(.+?)\\s*;[\\t ]*");
        Matcher m = propPat.matcher(testInput);

        if (m.find()) {
            System.out.println("Property found:");
            System.out.println("  Name: " + m.group(2));
            System.out.println("  Type: " + m.group(4));
            System.out.println("  Match end: " + m.end());
            System.out.println("  Full line length: " + testInput.length());

            // Test comment search after match
            if (m.end() < testInput.length()) {
                String afterMatch = testInput.substring(m.end());
                System.out.println("  After match: '" + afterMatch + "'");

                Pattern javaTypePattern = Pattern.compile("//\\s*javaType:\\s*([A-Za-z0-9_.]+)");
                Matcher javaTypeMatcher = javaTypePattern.matcher(afterMatch);
                if (javaTypeMatcher.find()) {
                    System.out.println("  JavaType found: " + javaTypeMatcher.group(1));
                } else {
                    System.out.println("  JavaType NOT found in after-match text");

                    // Try on full line
                    Matcher fullLineMatcher = javaTypePattern.matcher(testInput);
                    if (fullLineMatcher.find()) {
                        System.out.println("  JavaType found on full line: " + fullLineMatcher.group(1));
                    }
                }
            }
        } else {
            System.out.println("No property match found!");
        }
    }
}
