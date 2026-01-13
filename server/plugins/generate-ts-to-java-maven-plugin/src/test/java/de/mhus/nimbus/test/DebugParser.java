package de.mhus.nimbus.test;

import de.mhus.nimbus.tools.generatets.ts.TsParser;
import de.mhus.nimbus.tools.generatets.ts.TsModel;
import de.mhus.nimbus.tools.generatets.ts.TsSourceFile;
import de.mhus.nimbus.tools.generatets.ts.TsDeclarations;

import java.io.File;
import java.util.Arrays;

public class DebugParser {

    public static void main(String[] args) throws Exception {
        TsParser parser = new TsParser();

        // Test the parser with the actual ScrawlStep.ts file
        File scrawlStepFile = new File("../client_shared_src/scrawl/ScrawlStep.ts");
        System.out.println("Parsing file: " + scrawlStepFile.getAbsolutePath());
        System.out.println("File exists: " + scrawlStepFile.exists());

        if (scrawlStepFile.exists()) {
            TsModel model = parser.parse(Arrays.asList(scrawlStepFile.getParentFile().getParentFile()));

            System.out.println("Parsed " + model.getFiles().size() + " files");

            for (TsSourceFile file : model.getFiles()) {
                if (file.getPath().contains("ScrawlStep.ts")) {
                    System.out.println("File: " + file.getPath());

                    for (TsDeclarations.TsInterface intf : file.getInterfaces()) {
                        if ("StepWait".equals(intf.name)) {
                            System.out.println("  Interface: " + intf.name);

                            for (TsDeclarations.TsProperty prop : intf.properties) {
                                System.out.println("    Property: " + prop.name);
                                System.out.println("      Type: " + prop.type);
                                System.out.println("      JavaTypeHint: " + prop.javaTypeHint);
                                System.out.println("      Optional: " + prop.optional);
                            }
                        }
                    }
                }
            }
        }
    }
}
