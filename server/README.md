# nimbus

## Entwicklung

- asymetrische keys sollen immer vorrang vor symetrischen haben.
- Nutze lombok, nutze @Slf4j, nutze @Getter, @Setter, @RequiredArgsConstructor, @Data, @Builder
- Zur persistierung wird ausschliesslich JPA mti mongodb verwendet.
- Zum Cachen / Kurzfristige schnelle Speicher und Messaging wird redis verwendet.
- Zur Authentifizierung wird JWT verwendet, nur mit ECC Keys.
- Alle module haben eigene prefixe damit diese auch mal in einem server zusammen funktionieren koennen
  - Universe - Prefix U, REST /universe/*
  - Region - Prefix R, REST /region/*
  - World - Prefix W, REST /world/*
  - Shared - Prefix S
- In der REST API wird unterschieden, wer auf die API zugreifen soll, damit die Berechtigungen anders geprueft werden koennen 
  - Im Universe Service der Bereich auf den der Region Service zugreift ist /universe.region/*
  - Im Universe Service der Bereich auf den der User zugreift ist /universe/user/*
  - In Region Service der Bereich auf den der World Service zugreift ist /region/world/*
- REST Controller haben die Aufgabe die Kommunikation zu erledigen. Alles was Business Logik 
  beinhaltet muss in den Services liegen.

## Links

ASCII ART

https://patorjk.com/software/taag/#p=display&f=Sub-Zero&t=editor&x=none&v=4&h=4&w=80&we=false

JWT Tokens

https://www.jwt.io


