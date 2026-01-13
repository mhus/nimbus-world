# TypeScript to Java Generator Maven Plugin

Ein Maven-Plugin zur automatischen Generierung von Java-Klassen aus TypeScript-Definitionen für das Nimbus Voxel Engine Projekt.

## Überblick

Das `generate-ts-to-java-maven-plugin` parst TypeScript-Dateien und generiert entsprechende Java-Klassen mit Lombok-Annotationen. Es unterstützt Interfaces, Klassen, Enums und Type Aliases.

## Verwendung

```xml
<plugin>
    <groupId>de.mhus.nimbus</groupId>
    <artifactId>generate-ts-to-java-maven-plugin</artifactId>
    <version>${project.version}</version>
    <executions>
        <execution>
            <id>generate-ts-to-java</id>
            <phase>generate-sources</phase>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <sourceDirs>
                    <sourceDir>${project.basedir}/../client_shared_src</sourceDir>
                </sourceDirs>
                <outputDir>${project.basedir}/src/main/java</outputDir>
                <modelFile>${project.basedir}/model.json</modelFile>
                <configFile>${project.basedir}/ts-to-java.yaml</configFile>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## Enum-Wert-Behandlung

### Problem gelöst (Dezember 2025)

**Ursprüngliches Problem**: Das Plugin generierte erfundene Integer-Werte für TypeScript String-Enums anstatt der tatsächlichen Werte.

**Beispiel TypeScript**:
```typescript
export enum MessageType {
  LOGIN = 'login',
  LOGOUT = 'logout',
  PING = 'p'
}
```

**Alte (fehlerhafte) Java-Generierung**:
```java
public enum MessageType {
    LOGIN(1),           // ❌ Erfundener Wert
    LOGOUT(2),          // ❌ Erfundener Wert
    PING(3);            // ❌ Erfundener Wert
    
    private final int tsIndex;
}
```

**Neue (korrekte) Java-Generierung**:
```java
public enum MessageType {
    LOGIN("login"),     // ✅ Korrekter String-Wert
    LOGOUT("logout"),   // ✅ Korrekter String-Wert  
    PING("p");          // ✅ Korrekter String-Wert
    
    private final String tsIndex;
}
```

### Intelligente Typ-Erkennung

Das Plugin erkennt automatisch den Typ der Enum-Werte und generiert entsprechend:

#### 1. String-Enums
```typescript
export enum MessageType {
  LOGIN = 'login',
  PING = 'p'
}
```
→ Java: `String tsIndex` und String-Konstruktor

#### 2. Numerische Enums
```typescript
export enum Priority {
  LOW = 0,
  MEDIUM = 1,
  HIGH = 2,
  CRITICAL = 5
}
```
→ Java: `int tsIndex` und int-Konstruktor

#### 3. Gemischte Enums
```typescript
export enum MixedEnum {
  STRING_VAL = 'text',
  NUMERIC_VAL = 42,
  ANOTHER_STRING = 'hello'
}
```
→ Java: `String tsIndex` (Fallback) und alle Werte als Strings

### Implementierungsdetails

#### Architektur-Änderungen

1. **TsDeclarations.TsEnumValue** - Neue Klasse zur Speicherung von Name und Wert
   ```java
   public static class TsEnumValue {
       public String name;
       public String value;
   }
   ```

2. **TsParser.extractEnumValuesAndAssignments()** - Neue Methode zum Parsen von Enum-Werten
   ```java
   private void extractEnumValuesAndAssignments(String body, 
       List<TsDeclarations.TsEnumValue> out)
   ```
   - Regex-Pattern zur Erfassung von Namen und zugewiesenen Werten
   - Unterstützt String-Literale (`'value'`, `"value"`) und numerische Werte

3. **JavaType.enumValuesWithAssignments** - Neue Eigenschaft zur Speicherung der Wert-Zuweisungen

4. **JavaModelWriter** - Erweiterte Typ-Erkennung und Code-Generierung
   - Automatische Erkennung von String-, numerischen und gemischten Enum-Typen
   - Intelligente Auswahl zwischen `String tsIndex` und `int tsIndex`

#### Code-Generierung-Logik

```java
// Typ-Erkennung in JavaModelWriter
boolean hasStringValues = false;
boolean hasNumericValues = false;

for (TsDeclarations.TsEnumValue enumValue : valuesWithAssignments) {
    String value = enumValue.value;
    try {
        Integer.parseInt(value.trim());
        hasNumericValues = true;
    } catch (NumberFormatException e) {
        hasStringValues = true;
    }
}

// String-Typ verwenden wenn gemischt oder nur Strings
boolean useStringType = hasStringValues || (!hasNumericValues && !hasStringValues);
```

## Testsystem

### Evaluate-Modul

Das `evaluate/`-Untermodul dient als permanentes Testsystem für das Plugin:

```
plugins/generate-ts-to-java-maven-plugin/
├── src/                    # Plugin-Quellcode
├── evaluate/              # Test-System
│   ├── ts/               # Test-TypeScript-Dateien
│   └── src/main/java/    # Generierte Java-Dateien (Test-Output)
```

### Test-Enums für Enum-Wert-Behandlung

In `evaluate/ts/network/MessageTypes.ts` sind permanente Test-Enums definiert:

```typescript
// String-Enum (Haupt-Use-Case)
export enum MessageType {
  LOGIN = 'login',
  PING = 'p',
  // ... weitere
}

// Numerisches Enum (Test für int tsIndex)
export enum Priority {
  LOW = 0,
  MEDIUM = 1,
  HIGH = 2,
  CRITICAL = 5,
}

// Gemischtes Enum (Test für String-Fallback)
export enum MixedEnum {
  STRING_VAL = 'text',
  NUMERIC_VAL = 42,
  ANOTHER_STRING = 'hello',
}
```

### Automatisierte Test-Validierung

Im `EvaluatePluginIT` Test wird automatisch validiert:

```java
private static void validateEnumTypes(File outJavaDir) throws IOException {
    // MessageType: String tsIndex für String-Werte
    File messageTypeFile = new File(outJavaDir, "de/mhus/nimbus/evaluate/generated/network/MessageType.java");
    if (messageTypeFile.exists()) {
        String content = Files.readString(messageTypeFile.toPath());
        assertTrue(content.contains("private final String tsIndex"), 
                  "MessageType should use String tsIndex for string values");
        assertTrue(content.contains("LOGIN(\"login\")"), 
                  "MessageType LOGIN should have correct string value");
    }

    // Priority: int tsIndex für numerische Werte
    File priorityFile = new File(outJavaDir, "de/mhus/nimbus/evaluate/generated/network/Priority.java");
    if (priorityFile.exists()) {
        String content = Files.readString(priorityFile.toPath());
        assertTrue(content.contains("private final int tsIndex"), 
                  "Priority should use int tsIndex for numeric values");
        assertTrue(content.contains("LOW(0)") && content.contains("CRITICAL(5)"), 
                  "Priority should have correct numeric values");
    }

    // MixedEnum: String tsIndex als Fallback
    File mixedFile = new File(outJavaDir, "de/mhus/nimbus/evaluate/generated/network/MixedEnum.java");
    if (mixedFile.exists()) {
        String content = Files.readString(mixedFile.toPath());
        assertTrue(content.contains("private final String tsIndex"), 
                  "MixedEnum should use String tsIndex as fallback for mixed types");
        assertTrue(content.contains("NUMERIC_VAL(\"42\")"), 
                  "MixedEnum should convert numeric values to strings");
    }
}
```

### Tests ausführen

```bash
# Vollständiger Test (Plugin + Integration Tests)
mvn clean test

# Nur spezifischer Test
mvn test -Dtest=EvaluatePluginIT

# Plugin neu installieren und testen
mvn clean install
```

## Plugin erweitern

### 1. Neue TypeScript-Features hinzufügen

**Schritt 1**: `TsDeclarations.java` erweitern
```java
public static class TsNewFeature {
    public String name;
    public List<SomeProperty> properties = new ArrayList<>();
}
```

**Schritt 2**: `TsParser.java` erweitern
```java
private static final Pattern DECL_NEW_FEATURE = Pattern.compile("\\bexport\\s+newfeature\\s+([A-Za-z0-9_]+)\\b");

// In parse()-Methode:
for (NameOccur n : findNamed(src, DECL_NEW_FEATURE, '{')) {
    TsDeclarations.TsNewFeature feature = new TsDeclarations.TsNewFeature();
    feature.name = n.name;
    // ... parsing logic
    file.getNewFeatures().add(feature);
}
```

**Schritt 3**: `JavaGenerator.java` erweitern
```java
// In generate()-Methode:
if (f.getNewFeatures() != null) {
    for (TsDeclarations.TsNewFeature feature : f.getNewFeatures()) {
        JavaType t = new JavaType(feature.name, JavaKind.NEW_KIND, srcPath);
        // ... conversion logic
        jm.addType(t);
    }
}
```

**Schritt 4**: `JavaModelWriter.java` erweitern
```java
// In renderType()-Methode:
if (t.getKind() == JavaKind.NEW_KIND) {
    sb.append("public class ").append(name).append(" {\n");
    // ... generation logic
    sb.append("}\n");
}
```

### 2. Test hinzufügen

**Schritt 1**: Test-TypeScript in `evaluate/ts/` hinzufügen
```typescript
// evaluate/ts/test/NewFeature.ts
export newfeature TestFeature {
    property1: string;
    property2: number;
}
```

**Schritt 2**: Test-Validierung erweitern
```java
// In EvaluatePluginIT.validateXXX():
File newFeatureFile = new File(outJavaDir, "de/mhus/nimbus/evaluate/generated/test/TestFeature.java");
if (newFeatureFile.exists()) {
    String content = Files.readString(newFeatureFile.toPath());
    assertTrue(content.contains("public class TestFeature"), 
              "TestFeature should be generated as class");
    // ... weitere Validierungen
}
```

### 3. Konfiguration erweitern

**Schritt 1**: `Configuration.java` erweitern
```java
public class Configuration {
    private List<String> newFeatureOptions = new ArrayList<>();
    // ... getters/setters
}
```

**Schritt 2**: YAML-Konfiguration erweitern
```yaml
# ts-to-java.yaml
newFeatureOptions:
  - option1
  - option2
```

## Debugging

### Plugin-Entwicklung debuggen

```bash
# Mit Debug-Ausgabe
mvn clean generate-sources -X

# Plugin-spezifische Logs
mvn de.mhus.nimbus:generate-ts-to-java-maven-plugin:1.0.0-SNAPSHOT:generate -X
```

### Generierte Dateien prüfen

```bash
# Model-JSON prüfen
cat target/model.json | jq '.'

# Generierte Java-Dateien
find src/main/java -name "*.java" -exec head -20 {} \;
```

### Test-System verwenden

```bash
# Evaluate-System regenerieren
cd evaluate
mvn clean generate-sources

# Generierte Test-Dateien prüfen
ls -la src/main/java/de/mhus/nimbus/evaluate/generated/
```

## Best Practices

### 1. Rückwärtskompatibilität
- Neue Features sollen bestehende Generierung nicht beeinträchtigen
- Fallback-Verhalten für unbekannte TypeScript-Konstrukte
- Versionierung von generierten Kommentaren

### 2. Test-First Development
- Erst Test-TypeScript in `evaluate/ts/` hinzufügen
- Dann Plugin-Code implementieren
- Automatisierte Validierung erweitern

### 3. Fehlerbehandlung
- Graceful Degradation bei Parse-Fehlern
- Aussagekräftige Fehlermeldungen
- Logging für Debugging

### 4. Performance
- Effiziente Regex-Pattern
- Minimale File-I/O
- Caching wo möglich

## Bekannte Limitierungen

- Komplexe generische TypeScript-Typen werden vereinfacht
- Union Types werden zu `Object` konvertiert
- Dynamische Imports werden ignoriert
- Decorator werden nicht unterstützt

## Mitwirken

1. Feature in `evaluate/ts/` als TypeScript hinzufügen
2. Tests schreiben/erweitern
3. Plugin-Code implementieren
4. Integration Tests validieren
5. Dokumentation aktualisieren

---

*Diese Dokumentation beschreibt den Stand nach der Enum-Wert-Behandlung-Implementierung vom Dezember 2025.*
