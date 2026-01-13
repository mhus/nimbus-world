
# TS 2 JAVA

super, wenn ich in server/tools/generate-ts-to-java-maven-plugin/evaluate jetzt 'mvn clean package' aufrufe, geht das. Jetzt soll EvaluatePluginIT so umgestellt werden, das es das plugin in evaluate startet, aber aus dem java context heraus, also den aufruf 'simuliert' und dananch soll 'mvn clean package' in evaluate ausgefuegrt werden und geprueft werden ob alles compiliert werden kann (return code von mvn)

Das package der erstellten java dateien soll den relativen ordner der TS source uebenehmen. In der config soll definiert werden in welche package welche source kommen soll. z.b. source die endet mit types soll in package...

Die java klassen werden jetzt angelegt. jetzt sollten aus dem typescript aber auch noch parameter uebernommen werden in die klassen.

ok, also typescript interfaces werden nicht als interfaces, sondern als klassen mit parametern in java umgewandelt. Auch ts types. das wurde in scripts/ts-to-java-generator.js schon besser umgesetzt. 

Jetzt noch alle parameter private und die lombok annotation "@Data" und "@Builder"

Ich brauche noch ein mapping von Typen (z.b. ClientType) zu einem Typ mit package, z.b. ClientType -> de.mhus.nimbus.types.ClientType diese sollen in der Configuration angegeben werden und dann im JavaModel ersetzt werden.

```text
Kurzfassung der Lösung: Ich habe das Maven‑Plugin so erweitert, dass packageRules auch dann korrekt greifen, wenn Sie nicht den einen TS‑Wurzelordner, sondern mehrere einzelne Unterordner in <sourceDirs> konfigurieren. Bisher wurde nur relativ zum einzelnen sourceDir gematcht; war der Ordner selbst die Wurzel (z. B. network/messages), war relDir leer und die Regel „network/messages“ traf nicht. Jetzt werden Regeln sowohl gegen relDir (relativ zum jeweils passenden sourceDir) als auch gegen relDirCommon (relativ zum gemeinsamen Elternordner aller sourceDirs) geprüft. Zusätzlich wird die Package‑Ableitung (basePackage + relativ) ebenso mit dem gemeinsamen Elternordner fallbacked.Damit können Sie z. B. so konfigurieren:und in ts-to-java.yaml Regeln wie: packageRules:•dirEndsWith: types pkg: de.mhus.nimbus.generated.types•dirEndsWith: configs pkg: de.mhus.nimbus.generated.configs•dirEndsWith: network/messages pkg: de.mhus.nimbus.generated.network.messagesVorgehen zum Testen:1)Plugin neu installieren: mvn -q -pl tools/generate-ts-to-java-maven-plugin -am clean install2)Modul „generated“ (oder Ihr Zielmodul) bauen: mvn -q -pl generated -am clean package -DskipTestsFragen:•Haben Ihre angegebenen sourceDirs einen gemeinsamen Elternordner (z. B. …/client/shared/src)? Falls nein, sagen Sie mir bitte die konkreten Pfade, dann passe ich die Heuristik ggf. an.•Sollen Regeln auch greifen, wenn dirEndsWith exakt der Root‑Ordner ist (z. B. wenn sourceDir schon „network/messages“ ist)? Das ist jetzt durch den relDirCommon‑Fallback abgedeckt.
```

kannst du oben in jede javaklasse noch den dateiname (nicht pfad) der ts datei und originaler ts typ: 'interface FooBar' als kommentar rein schreiben.

```text
Added header comments with TS filename and original TS type in all Java classes. Enhanced exclusion of subdirectories for certain suffixes to prevent invalid Java generation. Verified changes with tests and successful Maven build.
```

An den enums fehlen die werte. die muessen auch engelegt werden. moeglichst mit dem tsIndex. Etwa so: enum T { VALUE1(1), ... ; T(tsIndex) {this.tsIndex = tsIndex} @Getter int tsIndex;    }

```text
Enum value generation with tsIndex was implemented and tested successfully. The parser extracts enum member names, and generated Java enums include tsIndex and getter. Integration tests and full project build passed, ensuring correct compilation.
```

## Enum-Wert-Behandlung (Dezember 2025 Update)

### Problem behoben: String-Werte statt erfundene Integer

**Issue**: Das Plugin generierte erfundene Integer-Werte (1, 2, 3...) für TypeScript String-Enums anstatt der tatsächlichen String-Werte ('login', 'logout', etc.).

**Lösung**: Implementierung einer intelligenten Typ-Erkennung:
- **String-Enums**: Verwenden `String tsIndex` mit korrekten String-Werten
- **Numerische Enums**: Verwenden `int tsIndex` mit korrekten numerischen Werten  
- **Gemischte Enums**: Fallback zu `String tsIndex` mit allen Werten als Strings

### Implementierung

1. **TsDeclarations.TsEnumValue** - Neue Klasse für Name+Wert Paare
2. **TsParser.extractEnumValuesAndAssignments()** - Regex-basierter Parser für Enum-Zuweisungen
3. **JavaType.enumValuesWithAssignments** - Speicherung der Wert-Zuweisungen
4. **JavaModelWriter** - Intelligente Typ-Erkennung und Code-Generierung

### Test-System erweitert

**Permanente Test-Enums** in `evaluate/ts/network/MessageTypes.ts`:
- `MessageType` (String-Werte) → `String tsIndex`
- `Priority` (Numerische Werte) → `int tsIndex`  
- `MixedEnum` (Gemischte Typen) → `String tsIndex` (Fallback)

**Automatisierte Validierung** in `EvaluatePluginIT.validateEnumTypes()`:
- Prüfung der korrekten tsIndex-Typen
- Verifikation der Wert-Korrektheit
- Kontinuierliche Regression-Tests

### Verwendung des Test-Systems

```bash
# Plugin entwickeln/erweitern:
# 1. Test-TypeScript in evaluate/ts/ hinzufügen
# 2. Plugin-Code implementieren  
# 3. Tests ausführen
mvn clean install

# Nur Enum-Tests prüfen
cd evaluate && mvn clean generate-sources
ls src/main/java/de/mhus/nimbus/evaluate/generated/network/

# Integration Tests
mvn test -Dtest=EvaluatePluginIT
```

Das evaluate-System dient als permanente Testumgebung für:
- Neue Plugin-Features
- Regressions-Tests
- Code-Generierung-Verifikation
- Kontinuierliche Integration

Kannst du alle Parameter die optional sind in ts (parameter?) mit der Jackson annotation @JsonInclude(JsonInclude.Include.NON_NULL) damit beim serilisieren diese weggelassen werden

```text
Annotated Java fields from optional TypeScript parameters with @JsonInclude(NON_NULL) to omit them during JSON serialization. Added jackson-annotations dependency to relevant modules to support compilation. Verified changes with integration tests and full project build, all succeeded.
```

An jede klasse soll die jackson annotation @JsonIgnoreProperties(ignoreUnknown = true) damit weitere felder nicht zu fehlern fuehren.

```text
Added @JsonIgnoreProperties(ignoreUnknown = true) to all generated Java classes to ignore unknown JSON fields. Updated JavaModelWriter to emit the annotation for all class kinds. Verified changes via integration test which passed without regressions.
```

In der Config soll es moeglich sein eine Klasse (full qualified) anzugeben, die als abgeleitete klasse fuer class anstelle von Object genutzt wird.

```text
Added configuration to specify a fully qualified base class for generated Java classes without explicit superclasses. Updated plugin logic to apply this base class after generation and type resolution. Verified compatibility and successfully passed all tests.
```

Wenn in ts ein interface ein anderes ableitet, dann muss das auch in den java klassen so umgesetzt werden. Wenn das abgeleitete interface unbekannt ist (weil es nicht mit bei der generation dabei ist), dann soll das im header kommentar vermerkt werden und in der konfiguration die moeglichkeit geben diese mit einer konfigurierten klasse zu ersetzen, ansonsten eben Object/default ableitung wie bisher.

```text
Added parsing of TS interface extends, propagated into Java model, and Mojo logic to either keep known inheritance, map unknown bases via new interfaceExtendsMappings, or drop and record unresolved to header. Test failed because extending generated classes lacked no-args superclass constructors, causing compile errors. Next: add Lombok @NoArgsConstructor to all generated classes to provide default super() and rerun tests.
```

Evtl sollte man @SuperBuilder anstelle von @Builder nutzen, dann kan nauch bei ableitungen wieder das builder pattern genutzt werden.

```text
The solution replaces Lombok's @Builder with @SuperBuilder to support inheritance in builder patterns. It updates JavaModelWriter to emit @SuperBuilder, add necessary constructors, and suppresses explicit "extends Object" to avoid compilation errors. The changes were validated by successful test execution and Maven build without errors.
```

Du kannst in EvaluatePluginIT den test erweitern um die neue Features zu testen.
Dazu kannst du auch unter evaluate/ts/features neue ts dateien hinzufuegen
die dann zu java generiert werden. In den generierten dateien kann das
Ergebnis validiert werden.
Du kannst auch evaluate/ts-to-java.yaml aendern um die neuen Features zu konfigurieren.
---
Du kannst auch mehrere tests mit verscheidenen source directories und config files erstellen falls man die tests trennen sollte 
---
Definiere an jede generierte klasse die annotation 'org.springframework.aot.hint.annotation.Reflective' - 
Besser: in der config soll es einen abschitt geben in dem man fuer klassen additional annotations definieren kann, die dann an jede klasse (nicht enum) angehaengt werden. dann kann ich dort org.springframework.aot.hint.annotation.Reflective eintragen.

```text
Die Lösung implementiert konfigurierbare Zusatz-Annotationen für alle generierten Klassen außer Enums. Die Annotation 'org.springframework.aot.hint.annotation.Reflective' kann nun über die Konfiguration additionalClassAnnotations hinzugefügt werden. Tests bestätigen die erfolgreiche Anwendung der Annotation in generierten Klassen.
```

Du kannst jetzt das anhaengen von '@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)' wieder entfernen, das kann ich in zukunft via config machen.
---
In der config soll es auch
- additionalFieldAnnotations - an alle felder
- additionalOptionalFieldAnnotations - nur optional felder
- additionalNonOptionalFieldAnnotations - nur nicht optional felder
geben. Die dann entsprechend angehaengt werden.

```text
Neue Konfigurationsoptionen für Feld-Annotationen wurden hinzugefügt und korrekt angewendet. Der JavaModelWriter gibt die Annotationen entsprechend aus. Alle Tests laufen erfolgreich und der Build kompiliert ohne Fehler.
```

Du kannst jetzt das Anhängen von '@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)' wieder entfernen, das kann ich in zukunft via config machen.

---
Dieser ts type 'export type ColorHex = string;' wird als

public class ColorHex {
}

Java generiert. Sollte aber:

public class ColorHex {
    private String value;
}

werden. Das sonst kein Wert zuweisen kann.
---
