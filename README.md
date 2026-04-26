# DispenserGenerator

DispenserGenerator ist ein Minecraft-Paper-Plugin, das aus einem normalen Werfer einen konfigurierbaren Blockgenerator macht. Statt klassische Cobblestone-Generatoren von Hand zu bauen, kann der Spieler spezielle Werfer-Generatoren platzieren, mit Brennstoffen versorgen und dann automatisch verschiedene Blockarten erzeugen lassen.

Das Plugin bringt drei Generator-Typen mit, die jeweils eigene Materialpools besitzen: ein Generator auf Basis des normalen Ofens, ein Generator auf Basis des Räucherofens und ein Generator auf Basis des Schmiedeofens. Jeder Typ erzeugt andere Blöcke und kann über ein eigenes Menü aktiviert, mit Ladung versorgt, in verschiedene Modi geschaltet und mit Verzauberungsbüchern erweitert werden.

## Was das Plugin macht

Ein DispenserGenerator erzeugt automatisch Blöcke vor dem Werfer. Welche Blöcke entstehen, hängt vom Generator-Typ ab. Die Erzeugung läuft in Intervallen und verbraucht dabei Ladungen aus drei Ressourcen:

- `Getrockneter Seetangblock`
- `Knochenblock`
- `Lavaeimer`

Sobald genügend Ladung vorhanden ist und der Generator aktiviert wurde, produziert er im konfigurierten Takt neue Blöcke in seinem Wirkungsbereich. Optional zeigt das Plugin mit Partikeln an, in welchem Bereich der Generator arbeitet.

## Hauptfunktionen

### 1. Drei Generator-Typen

Es gibt drei unterschiedliche Generatoren:

- `Normaler Ofen`
  Erzeugt vor allem Stein- und Overworld-Blöcke wie Stein, Cobblestone, Andesit, Diorit, Granit, Sand, Kies und einige seltenere Varianten wie Obsidian oder moosigen Bruchstein.
- `Räucherofen`
  Erzeugt eher Natur- und organische Blöcke wie Erde, grobe Erde, Wurzelerde, Moosblock, Holzstämme, Bretter, Clay und ähnliche Materialien.
- `Schmiedeofen`
  Erzeugt tiefere, härtere oder nether-nahe Materialien wie Deepslate, Tuff, Basalt, Blackstone und seltenere Varianten wie weinender Obsidian.

Jeder Typ hat eigene Kosten pro Produktionszyklus und eigene Materiallisten in der `config.yml`.

### 2. Automatische Blockerzeugung

Der Generator arbeitet blockweise vor dem Werfer in Blickrichtung des platzierten Dispensers. Er setzt nur dann neue Blöcke, wenn an der Zielposition Luft ist. Bereits belegte Blöcke werden nicht überschrieben.

Die Produktionsgeschwindigkeit und die Zahl der generierten Blöcke pro Zyklus können in der Konfiguration festgelegt werden.

### 3. Zwei Betriebsmodi

Jeder Generator besitzt zwei Modi:

- `Würfel`
  Der Generator arbeitet in einem größeren Bereich vor dem Werfer und füllt diesen nach einem festen Muster mit Blöcken.
- `5er Linie`
  Der Generator erzeugt eine gerade Linie aus bis zu fünf Blöcken nach vorne. Dieser Modus ist besonders nützlich für einfache, kompakte Farmen.

Der Modus kann im Generator-Menü jederzeit umgeschaltet werden.

### 4. Partikel-Vorschau

Wenn die Partikelanzeige aktiviert ist, zeigt das Plugin mit `END_ROD`-Partikeln den aktiven Bereich des Generators an. So kann man direkt sehen, wo Blöcke entstehen werden. Die Vorschau lässt sich im Menü ein- und ausschalten.

### 5. Brennstoff- und Ladungssystem

Der Generator benutzt keine klassische Ofenlogik, sondern ein eigenes Ladungssystem. Die drei Rohstoffe werden als gespeicherte Generator-Ladung verwaltet:

- Seetangblöcke liefern `Kelp-Ladung`
- Knochenblöcke liefern `Knochen-Ladung`
- Lavaeimer liefern `Lava-Ladung`

Lavaeimer werden nur wegen ihrer Lava verbraucht. Leere Eimer bleiben erhalten und werden entweder in das Werfer-Inventar oder zurück an den Spieler gegeben.

### 6. Upgrade-System mit Enchanted Books

Jeder Generator kann über ein separates Upgrade-Menü verbessert werden. Die Upgrades werden durch verzauberte Bücher eingesetzt. Unterstützt werden:

- `Behutsamkeit`
  Empfindliche oder seltene Blöcke bleiben in ihrer besseren Form erhalten.
- `Glück`
  Erhöht die Chance auf Bonus-Drops beim Abbau generierter Blöcke.
- `Reparatur`
  Erzeugt gelegentlich Erfahrungsorbs beim Abbau generierter Blöcke.
- `Effizienz`
  Erhöht die Anzahl der generierten Blöcke pro Zyklus.
- `Haltbarkeit`
  Vergrößert den Wirkungsbereich des Generators.

Das Größen-Upgrade ist intern auf Stufe 3 begrenzt. Daraus ergeben sich aktuell diese Würfelgrößen:

- Stufe 0: `5`
- Stufe 1: `10`
- Stufe 2: `13`
- Stufe 3: `15`

### 7. Persistenz

Alle gesetzten Generatoren werden beim Stoppen des Servers gespeichert und beim Neustart wieder geladen. Position, Generator-Typ, Modus, Ladungen, Aktivstatus und Upgrades bleiben erhalten.

### 8. Administrationsbefehl

Administratoren können Generatoren direkt per Befehl erhalten:

```text
/oregen give <ofen|räucherofen|schmiedeofen>
```

Benötigte Permission:

```text
dispensergenerator.admin
```

Standardmäßig ist diese Berechtigung nur für OPs freigegeben.

## Crafting-Rezept

Jeder Generator wird als spezieller Werfer gecraftet. Das Muster ist bei allen drei Typen gleich:

```text
M M M
M D M
M F M
```

Dabei gilt:

- `M` = Magmablock
- `D` = Werfer
- `F` = passender Ofen-Typ

Verwendete Ofen-Typen:

- normaler Ofen für den Ofen-Generator
- Räucherofen für den Räucherofen-Generator
- Schmiedeofen für den Schmiedeofen-Generator

## Installation

1. Plugin mit Maven bauen oder die fertige JAR verwenden.
2. Die JAR in den `plugins`-Ordner eines Paper-Servers legen.
3. Server starten.
4. Optional die erzeugte `config.yml` anpassen.
5. Server nach Konfigurationsänderungen neu starten oder das Plugin neu laden.

## Nutzung im Spiel

### Generator erhalten

Es gibt zwei Wege:

- per Crafting-Rezept
- per Admin-Befehl `/oregen give ...`

### Generator platzieren

Der Generator wird als spezieller Werfer gesetzt. Die Ausrichtung des Werfers bestimmt, in welche Richtung Blöcke generiert werden.

### Menü öffnen

- `Rechtsklick` auf den Generator öffnet das Generator-Menü.
- `Ducken + Rechtsklick` öffnet direkt das Inventar des Werfers.

### Generator mit Ladung versorgen

Im Hauptmenü gibt es drei Ladungsslots:

- `Kelp-Slot`
- `Knochen-Slot`
- `Lava-Slot`

Es gibt mehrere Möglichkeiten, Brennstoffe einzulagern:

- Gegenstand mit dem Cursor direkt auf den passenden Slot klicken
- Linksklick für kleine Mengen
- Rechtsklick für einen Stack
- Ducken + Klick, um alle passenden Items aus dem Inventar einzulagern
- Alternativ können die Rohstoffe direkt in das Werfer-Inventar gelegt werden; das Plugin zieht sie automatisch ein

### Generator starten

Im Hauptmenü kann der Generator auf `AN` oder `AUS` gestellt werden. Nur aktive Generatoren erzeugen Blöcke.

### Modus wechseln

Über den Modus-Button im Menü kann zwischen `Würfel` und `5er Linie` umgeschaltet werden.

### Partikel ein- oder ausschalten

Über den Partikel-Button lässt sich die Bereichsvorschau aktivieren oder deaktivieren.

### Upgrades einsetzen

Im Upgrade-Menü besitzt jedes Upgrade einen festen Slot. Dort kann das passende Enchanted Book eingesetzt werden. Wenn bereits ein Upgrade vorhanden ist, wird es beim Ersetzen an den Spieler zurückgegeben. Mit leerem Cursor kann ein eingesetztes Buch wieder entfernt werden.

## Verhalten beim Abbau

Wenn ein gesetzter Generator abgebaut wird:

- wird nicht nur der Werfer entfernt
- der spezielle Generator-Block selbst wird gedroppt
- gespeicherte Ladungen und Upgrades bleiben im Item erhalten
- der Inhalt des Werfer-Inventars wird ebenfalls gedroppt

Wenn ein vom Generator erzeugter Block abgebaut wird, können abhängig von den Upgrades zusätzliche Drops oder Erfahrungsorbs entstehen.

## Konfiguration

Die wichtigste Datei ist [`config.yml`](/d:/GitHub_McB-sser/DispenserGenerator/src/main/resources/config.yml).

Wichtige Bereiche:

- `generator.interval-ticks`
  Gibt an, wie oft der Generator arbeitet.
- `generator.base-blocks-per-cycle`
  Basisanzahl an Blöcken pro Zyklus.
- `generator.max-blocks-per-cycle`
  Maximale Anzahl generierter Blöcke pro Zyklus.
- `generator.particle-preview`
  Aktiviert die Partikelvorschau standardmäßig.
- `generator.stop-when-no-fuel`
  Stoppt den Generator automatisch, wenn keine Ladung mehr vorhanden ist.
- `costs.*`
  Definiert, wie viel Kelp-, Knochen- und Lava-Ladung für 64 erzeugte Blöcke verbraucht wird.
- `materials.*`
  Legt fest, welche Materialien ein Generator-Typ häufig oder selten erzeugen kann.
- `mode-requirements.enabled`
  Aktiviert optionale Umgebungsanforderungen.
- `mode-requirements.cube-nearby-any`
  Benachbarte Blöcke, die für den Würfelmodus vorhanden sein müssen.
- `mode-requirements.cobbler-nearby-any`
  Benachbarte Blöcke, die für den Linienmodus vorhanden sein müssen.

## Standardverhalten der Generatoren

### Ofen-Generator

Geeignet für klassische Stein- und Baublöcke. Gut für frühe Ressourcenproduktion, Baumaterial-Farmen und allgemeine Survival-Nutzung.

### Räucherofen-Generator

Geeignet für Naturblöcke, Holz-basierte Materialien und Dekoblöcke. Sinnvoll für Landschaftsprojekte und Baumaterialien mit natürlichem Look.

### Schmiedeofen-Generator

Geeignet für härtere, dunklere oder tiefer wirkende Materialien. Besonders nützlich für Deepslate-, Basalt- oder Blackstone-Bauprojekte.

## Hinweise

- Erze werden absichtlich nicht generiert.
- `Ancient Debris` ist ebenfalls ausgeschlossen.
- Der Generator setzt nur Blöcke in freie Luft.
- Die tatsächliche Ausbeute hängt vom Generator-Typ, den Upgrades und der Konfiguration ab.

## Entwicklung

Build mit Maven:

```bash
mvn clean package
```
