import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class TestRegex {
    public static void main(String[] args) {
        String testInput = """
export interface StepWait {
  kind: 'Wait';
  /** Duration in seconds */
  seconds: number; // javaType: int
}
        """;

        // Test the regex pattern
        Pattern propPat = Pattern.compile("(?m)^[\\t ]*(public|private|protected)?[\\t ]*([A-Za-z_$][A-Za-z0-9_$]*)[\\t ]*(\\?)?[\\t ]*:[\\t ]*(.+?)\\s*;([^\\r\\n]*)");
        Matcher m = propPat.matcher(testInput);

        System.out.println("Testing property regex:");
        while (m.find()) {
            System.out.println("Found property:");
            System.out.println("  Full match: '" + m.group(0) + "'");
            System.out.println("  Visibility: '" + m.group(1) + "'");
            System.out.println("  Name: '" + m.group(2) + "'");
            System.out.println("  Optional: '" + m.group(3) + "'");
            System.out.println("  Type: '" + m.group(4) + "'");
            System.out.println("  Comment: '" + m.group(5) + "'");

            // Test javaType extraction
            String comment = m.group(5);
            if (comment != null) {
                Pattern javaTypePattern = Pattern.compile("//\\s*javaType:\\s*([A-Za-z0-9_.]+)");
                Matcher javaTypeMatcher = javaTypePattern.matcher(comment);
                if (javaTypeMatcher.find()) {
                    System.out.println("  JavaTypeHint: '" + javaTypeMatcher.group(1) + "'");
                }
            }
            System.out.println();
        }
    }
}
