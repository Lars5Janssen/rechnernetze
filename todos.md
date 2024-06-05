## TODOS für Aufgabe 3
- [ ] threading für das Senden
  - Bei einem sende vorgang in seq0 ein initialpaket senden
  - ersten 8 byte enthalten eine sequenznummer welche selbst erstellt werden muss.
    - PrioQueue nicht gesendete Pakete
    - bedenke dabei den Timer direkt vor dem senden zu starten
    - [FCPaket1, 2 ,3, 4] darin laufen die timer
      - nach ack für spezifische seq. nummer rtt time setzen
- [ ] rtt algorithmus machen.
- [ ] threading fürs empfangen
  - der server sendet die exakte seq nummer des erhaltenen Packets
  - bei erhalt des packets (ack) timer des FCPacket canceln und zeit und weitere infos in csv schreiben.
  


- [ ] Konsole für den Client
  - Nur übergabe der Quelle und des Ziels. Hostname/Portnummer/Window-Größe und Fehlerrate sind vorrausgesetzt.
- [ ] Selective-Repeat Verfahren implementieren. (Funktional Interface oder statische Klasse?)
  - Die Sequence wird häufig falsch berechnet. Obacht 
  - [ ] -> Dynamische Berechnung der `Timeoutzeit nach algorithmus` des TCP-Verfahrens (FCdata.pdf seite 56)
- [ ] Werte in CSV-Formate Parsen (Hier vielleicht ebenfalls eine statische Klasse? 1 parameter?)

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

### Vorgaben Implementierung

Zur Verarbeitung von Informationen über alle verwalteten Objekte sind jeweils eigene
Datenstrukturen (Klassen) zur Beschreibung hilfreich. Integrieren Sie eine Sequenznummer in
jedes UDP-Datenpaket (als die ersten 8 Byte des Daten-Bytearrays). Benutzen Sie dafür die
Methoden der mitgegebenen Klasse FCpacket und speichern Sie im Sendepuffer nur
Pakete vom Typ FCpacket, damit Sie auch weitere Zusatzinformationen für ein gesendetes
Paket ablegen können!

### Für Selective-Repeat
Wir benötigen:
- Timeoutzeit nach TCP Algorithmus -> Statischen Klasse. Timeout.reTransmit()
- CSV Parser
