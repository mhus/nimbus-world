package test;

import de.mhus.nimbus.tools.generatets.ts.TsParser;
import de.mhus.nimbus.tools.generatets.ts.TsModel;
import de.mhus.nimbus.tools.generatets.ts.TsSourceFile;
import de.mhus.nimbus.tools.generatets.ts.TsDeclarations;

import java.io.File;
import java.util.Arrays;

public class JavaTypeHintTest {

    public static void main(String[] args) throws Exception {
        TsParser parser = new TsParser();

        // Test the parser with our test file
        File testFile = new File("test-typescript.ts");

        TsModel model = parser.parse(Arrays.asList(testFile.getParentFile()));

        System.out.println("Parsed " + model.getFiles().size() + " files");

        for (TsSourceFile file : model.getFiles()) {
            System.out.println("File: " + file.getPath());

            for (TsDeclarations.TsInterface intf : file.getInterfaces()) {
                System.out.println("  Interface: " + intf.name);

                for (TsDeclarations.TsProperty prop : intf.properties) {
                    System.out.println("    Property: " + prop.name);
                    System.out.println("      Type: " + prop.type);
                    System.out.println("      JavaTypeHint: " + prop.javaTypeHint);
                }
            }
        }
    }
}
