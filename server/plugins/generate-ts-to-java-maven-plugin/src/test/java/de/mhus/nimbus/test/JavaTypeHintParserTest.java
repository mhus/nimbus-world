package de.mhus.nimbus.test;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test für das Parsen von javaType-Hints aus TypeScript-Zeilen
 */
public class JavaTypeHintParserTest {

    @Test
    public void testParseJavaTypeHint_BasicCase() {
        String line = "  seconds: number; // javaType: int";
        String result = parseJavaTypeHintFromLine(line);
        assertEquals("int", result);
    }

    @Test
    public void testParseJavaTypeHint_WithExtraSpaces() {
        String line = "  duration: number;   //   javaType:    long   ";
        String result = parseJavaTypeHintFromLine(line);
        assertEquals("long", result);
    }

    @Test
    public void testParseJavaTypeHint_FullyQualifiedType() {
        String line = "  timestamp: number; // javaType: java.time.Instant";
        String result = parseJavaTypeHintFromLine(line);
        assertEquals("java.time.Instant", result);
    }

    @Test
    public void testParseJavaTypeHint_NoComment() {
        String line = "  name: string;";
        String result = parseJavaTypeHintFromLine(line);
        assertNull(result);
    }

    @Test
    public void testParseJavaTypeHint_CommentButNoJavaType() {
        String line = "  id: number; // some other comment";
        String result = parseJavaTypeHintFromLine(line);
        assertNull(result);
    }

    @Test
    public void testParseJavaTypeHint_MultipleComments() {
        String line = "  value: number; // some comment // javaType: BigDecimal";
        String result = parseJavaTypeHintFromLine(line);
        assertEquals("BigDecimal", result);
    }

    @Test
    public void testParseJavaTypeHint_WithDocComment() {
        String line = "  /** Duration in seconds */ seconds: number; // javaType: int";
        String result = parseJavaTypeHintFromLine(line);
        assertEquals("int", result);
    }

    @Test
    public void testParseJavaTypeHint_CaseInsensitive() {
        String line = "  count: number; // JAVATYPE: Integer";
        String result = parseJavaTypeHintFromLine(line);
        assertEquals("Integer", result);
    }

    @Test
    public void testParseJavaTypeHint_DifferentFormats() {
        // Test verschiedene Varianten der Schreibweise
        assertEquals("int", parseJavaTypeHintFromLine("x: number; //javaType:int"));
        assertEquals("long", parseJavaTypeHintFromLine("x: number; // javaType : long"));
        assertEquals("String", parseJavaTypeHintFromLine("x: string; //javaType=String"));
    }

    @Test
    public void testParseJavaTypeHint_ComplexTypes() {
        String line = "  items: Item[]; // javaType: List<ItemDto>";
        String result = parseJavaTypeHintFromLine(line);
        assertEquals("List<ItemDto>", result);
    }

    @Test
    public void testDebugSpecificCase() {
        String input = "x: number; // javaType : long";
        System.out.println("Debug: input = '" + input + "'");

        int commentStart = input.lastIndexOf("//");
        System.out.println("Debug: commentStart = " + commentStart);

        String comment = input.substring(commentStart + 2).trim();
        System.out.println("Debug: comment = '" + comment + "'");

        String lowerComment = comment.toLowerCase();
        System.out.println("Debug: lowerComment = '" + lowerComment + "'");

        int javaTypeStart = lowerComment.indexOf("javatype");
        System.out.println("Debug: javaTypeStart = " + javaTypeStart);

        String result = parseJavaTypeHintFromLine(input);
        System.out.println("Debug: result = '" + result + "'");

        assertEquals("long", result);
    }

    @Test
    public void testParseJavaTypeHint_EmptyLine() {
        String line = "";
        String result = parseJavaTypeHintFromLine(line);
        assertNull(result);
    }

    @Test
    public void testParseJavaTypeHint_OnlyWhitespace() {
        String line = "   ";
        String result = parseJavaTypeHintFromLine(line);
        assertNull(result);
    }

    /**
     * Die zu testende Funktion - robust und einfach zu verstehen
     */
    private String parseJavaTypeHintFromLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }

        // Finde den letzten // Kommentar in der Zeile
        int commentStart = line.lastIndexOf("//");
        if (commentStart == -1) {
            return null;
        }

        String comment = line.substring(commentStart + 2).trim();
        String lowerComment = comment.toLowerCase();

        // Suche nach "javatype" (case insensitive) gefolgt von : oder =
        int javaTypeStart = lowerComment.indexOf("javatype");
        if (javaTypeStart == -1) {
            return null;
        }

        // Finde den Separator nach "javatype"
        int separatorPos = javaTypeStart + "javatype".length();

        // Überspringe Leerzeichen
        while (separatorPos < lowerComment.length() && Character.isWhitespace(lowerComment.charAt(separatorPos))) {
            separatorPos++;
        }

        // Prüfe auf : oder =
        if (separatorPos >= lowerComment.length()) {
            return null;
        }

        char separator = lowerComment.charAt(separatorPos);
        if (separator != ':' && separator != '=') {
            return null;
        }

        // Extrahiere den Typ nach dem Separator
        int typeStart = separatorPos + 1;

        // Überspringe Leerzeichen nach dem Separator
        while (typeStart < comment.length() && Character.isWhitespace(comment.charAt(typeStart))) {
            typeStart++;
        }

        if (typeStart >= comment.length()) {
            return null;
        }

        String typeStr = comment.substring(typeStart);

        // Entferne nachfolgende Kommentare (falls vorhanden)
        int nextComment = typeStr.indexOf("//");
        if (nextComment != -1) {
            typeStr = typeStr.substring(0, nextComment).trim();
        }

        // Entferne Leerzeichen und Semikolon am Ende
        typeStr = typeStr.replaceAll("[\\s;]*$", "");

        return typeStr.isEmpty() ? null : typeStr;
    }
}

