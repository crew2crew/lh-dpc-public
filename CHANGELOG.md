# Änderungsprotokoll

## 0.49, 02.05.2021

- Korrektur an Seitenumbruchsbehandlung

## 0.48, 05.06.2019

- Korrektur an "SB"

## 0.47, 23.08.2017

- Anscheinend läuft das Format von Passage und Cargo etwas auseinander. Die letzte Anpassung von Version 0.46 funktionierte nur für die Cargo-Pläne, deshalb jetzt eine etwas andere Herangehensweise an die Seitenerkennung.

## 0.46, 06.08.2017

- Zeilenanzahl zwischen zwei Seiten hat sich geändert; DPC entsprechend angepaßt

## 0.45, 09.07.2017

- Korrektur an MED

## 0.44, 10.11.2016

- Korrektur an RE12

## 0.43, 02.11.2016

- „REP“ nachgetragen

## 0.42, 02.10.2016

- Erkennung von generischen Ereignissen und damit auch „RE“ korrigiert.

## 0.41, 26.08.2016

- Seminar-Erkennung überarbeitet

## 0.40, 29.12.2015

- „Conversion“ und Seminar nachgetragen
- Änderung an der Seitenendeerkennung

## 0.39, 02.10.2015

- Standby-Airport nachgetragen
- Korrektur an Reserve-Abfrage

## 0.38, 02.08.2015

- Korrektur an Simulator-Anreise-Termin

## 0.37, 31.03.2015

- Korrektur, so dass ein Ortstag als freier Tag erkannt wird

## 0.36, 6.03.2015

- Ortstag nachgetragen

## 0.35, 01.11.2014

- Korrektur an OTX

## 0.34, 04.09.2014

- erneute Anpassung der RE12
- Aktualisierung MED

## 0.33, 09.07.2013

- Anpassung der RE12 für die Cargo

## 0.32, 09.12.2012

- Anpassung, damit sich auf einem Rechner mehrere Dienstpläne ohne Kollision importieren lassen

## 0.31, 15.04.2012

- RE6 angepaßt für den Fall, daß keine Station angegeben ist
- alternative Flugnummer für Cargo nachgetragen

## 0.30, 01.01.2012

- OTX/O angepaßt

## 0.29, 27.10.2011

- Seitenwechselerkennung geändert

## 0.28, 26.05.2011

- Briefingroutine überarbeitet, Datumsfehler korrigiert

## 0.27, 26.05.2011

- Seitenwechsel im Flugdienst korrigiert
- RB-Dienste korrigiert

## 0.26, 09.02.2011

- Anpassung an einen speziellen Plan, der nicht mit "no further duties planned" endet

## 0.25, 05.11.2010

- Briefingzeit nachwievor 1:40h vor dem Dienst, Tag aus der Briefingzeit herausgeschnitten, um die Übersicht zu verbessern

## 0.25b, 1.11.2010

- Implementierung einer separaten Briefingzeit, allerdings ohne intensiveres Testen; aktuell wird die Briefingzeit fest 1:40h vor den zugehörigen Flug gesetzt (zumindest in der Theorie)

## 0.24, 02.05.2010

- Der DPC merkt sich jetzt die Verzeichnisse, aus denen und in die im- bzw. exportiert wurde
- Conversions wurden nachgepflegt

## 0.23, 11.12.2009

- abermals Änderung an der Routine für Dienste über den Monats- Wechsel vorgenommen

## 0.22, 25.10.2009

- Fehler im Monatswechsel bei Flugdienst behoben (Java rechnet die Monate von 0 bis 11!)

## 0.21, 08.08.2009

- Fehler in der SIM-Routine behoben (Anpassung für Monatsübergang steht noch aus)

## 0.20b3, 27.02.2009

- Medical nachgetragen

## 0.20b2, 10.12.2008

- Jahreswechsel korrigiert bei monatsübergreifenden Plänen

## 0.20b, 6.12.2008

- "Briefingzeitfehler" in manchen Plänen durch weitere Abfrage kompensiert, BETA um klarzustellen, daß einige Anpassungen nötig waren, die womöglich zu Folgefehlern führen 0.19, 01.12.2008:
- TKFIT nachgetragen

## 0.18, 24.11.2008

- einige Dienste nachgetragen (EH, EMCRM, QF, EM)

## 0.17, 15.11.2008

- Betriebliche Elternteilzeit nachgetragen
- für den Fall, daß eine Briefingzeit zwischen Dienstanfang und -ende im Plan erscheint, muß noch eine gute Lösung her... 0.16, 08.11.2008:
- Die Briefingzeit ist jetzt in den ersten Eintrag gewandert, d.h. es wird kein separater Briefing-Event generiert 0.15, 04.06.2008:
- einige Purser-Ereignisse nachgetragen, "EH" und "=" fehlen noch

## 0.14

- "Bem:" beim Simulatorereignis wird ignoriert (gewählte Lösung eher unschön)

## 0.13

- einige Anpassungen, auf daß auch Condor-Pläne konvertiert werden
