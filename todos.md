## TODOS für Aufgabe 3

- [ ] Genau mit dem Selective-Repeat Verfahren beschäftigen.
     Nur verloren gegangene Pakete spollen erkannt und erneut übertragen werden.
    - [ ] -> Dynamische Berechnung der Timeoutzeit nach algorithmus des TCP-Verfahrens (FCdata.pdf seite 56)
- [ ] Werte in CSV-Formate Parsen
-

## Bedenke das der Server...

- ...`nicht multi-threadfähig` ist. Nur ein Kopierauftragg zur Zeit.
- ...Evtl eintreffende UDP-Pakete von anderen Absendern werden ignoriert solange vorheriger noch nicht verarbeitet ist.
- ...beim Start wird der UDP Port muss beim Start übergeben werden.
- ...nach 3 Sekunden ohne reaktion von Client der Server den Kopierauftrag beendet. Alles danach wird Ingoriert. Der Dateitransfer-Server steht sofort danach für neue Aufträge zur verfügung.
- ...ein Bestätigungspakete als pos. Quittung (`ACK`) versendet. -> enthält nur `Sequenznummer`. Mit einer Verzögerung von 10ms
- ...in der Lage ist das n-te empfangene Paket als fehlend zu melden (Parameter ERROR_RATE = n n >= 0). 
    -> `**Dateitransfer-Client**` schickt um dies zu simulieren mit dem ersten Paket die Sequenznummer 0 als Teil der Steuerdaten   

### Client Programmieren:

- richtige Reihenfolge, Flusskontrolle und mögliche Paket-Verluste. -> Selective-Repeat

Übergabe bei erstellung:

    1. Hostname oder IP-Adresse des Dateitransfer-Servers
    2. Portnummer des Dateitransfer-Servers
    3. Quellpfad inkl. Dateiname der zu sendenden Datei auf dem lokalen System
    4. Zielpfad inkl. Dateiname der zu empfangenden Datei auf dem Dateitransfer-Server
    5. Window-Größe N (> 0, ganzzahlig)
    6. Fehlerrate ERROR_RATE (>= 0, ganzzahlig) zur Übergabe an den Dateitransfer-Server