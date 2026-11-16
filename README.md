# LH-DPC – Dienstplankonverter

LH-DPC wandelt einen als PDF veröffentlichten Einsatzplan aus dem
*Crew Assignment System* in eine iCalendar-Datei (`.ics`) um. Die Datei lässt
sich in Apple Kalender, Outlook, Google Kalender und andere Programme importieren.

Das Programm entstand ab 2007 als privates Hilfsmittel und wurde bis 2021
gepflegt (Version 0.49). Laut Änderungsprotokoll wurde es mit Plänen aus Passage,
Cargo und Condor verwendet.

> **Hinweis:** Dies ist ein inoffizielles, privates Projekt. Es besteht keine
> Verbindung zur Deutschen Lufthansa AG oder zu anderen Fluggesellschaften.
> Es gibt keine Gewähr, dass das Planformat aktuell noch unterstützt wird.
> Konvertierte Termine bitte immer mit dem Original-Plan abgleichen.

## Funktionen

- Erkennt u. a. Flugdienste, Deadhead (DH), Ground Transport (GT), Briefingzeiten,
  Reserve/Standby (RE6, RE12, RE13, SB, …), Simulator inkl. An-/Abreise (OTX/TLX/TCX),
  Seminare, Medical, Urlaub, OFF- und freie Tage.
- Dienste über Mitternacht und Monats-/Jahreswechsel werden berücksichtigt.
- Optional: Flugnummer in der Terminbeschreibung, Export freier Tage, OFF- und Urlaubstage
  als ganztägige Termine.

## Voraussetzungen

- Java 8 oder neuer
- [Apache PDFBox](https://pdfbox.apache.org/) **1.6.0** (die Version 2.x ist nicht
  API-kompatibel) sowie deren Abhängigkeiten. Diese drei Jars gehören in den Ordner `lib/`:
  - `pdfbox-1.6.0.jar`
  - `fontbox-1.6.0.jar`
  - `commons-logging-1.1.1.jar`

  Alle drei gibt es auf Maven Central:

  ```sh
  mkdir -p lib && cd lib
  curl -LO https://repo1.maven.org/maven2/org/apache/pdfbox/pdfbox/1.6.0/pdfbox-1.6.0.jar
  curl -LO https://repo1.maven.org/maven2/org/apache/pdfbox/fontbox/1.6.0/fontbox-1.6.0.jar
  curl -LO https://repo1.maven.org/maven2/commons-logging/commons-logging/1.1.1/commons-logging-1.1.1.jar
  ```

## Bauen und Starten

Ohne IDE:

```sh
javac -encoding UTF-8 -cp "lib/*" -d build/classes src/LH_DPC.java
java -cp "build/classes:lib/*" LH_DPC        # Windows: "build/classes;lib/*"
```

Alternativ lässt sich das Projekt in NetBeans öffnen (oder mit `ant jar` bauen);
das Ergebnis liegt dann in `dist/LH-DPC.jar`.

## Benutzung

1. **PDF-Plan öffnen** und den Dienstplan auswählen. Name, Stationierung und
   Zeitraum werden zur Kontrolle angezeigt.
2. Gewünschte Optionen ankreuzen.
3. **ics-Datei speichern** und die Datei in den Kalender importieren.

Jeder Termin erhält eine UID aus Datum, laufender Nummer, einem Hash des Namens
(der Klarname steht nicht in der Datei) und Stationierung.
Wird derselbe Plan erneut importiert, erkennen viele Kalender die Termine wieder.
Bei Plänen, die mit Versionen vor der Veröffentlichung auf GitHub erzeugt wurden,
weicht die UID ab; dann entstehen beim erneuten Import doppelte Termine.

## Datenschutz

Dienstpläne enthalten personenbezogene Daten. Bitte keine echten Pläne oder
erzeugten `.ics`-Dateien in Issues oder Pull Requests hochladen. Zum Nachstellen
von Fehlern genügen anonymisierte Ausschnitte der betroffenen Zeilen.

## Offene Punkte

- Line-Check (LC) wird nicht erkannt.
- Umläufe/Dienstketten erzeugen (Option in der Oberfläche vorhanden, aber deaktiviert).
- Bei RE12 über Mitternacht wird ein Monatswechsel nicht geprüft.
- Viele Diensttypen sind als nahezu identische Codeblöcke umgesetzt und könnten
  zusammengefasst werden.
- Umstellung auf eine aktuelle PDFBox-Version.

## Lizenz

[MIT](LICENSE)
