
# Create Block Type

Dieses Dokument beschreibt wie man einen neuen Block-Type erstellt.
Ein Block-Type muss an jedem BLlock in der Welt angegeben werden um dessen Eigenschaften zu definieren.

## Block-Type Typen

Block Typen gibt es fü® verschiedene Aufgaben. Der BlockTypeType wird an BlockType.type angegeben.

* GROUND(1) - Dieser Type wird genutzt um Bodenflächen zu definieren. Er beeinflusst die minimale Höhe der Welt und die Ground Height.
* WATER(2) - Wird fü® Wasser genutzt. Blöcke mit diesem Type werden als Wasserflächen interpretiert und beeinflusst die Water Höhe der Welt.
* PLANT(3) - Wird genutzt um Pflanzen zu definieren. Blöcke mit diesem Type werden als Pflanzen interpretiert und beeinflussen die Vegetation der Welt.
* PLANT_PART(4) - Wird genutzt um Teile von Pflanzen zu definieren, wie z.B. Blätter oder Stängel. Nur für Pfanzen die aus mehreren Teilen bestehen.
* STRUCTURE(5) - Wird genutzt um Strukturen zu definieren, wie z.B. Gebäude oder Felsen. - Strukturen sind vor allem in Modellen/Schematics wichtig.
* DECORATION(6) - Wird genutzt um Dekorative Elemente zu definieren, meist Billboards, wie z.B. Kerzenständer, kleine Steine, etc.
* UTILITY(7) - Wird genutzt um nützliche Elemente zu definieren, wie z.B. Leitern, Zäune, etc.
* LAVA(8) - Wird genutzt für Lava Blöcke. Blöcke mit diesem Type werden als Lavaflächen interpretiert und beeinflussen die Wasser Höhe der Welt.
* WINDOW(9) - Wird genutzt für Fenster Blöcke. Blöcke mit diesem Type werden als Fenster interpretiert. - mit Funktionen wie Open / Close
* DOOR(10) - Wird genutzt für Tür Blöcke. Blöcke mit diesem Type werden als Türen interpretiert. - mit Funktionen wie Open / Close
* WALL(11) - Wird genutzt für Wand Blöcke. Blöcke mit diesem Type werden als Wände interpretiert. Diese sind spezielle Strukturen und ähnlich zu Strukturen
* ROOF(12) - Wird genutzt für Dach Blöcke. Blöcke mit diesem Type werden als Dächer interpretiert. Diese sind spezielle Strukturen und ähnlich zu Strukturen
* PATH(13) - Wird genutzt für Pfad und Strassen Blöcke. Blöcke mit diesem Type werden als Pfade interpretiert. Diese beeinflussen die Begehbarkeit der Welt.
* FENCE(14) - Wird genutzt für Zaun Blöcke. Blöcke mit diesem Type werden als Zäune interpretiert. Diese beeinflussen die Begehbarkeit der Welt.
* STAIRS(15) - Wird genutzt für Treppen Blöcke. Blöcke mit diesem Type werden als Treppen interpretiert. Diese beeinflussen die Begehbarkeit der Welt.
* RAMP(16) - Wird genutzt für Rampen Blöcke. Blöcke mit diesem Type werden als Rampen interpretiert. Diese beeinflussen die Begehbarkeit der Welt.
* BRIDGE(17) - Wird genutzt für Brücken Blöcke. Blöcke mit diesem Type werden als Brücken interpretiert. Diese beeinflussen die Begehbarkeit der Welt.
* LIGHT(18) - Wird genutzt für Lichtquellen Blöcke. Blöcke mit diesem Type werden als Lichtquellen interpretiert. Diese beeinflussen die Beleuchtung der Welt.
* BLOCK(19) - Wird genutzt für generische Blöcke. Blöcke mit diesem Type werden als normale Blöcke interpretiert.
* OTHER(99) - Wird genutzt für alle anderen Blöcke die nicht in eine der anderen Kategorien passen.

## Block Shapes

Blocks können verschiedene Formen haben, die das Aussehen bestimmen und die Wirkung von Offsets beeinflussen.

* INVISIBLE(0) - Unsichtbarer Block.
* CUBE(1) - Klassischer Block als Würfel.
* CROSS(2) - Kreuzförmiger Block, oft für Pflanzen verwendet. Zwei gekreuzte Flächen.
* HASH(3) - Gitterförmiger Block, oft für dekorative Zwecke verwendet. Oder für Kaktusse.
* MODEL(4) - Zeigen ein 3D-Modell anstelle einer einfachen Form. Als Texture wird der Pfad zu einer Model-Datei angegeben.
* GLASS(5) - Block der eine Glass Block Zeigt. Keine Texture.
* GLASS_FLAT(6) - Flacher Glass Block. Kieine Texture.
* FLAT(7) - Flacher Block, oft für Bodenbeläge verwendet. Zeigt eine flache Textur. Nur die TOP Seite/Texture wird angezeigt.
* SPHERE(8) - Kugelförmiger Block. - Kugel, nur eine Texture
* CYLINDER(9) - Zylindrischer Block. - Zylinder, TOP Texture für Deckel, SIDE Texture für Seitenfläche
* STEPS(11) - Block in Form von Stufen. - Stufenförmiger Block, TOP Texture für obere Fläche, SIDE Texture für Seitenflächen
* STAIR(12) - Block in Form einer Treppe. - Treppenförmiger Block, TOP Texture für obere Fläche, SIDE Texture für Seitenflächen
* BILLBOARD(13) - Block in Form eines Billboards. - Zeigt eine flache Textur, die sich immer zur Kamera dreht. z.b. Für Pflanzen oder Decorations
* SPRITE(14) - Es werden mehrere Sprites angezeigt, die sich immer zur Kamera drehen. - Für komplexere Pflanzen oder Dekorationen, z.B. Wiese
* (Deprecated: FLAME(15))
* (OCEAN(16) - Wird nur für Ozean genutzt)
* (Deprecated: OCEAN_COAST(17))
* (Deprecated: OCEAN_MAELSTROM(18))
* (Deprecated: RIVER(19))
* (Deprecated: RIVER_WATERFALL(20))
* (Deprecated: RIVER_WATERFALL_WHIRLPOOL(21))
* WATER(22) - Wird für Wasser genutzt. Zeigt eine animierte Wasser Textur. Benoetigt Texture.
* (Deprecated: LAVA(23))
* FOG(24) - Zeigt einen Fog Block an
* THIN_INSTANCES(25) - Zeigt viele Instanzen an, aber ist immer zur kammer gerichtet - auch Y-Achse
* WALL(26) - Zeigt eine wand an die am Rand der Blocks gebildet wird. Durch Face Visibility wird defineirt welche Seiten sichtbar sind.
* FLIPBOX(27) - Zeigt eine Animation an.
* (ITEM(28) - Wird genutzt um Items im Spiel anzuzeigen, ist ein Billboard mit Item Textur)
* BUSH(29) - Zeigt einen Busch an.

## Collection

Note: Es gibt Collections die vor dem Pfad oder der Id angegeben werden und mit Doppelpunkten getrennt sind.
z.b. "collection_name:path/to/texture"

Collections sind Gruppen von Assets/Texturen oder Modellen die zusammengehören.

* 'w' - World Collection. Sind direkt an jeder Welt verfügbar.
* 'r' - Region Collection. Sind an jede Region gebunden. Jede Welt ist in einer Region, Welten teilen sich die gleiche Region.
* 'rp' - Region Public Collection 'rp'. Hier werden Assets gespeichert die auch ohne Login verfügbar sind.
* 'm' - Shared Collection 'm'. Hier werden milecraft ähnliche Texturen, BlockTypen und Modelle gespeichert die in vielen Welten genutzt werden.
* 'n' - Shared Nimbus Collection 'n'. Hier werden Nimbus Basis Texturen, BlockTypen und Modelle gespeichert die in vielen Welten genutzt werden. z.b. n:0 - Air Block oder n:o - Ocean Water Block.
* 'p' - Shared Public Collection 'p'. Hier werden Public Assets gespeichert die auch ohne Login verfügbar sind.

## Texturen

Texturen haben einen Pfad der auch die Collection beinhaltet. z.b. "w:textures/blocks/grass.png" - ist eine Texture in der World Collection im Pfad textures/blocks/grass.png

Texturen können Transparent sein: z.B. wenn Leaves oder Pflanzen dargestellt werden sollen. Ground Blocks sollen nicht Transparent sein.

Texturen können BackFaceCulling haben: Wenn BackFaceCulling aktiviert ist, werden die Rückseiten der Texturen nicht gerendert. Dies ist nützlich für Blöcke wie Pflanzen oder Blätter, bei denen die Rückseite nicht sichtbar sein muss und somit die Leistung verbessert werden kann.

Fü® verschiedene Seiten können unterschiedliche Texturen angegeben werden, dabei gibt es Grupperungen die durch spezialisierung überschrieben werden:

* ALL: Gilt für alle Seiten
* SIDE: Gilt für alle Seiten außer TOP und BOTTOM
* TOP: Gilt nur für die obere Seite
* BOTTOM: Gilt nur für die untere Seite
* FRONT: Gilt nur für die Vorderseite
* BACK: Gilt nur für die Rückseite
* LEFT: Gilt nur für die linke Seite
* RIGHT: Gilt nur für die rechte Seite

```json
textures: {
    "ALL": {
      path: "w:textures/blocks/dirt.png",
      transparent: false,
      backFaceCulling: false
    },
    "TOP": {
        ...
    }
}
```

## Block Anlegen

* Wenn die BlcokId bekoannt ist, dann prüfe ob der BlockType bereits existiert. Falls ja, wird ein Fehler ausgegeben.
* Wähle die passenden Texturen aus oder lege neue an.
* Rufe die create....BlockType Funktion auf und übergebe die Parameter.

z.b. createCubeBlockType(...) oder createBillboardBlockType(...)

## Texture aussuchen

Suche mit der Asset-Suche in der gewünschten Collection die passenden Texturen aus.

## Texture neu anlegen

Lasse mit dem asset-image-generator eine neue Texture erstellen und speichere diese in der gewünschten Collection ab.

## Cube Block 

Vereinfachte Funktion um einen Würfel Block zu erstellen: createCubeBlockType(...)

* blockTypeId: Id des BlockTypes - Eindeutig. Falls bereits existiert wird ein Fehler ausgegeben
* title: Name des BlockTypes
* description: Beschreibung des BlockTypes
* textures: Texturen Struktur wie oben beschrieben
* type: blockTypeType: Welcher Type der Block hat (GROUND, WATER, STRUCTURE, etc.)
* solid: Ob der Block solide ist oder man durchlaufen kann
* autoJump: Ob der Spieler automatisch auf den Block springen kann - bei Ground Types sinnvoll

Vor dem Erstellen muessen die Textureen ausgewählt oder neu angelegt werden.

## Billboard Block

Vereinfachte Funktion um einen Billboard Block zu erstellen: createBillboardBlockType(...)

* blockTypeId: Id des BlockTypes - Eindeutig. Falls bereits existiert wird ein Fehler ausgegeben
* title: Name des BlockTypes
* description: Beschreibung des BlockTypes
* texture: Es wird nur eine Texture angegeben die für das Billboard, die Texture ist immer Transparent und hat BackFaceCulling aktiviert (wird nie von hinten angezeigt)
* type: blockTypeType: Welcher Type der Block hat (GROUND, WATER, STRUCTURE, etc.) - meist DECORATION sinnvoll
* solid: Ob der Block solide ist oder man durchlaufen kann, bei Billboards meist false
* autoJump: Ob der Spieler automatisch auf den Block springen kann - bei Ground Types sinnvoll, bei Billboards meist false

