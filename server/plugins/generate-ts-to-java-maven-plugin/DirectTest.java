import java.lang.reflect.Method;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class DirectTest {
    public static void main(String[] args) throws Exception {
        // Test the actual regex and method directly
        String testTypeScript = """
        export interface StepWait {
          kind: 'Wait';
          /** Duration in seconds */
          seconds: number; // javaType: int
        }
        """;

        System.out.println("Testing TypeScript content:");
        System.out.println(testTypeScript);
        System.out.println("================");

        // Test property regex
        Pattern propPat = Pattern.compile("(?ms)^[\\t ]*(public|private|protected)?[\\t ]*([A-Za-z_$][A-Za-z0-9_$]*)[\\t ]*(\\?)?[\\t ]*:[\\t ]*(.+?)\\s*;[\\t ]*");
        Matcher m = propPat.matcher(testTypeScript);

        int count = 0;
        while (m.find()) {
            count++;
            System.out.println("Property " + count + " found:");
            System.out.println("  Name: " + m.group(2));
            System.out.println("  Type: " + m.group(4));
            System.out.println("  Match start: " + m.start() + ", end: " + m.end());

            // Test the line extraction logic
            int matchStart = m.start();
            int matchEnd = m.end();

            // Find line start
            int lineStart = findLineStart(testTypeScript, matchStart);
            int lineEnd = findLineEnd(testTypeScript, matchStart);

            System.out.println("  Line start: " + lineStart + ", line end: " + lineEnd);

            if (lineStart >= 0 && lineEnd > lineStart) {
                String fullLineText = testTypeScript.substring(lineStart, lineEnd);
                System.out.println("  Full line: '" + fullLineText + "'");

                Pattern javaTypePattern = Pattern.compile("//\\s*javaType:\\s*([A-Za-z0-9_.]+)");
                Matcher javaTypeMatcher = javaTypePattern.matcher(fullLineText);
                if (javaTypeMatcher.find()) {
                    System.out.println("  JavaType hint found: " + javaTypeMatcher.group(1));
                } else {
                    System.out.println("  No JavaType hint found on this line");
                }
            }
            System.out.println();
        }

        if (count == 0) {
            System.out.println("No properties found!");
        }
    }

    private static int findLineStart(String body, int fromPos) {
        for (int i = fromPos - 1; i >= 0; i--) {
            if (body.charAt(i) == '\n') {
                return i + 1;
            }
        }
        return 0; // Beginning of file
    }

    private static int findLineEnd(String body, int fromPos) {
        for (int i = fromPos; i < body.length(); i++) {
            if (body.charAt(i) == '\n') {
                return i;
            }
        }
        return body.length();
    }
}
