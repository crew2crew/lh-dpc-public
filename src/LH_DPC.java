/*
 * LH-DPC - Dienstplankonverter
 *
 * Wandelt einen als PDF veröffentlichten Einsatzplan aus dem
 * "Crew Assignment System" in eine iCalendar-Datei (.ics) um, die sich in
 * gängige Kalenderprogramme importieren lässt.
 *
 * Inoffizielles Hilfsprogramm; keine Verbindung zur Deutschen Lufthansa AG
 * oder anderen Fluggesellschaften.
 *
 * Copyright (c) 2007-2026 crew2crew (https://github.com/crew2crew)
 * SPDX-License-Identifier: MIT
 */

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.io.*;
import org.apache.pdfbox.util.*;
import org.apache.pdfbox.pdmodel.*;
import java.util.*;
import java.util.regex.*;
import java.util.prefs.*;

/**
 * Hauptklasse mit Swing-Oberfläche.
 *
 * <p>Ablauf: {@link #decodeFile(File)} liest das PDF mit PDFBox ein, prüft
 * die Kopfzeilen und reduziert den Text auf die eigentlichen Planzeilen
 * ({@link #decodedDP}). {@link #encodeFile(File)} übersetzt diese Zeilen
 * anschließend in VEVENT-Einträge und schreibt die .ics-Datei.</p>
 */
public class LH_DPC implements ActionListener {
    
    static final String version = "0.49";
    
    JFrame f;				// the main window
    JPanel p;				// the panel
    JButton openB;			// the open button
    JButton saveB;			// the save button
    
    JLabel nameL, tlcL, stationierungL, zeitraumL, versionL;		// some labels
    JCheckBox long_days,free_days,off_days,duty_chains,vacation_days;
    
    String decodedDP="";		// will contain the central element of the Dienstplan
    String name = "", dutyPeriod = "", base = "";	// some data about the Dienstplan
    int offT,teilzeitT,urlaubsT;	//   "    "    "    "       "
    // merkt sich die zuletzt verwendeten Verzeichnisse für Öffnen/Speichern
    Preferences prefs = Preferences.userRoot().node("/lh-dpc");

    
    public LH_DPC() {
        
        // create the main window
        f = new JFrame("LH-DPC");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setSize(new Dimension(500, 200));
        
        p = new JPanel(null);
        
        f.setContentPane(p);
        
        // definition of the open button
        openB = new JButton("PDF-Plan öffnen");
        openB.setBounds(10,15,180,50);
        openB.setActionCommand("open");
        openB.addActionListener(this);
        p.add(openB);
        
        // definition of the save button, initially it's disabled
        saveB = new JButton("ics-Datei speichern");
        saveB.setBounds(10,80,180,50);
        saveB.setActionCommand("save");
        saveB.addActionListener(this);
        saveB.setEnabled(false);
        p.add(saveB);
        
        // define the labels:
        // versionL
        versionL = new JLabel("Version: "+version);
        versionL.setBounds(20,145,150,20);
        versionL.setEnabled(false);
        p.add(versionL);
        
        // nameL
        nameL = new JLabel("Name: ");
        nameL.setEnabled(false);
        nameL.setBounds(220,10,250,20);
        p.add(nameL);
        
        //stationierungL
        stationierungL = new JLabel("Stationierung: ");
        stationierungL.setEnabled(false);
        stationierungL.setBounds(220,30,250,20);
        p.add(stationierungL);
        
        // zeitraumL
        zeitraumL = new JLabel("Zeitraum: ");
        zeitraumL.setEnabled(false);
        zeitraumL.setBounds(220,50,250,20);
        p.add(zeitraumL);
        
        // Langausgabe?
        long_days = new JCheckBox("Langausgabe der Flugdienste",false);
        long_days.setBounds(220,70,250,20);
        long_days.setToolTipText("Zeigt die Flugnummer in der Terminbeschreibung an");
        p.add(long_days);
        
        // freie Tage anzeigen?
        free_days = new JCheckBox("Freie Tage exportieren",false);
        free_days.setBounds(220,90,250,20);
        free_days.setToolTipText("Exportiert die freien Tage als ganztägigen Termin");
        p.add(free_days);
        
        // Off-Tage anzeigen?
        off_days = new JCheckBox("Off-Tage exportieren",true);
        off_days.setBounds(220,110,250,20);
        off_days.setToolTipText("Exportiert die Off-Tage als ganztägigen Termin");
        p.add(off_days);
        
        // Urlaubstage anzeigen?
        vacation_days = new JCheckBox("Urlaubstage exportieren",true);
        vacation_days.setBounds(220,130,250,20);
        vacation_days.setToolTipText("Exportiert die Urlaubstage als ganztägigen Termin");
        p.add(vacation_days);
        
        // Dienstketten anzeigen?
        duty_chains = new JCheckBox("Dienstketten anzeigen",false);
        duty_chains.setBounds(220,150,250,20);
        duty_chains.setToolTipText("Versucht aus den gegebenen Diensten Dienstketten zu erzeugen - noch nicht implementiert!");
        duty_chains.setEnabled(false);
        p.add(duty_chains);
        
        //Display the window.
        f.setVisible(true);
    }
    
    /**
     * Liest den Dienstplan aus einem PDF.
     *
     * <p>Der Text wird mit PDFBox extrahiert. Aus den drei Kopfzeilen werden
     * Name, Planzeitraum und Stationierung gelesen. Danach werden alle
     * Planzeilen bis "Keine weiteren Einsätze geplant" gesammelt; Seitenumbrüche
     * ("Printed for ...") und wiederholte Spaltenköpfe werden übersprungen.
     * Das Ergebnis steht zeilenweise in {@link #decodedDP}.</p>
     *
     * @param datei die ausgewählte PDF-Datei
     * @return {@code true}, wenn ein gültiger Plan erkannt wurde
     */
    public boolean decodeFile(File datei) {
        String text = new String();	// will initially contain the whole text of the Dienstplan
        // but it's also used as a temporary variable
        StringTokenizer st;			// st will contain all the lines of the Dienstplan
        
        try {
            PDFTextStripper stripper = new PDFTextStripper();	// create a new stripper object
            PDDocument pdf;									// create a new PDDocument housing the pdf
            pdf = PDDocument.load(datei);			// now let's try to open the selected file
            text = stripper.getText(pdf);			// and get the text from it, storing it in "text"
            pdf.close();							// close the PDF file
        } catch (IOException e) {					// any problem reading the file?
            System.err.println("Schreib-/Leseprobleme!");		// TODO: Fehler an die Oberfläche melden
        }
        
        st = new StringTokenizer(text,"\n\r");		// tokenize the complete string in separate lines
        
        if (st.hasMoreTokens()) {		// erste Zeile, z. B.: Deutsche Lufthansa AGPlan Nr: x.x.xCrew Assignment System  vx.x.x
            text = st.nextToken();
            if (!Pattern.matches(".*Plan Nr: .*",text))
                return false;
        } else return false;
        
        if (st.hasMoreTokens()) {		// zweite Zeile: Veröffentlichter Einsatzplan NACHNAME Vorname
            text = st.nextToken();
            if (Pattern.matches(".*Einsatzplan.*",text)) {
                name=text.substring(text.indexOf("plan")+5,text.length()-3);
            } else return false;
        } else return false;
        
        if (st.hasMoreTokens()) {		// dritte Zeile: 1Seite : Druckdatum : DD MMM YYYY hh:mmMonat : MMM YYYYDienstplan für : <Funktion/Muster> <Base>
            text = st.nextToken();
            if (Pattern.matches(".*Druckdatum.*Monat.*Dienstplan.*",text)) {
                int pos = text.indexOf("Monat");
                int pos2 = text.indexOf("Dienstplan");
                dutyPeriod = text.substring(pos+8,pos2);
                base=text.substring(text.length()-3,text.length());
            } else return false;
        }
        
        
        
                /* Now we search for the following line:
                 * UTC Kalender/Homebase FRA - UTC Zeitdifferenz +02:00
                 */
        while (st.hasMoreTokens()) {
            text = st.nextToken();
            if (Pattern.matches("UTC Kalender/Homebase.*",text))
                break;		// leave this routine
        }
        if (!st.hasMoreTokens()) return false;	// there should be some data following, so the PDF is corrupted

        
        // let's skip the next few lines until we've got a date
        while (st.hasMoreTokens()) {
            text=st.nextToken();
            if ( Pattern.matches("[A-Z][a-z]",text.substring(0,2)) && Pattern.matches("[0-9]{2}",text.substring(3,5)) )
                break; 
        }
        if ( !(Pattern.matches("[A-Z][a-z]",text.substring(0,2)) && Pattern.matches("[0-9]{2}",text.substring(3,5))) )
            return false;

        
        // so this should be the first "useful" line of the Dienstplan
        decodedDP=text.trim()+"\n";

                
        while ((st.hasMoreTokens()) && !(Pattern.matches(".*Keine weiteren Einsätze geplant / No further duties planned.*",text))) {
            text = st.nextToken();
            
            if ((Pattern.matches(".*Keine weiteren Einsätze geplant / No further duties planned.*",text)))
                break;
            
                            
            // We do want to skip the text separating two pages:
            if ( text.length()>10 && text.substring(0,11).equals("Printed for")) {
                // delete the following 10 lines and store the following one in text
                for (int i=0; i<4; i++) {
                    if (st.hasMoreTokens()) {
                        text=st.nextToken();
                    }
                    else
                        break;
                }                
            } 
            
            // Skip the header of each column
            // The first 2 lines are automatically skipped including all
            // following lines starting with at least one space char
            if (text.startsWith("UTC Kalender/Homebase")) {
                // Let's skip the following 1+ lines, 
                // the second and following only if they start with at least 1 empty space
                for (int i=0; i<6; i++) {
                    text = st.nextToken();
                }
            }
            
            decodedDP=decodedDP+text.trim()+"\n";
        } 
        
        // and remove all unnecessary spaces:
        decodedDP=decodedDP.replaceAll("\\x20+"," ");
        
        return true;		// gültiger Plan erkannt
    }
    
    
    /**
     * Wandelt {@link #decodedDP} in eine iCalendar-Datei um.
     *
     * <p>Jede Planzeile wird gegen eine lange if/else-Kette geprüft, in der
     * jeder Zweig genau einen Diensttyp erkennt (Frei, OFF, Urlaub, Reserve,
     * Standby, Flugdienst, Deadhead, SIM, Seminar, ...). Ein allgemeiner
     * Zweig am Ende fängt das Format "DIENST ORT hh:mm-hh:mm" ab.</p>
     *
     * <p>Beginnt eine Zeile mit einem Datum ("Mo 05"), ist {@code delta} = 2,
     * sonst 0; alle Feldindizes sind um {@code delta} verschoben. Endet eine
     * Zeitangabe mit "-", geht der Dienst über Mitternacht und das Ende steht
     * in der folgenden Zeile, die deshalb direkt mitgelesen wird.</p>
     *
     * <p>Alle Zeiten werden als UTC ("Z") geschrieben, wie sie im Plan stehen.</p>
     *
     * @param datei Zieldatei
     * @return {@code true}, sofern kein Fehler gemeldet wurde
     */
    public boolean encodeFile(File datei) {
        StringTokenizer st = new StringTokenizer(decodedDP, "\n");		// tokenize the main Dienstplan
        String[] line;
        StringBuffer sb = new StringBuffer("");	// will contain the ics data
        Boolean briefing = false;
        String briefingS = "";
        
        
        /* A short explanation concerning the date handling:
         * We assume, that the Dienstplan doesn't start with the first day of the current
         * month, but also contains the last few days of the previous month.
         * Therefore tag and tagS are set to 15.
         * Moreover monat is set to the previous month and, if required, jahr's set appropriately
         */
        int delta=0;		// increment if a date is found
        int tag=15,monat=1,jahr;
        int vortag=15;          // used to remember the previous day
        String tagS="15",monatS,jahrS;
        
        String[] monate = new String[] {"JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC"};

        // let's get the month
        for (int i=0; i<12; i++)
            if (dutyPeriod.substring(0,3).equals(monate[i])) monat=i+1;

        // and now the year
        // Important: there are 3 different cases
        // - JAN 2008               length: 8
        // - JAN-FEB 2008           length: 12
        // - DEC 2008-JAN 2009      length: 17
        if (dutyPeriod.length()==8 || dutyPeriod.length()==17) {
            jahrS = dutyPeriod.substring(4,8);
            jahr=Integer.parseInt(jahrS);
        }
        else if (dutyPeriod.length()==12) {
            jahrS = dutyPeriod.substring(8,12);
            jahr=Integer.parseInt(jahrS);
        }
        else {  // shouldn't happen!
            System.out.println(dutyPeriod+" Länge:"+dutyPeriod.length());
            jahrS = dutyPeriod.substring(4,8);
            jahr=Integer.parseInt(jahrS);
            System.out.println("Please check the year!");
        }
        

        // and now we've got to change everything as explained above...
        monat--;
        if (monat==0) monat=12;
        monatS=Integer.toString(monat);
        if (monat<10) monatS="0"+monatS;
        if (monat==12) jahr--;
        jahrS=Integer.toString(jahr);


        String uid;	// must be unique and look something like this (acc. to RFC2445):
        // 19960401T080045Z-4000F192713-0052@host1.com
        String dtstamp;	// unique als well with the following format:
        // 20060201T000001Z
        int inc=0;	// with each line "inc" will be increased to create a unique number for each entry
        
        // let's create the ics header
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//LH-DPC//DPC//EN//" + version + "\r\n");        
        sb.append("X-WR-CALNAME:Dienstplan\r\n");
        sb.append("X-WR-TIMEZONE:Europe/Berlin\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");
        sb.append("METHOD:PUBLISH\r\n");
        
        while (st.hasMoreTokens()) {
            String whole_line = st.nextToken(); 
            line = whole_line.split(" ");
            if (line.length<1) continue;		// every useful line has got a least 2 entries
            
            inc++;		// laufende Nummer, macht UID und DTSTAMP eindeutig
            
                        /* First of we've got to verify if the line starts with a date.
                         * If so the variable "delta" has to be set to "2" and tag has to be increased by "1"
                         */
            if ( (Pattern.matches("[A-Z][a-z]",line[0])) && (Pattern.matches("[0-9]{2}",line[1])) ) { // is it a date?
                delta=2;
                // now this is a date and we've got to make sure, that the chronology is given (change of month?)
                tagS=line[1];
                tag=Integer.parseInt(tagS);
                if (tag < vortag) {     // so we've got a change of month!
                    System.out.println("Monatswechsel!");
                    monat=(monat+1)%13;
                    if (monat==0) {     // the year has to be changed as well
                        jahr++;
                        jahrS=Integer.toString(jahr);
                        monat++;
                    }
                    monatS=Integer.toString(monat);
                    if (monat<10) monatS="0"+monatS;
                }
                vortag=tag;
            } else delta=0;
            
            // Statt des Klarnamens nur ein Hash: UIDs bleiben pro Person eindeutig,
            // ohne dass der Name in der .ics-Datei steht.
            if (name.length() > 0)
                uid = jahrS+monatS+tagS+"T"+Integer.toString(inc) + nameHash(name) + base + "@lh-dpc.invalid";
            else
                uid = jahrS+monatS+tagS+"T"+Integer.toString(inc)+base+"@lh-dpc.invalid";
            
            dtstamp = jahrS+monatS+tagS+"T00"+Integer.toString((inc/600)%3)+Integer.toString((inc/60)%10)+Integer.toString((inc/10)%6)+Integer.toString(inc%10)+"Z";
            
            // Frei
            if ( free_days.isSelected() && line.length==2 && Pattern.matches("[A-Z][a-z]",line[0]) && Pattern.matches("[0-9]{2}",line[1]) ) {
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART;VALUE=DATE:"+jahrS+monatS+tagS+"\r\n");
                sb.append("DURATION:P1D\r\n");
                sb.append("SUMMARY:Frei\r\n");
                sb.append("END:VEVENT\r\n");
            }
            
            // Off
            else if ( off_days.isSelected() && line.length>=3 && Pattern.matches("[A-Z][a-z]",line[0]) && (Pattern.matches("[0-9]{2}",line[1])) && (line[2].equals("OFF")) ) { // short circuit evaluation!
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART;VALUE=DATE:"+jahrS+monatS+tagS+"\r\n");
                sb.append("DURATION:P1D\r\n");
                sb.append("SUMMARY:Off\r\n");
                sb.append("END:VEVENT\r\n");
            }
            
            // Off_2
            else if ( off_days.isSelected() && (line.length>=3) && (Pattern.matches("[A-Z][a-z]",line[0])) && (Pattern.matches("[0-9]{2}",line[1])) && (line[2].equals("OFF_2")) ) { // short circuit evaluation!
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART;VALUE=DATE:"+jahrS+monatS+tagS+"\r\n");
                sb.append("DURATION:P1D\r\n");
                sb.append("SUMMARY:Off\r\n");
                sb.append("END:VEVENT\r\n");
            }
            
            // Off_ (Condor)
            else if ( off_days.isSelected() && line.length>=3 && Pattern.matches("[A-Z][a-z]",line[0]) && (Pattern.matches("[0-9]{2}",line[1])) && (line[2].equals("OFF_")) ) { // short circuit evaluation!
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART;VALUE=DATE:"+jahrS+monatS+tagS+"\r\n");
                sb.append("DURATION:P1D\r\n");
                sb.append("SUMMARY:Off\r\n");
                sb.append("END:VEVENT\r\n");
            }

            // -- (Strichtag?)
            else if ( (line.length>=3) && (Pattern.matches("[A-Z][a-z]",line[0])) && (Pattern.matches("[0-9]{2}",line[1])) && (line[2].equals("--")) ) { // short circuit evaluation!
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART;VALUE=DATE:"+jahrS+monatS+tagS+"\r\n");
                sb.append("DURATION:P1D\r\n");
                sb.append("SUMMARY:Strichtag (--)\r\n");
                sb.append("END:VEVENT\r\n");
            }
            
            // Ortstag
            else if ( free_days.isSelected() && line.length>delta && line[0+delta].equals("ORTSTAG") ) {
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART;VALUE=DATE:"+jahrS+monatS+tagS+"\r\n");
                sb.append("DURATION:P1D\r\n");
                sb.append("SUMMARY:Ortstag\r\n");
                sb.append("END:VEVENT\r\n");
            }
            
            // Krank (K)
            else if ( (line.length>=3) && (Pattern.matches("[A-Z][a-z]",line[0])) && (Pattern.matches("[0-9]{2}",line[1])) && (line[2].equals("K")) ) { // short circuit evaluation!
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART;VALUE=DATE:"+jahrS+monatS+tagS+"\r\n");
                sb.append("DURATION:P1D\r\n");
                sb.append("SUMMARY:Krank\r\n");
                sb.append("END:VEVENT\r\n");
            }
            
            // Standby
            else if ( (line.length>=3) && (Pattern.matches("[A-Z][a-z]",line[0])) && (Pattern.matches("[0-9]{2}",line[1])) && (line[2].equals("S")) ) { // short circuit evaluation!
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART;VALUE=DATE:"+jahrS+monatS+tagS+"\r\n");
                sb.append("DURATION:P1D\r\n");
                sb.append("SUMMARY:Standby (S)\r\n");
                sb.append("END:VEVENT\r\n");
            }
            
            // Standby Airport:     ORK1  SB LH9007S-1  A320  MUC 08:25-10:30 MUC 02:05 00:00
            else if ( (line.length >= 6 + delta) && line[1+delta].equals("SB") && line[2+delta].startsWith("LH") ) { // short circuit evaluation!
                String where = line[4+delta];
                String offb = line[5+delta].substring(0,2)+line[5+delta].substring(3,5);
                String onb = line[5+delta].substring(6,8)+line[5+delta].substring(9,11);
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                sb.append("SUMMARY:SB Airport "+where+"\r\n");
                sb.append("END:VEVENT\r\n");
            }
            
            // Daystop X
            else if (line.length>1+delta && line[0+delta].equals("X")) {
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART;VALUE=DATE:"+jahrS+monatS+tagS+"\r\n");
                sb.append("DURATION:P1D\r\n");
                String where = "";
                if ( Pattern.matches("[A-Z]{3}", line[1+delta]) ) 		// is it an Airport?
                    where = " "+line[1+delta];
                sb.append("SUMMARY:Daystop"+where+"\r\n");
                sb.append("END:VEVENT\r\n");
            }
            
            // Urlaub U
            else if ( vacation_days.isSelected() && (line.length>=3) && (Pattern.matches("[A-Z][a-z]",line[0])) && (Pattern.matches("[0-9]{2}",line[1])) && (line[2].equals("U")) ) {
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART;VALUE=DATE:"+jahrS+monatS+tagS+"\r\n");
                sb.append("DURATION:P1D\r\n");
                sb.append("SUMMARY:Urlaub\r\n");
                sb.append("END:VEVENT\r\n");
            }
            
            // Urlaub U1
            else if ( vacation_days.isSelected() && (line.length>=3) && (Pattern.matches("[A-Z][a-z]",line[0])) && (Pattern.matches("[0-9]{2}",line[1])) && (line[2].equals("U1")) ) {
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART;VALUE=DATE:"+jahrS+monatS+tagS+"\r\n");
                sb.append("DURATION:P1D\r\n");
                sb.append("SUMMARY:Urlaub (U1)\r\n");
                sb.append("END:VEVENT\r\n");
            }

            // Urlaub Condor U_DE
            else if ( vacation_days.isSelected() && (line.length>=3) && (Pattern.matches("[A-Z][a-z]",line[0])) && (Pattern.matches("[0-9]{2}",line[1])) && (line[2].equals("U_DE")) ) {
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART;VALUE=DATE:"+jahrS+monatS+tagS+"\r\n");
                sb.append("DURATION:P1D\r\n");
                sb.append("SUMMARY:Urlaub\r\n");
                sb.append("END:VEVENT\r\n");
            }
            
            // Betriebliche Elternteilzeit:	Mi 04 VE FRA 07:00- bzw. -23:00
            else if ( vacation_days.isSelected() && line.length>=2+delta && line[0+delta].equals("VE") && line[1+delta].substring(0,1).equals("-")) {
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART;VALUE=DATE:"+jahrS+monatS+tagS+"\r\n");
                sb.append("DURATION:P1D\r\n");
                sb.append("SUMMARY:Elternteilzeit\r\n");
                sb.append("END:VEVENT\r\n");
            }

            // Freizeitmodell(???)
            else if ( vacation_days.isSelected() && line.length>=2+delta && line[0+delta].equals("FRS") && line[1+delta].substring(0,1).equals("-")) {
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART;VALUE=DATE:"+jahrS+monatS+tagS+"\r\n");
                sb.append("DURATION:P1D\r\n");
                sb.append("SUMMARY:Freizeit\r\n");
                sb.append("END:VEVENT\r\n");
            }

            // RB lang
            else if ( (line.length==3+delta) && (line[0+delta].equals("RB")) ) {
                String where = line[1+delta];
                String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                String onb = line[2+delta].substring(6,8)+line[2+delta].substring(9,11);
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                sb.append("SUMMARY:RB in "+where+"\r\n");
                sb.append("END:VEVENT\r\n");
            }

            // RB kurz
            else if ( (line.length==2+delta) && (line[0+delta].equals("RB")) ) {
                String offb = line[1+delta].substring(0,2)+line[1+delta].substring(3,5);
                String onb = line[1+delta].substring(6,8)+line[1+delta].substring(9,11);
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                sb.append("SUMMARY:RB\r\n");
                sb.append("END:VEVENT\r\n");
            }
            
            // Standby:			Mi 04 SB FRA 07:00-
            // Alternativ Mi 04 SB 07:00-
            else if ( line.length>=2+delta && line[0+delta].equals("SB") ) {
                String where = "";
                if (Character.isDigit(line[1+delta].charAt(0))) {
                    // Apparently the airport is missing
                    delta -= 1;
                }
                else {
                    where = line[1+delta];
                }
                if (line[2+delta].endsWith("-")) {						// so the Standby ends the next day
                    String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    line=st.nextToken().split(" ");						// get the next line - CAUTION!
                    if ( (Pattern.matches("[A-Z][a-z]",line[0])) && (Pattern.matches("[0-9]{2}",line[1])) ) { // is it a date?
                        delta=2;
                        tagS=line[1];
                        tag=Integer.parseInt(tagS);
                        if (tag < vortag) {     // so we've got a change of month!
                            vortag=tag;
                            monat=monat%12+1;
                            monatS=Integer.toString(monat);
                            if (monat<10) monatS="0"+monatS;
                            if (monat==1) {     // the year has to be changed as well
                                jahr++;
                                jahrS=Integer.toString(jahr);
                            }
                        }
                        vortag=tag;
                    } else delta=0;
                    if ( line.length>=2 ) {
                        String onb = line[1+delta].substring(1,3)+line[1+delta].substring(4,6);
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                        sb.append("SUMMARY:Standby (SB)\r\n");
                        sb.append("DESCRIPTION:Ort: "+where+"\r\n");
                        sb.append("END:VEVENT\r\n");
                    } else {
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                        sb.append("SUMMARY:Fehler!\r\n");
                        sb.append("DESCRIPTION:Bitte melden!\r\n");
                        sb.append("END:VEVENT\r\n");
                    }
                } else {													// the Standby ends the same day
                    String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                    String onb = line[2+delta].substring(6,8)+line[2+delta].substring(9,11);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                    sb.append("SUMMARY:Standby (SB)\r\n");
                    sb.append("DESCRIPTION:Ort: "+where+"\r\n");
                    sb.append("END:VEVENT\r\n");
                    
                }
            }
            
            // Reserve6:			RE6 FRA 06:00-
            else if ( line.length>=2+delta && line[0+delta].equals("RE6") ) {
                String where = line[1+delta];
                if (Pattern.matches("[0-9]{2}:[0-9]{2}.*", where)) {
                    // so there is no location, RE6 is directly followed by the time...
                    where = "";
                    delta--;
                }
                if (line[2+delta].endsWith("-")) {						// so the Standby ends the next day
                    String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    line=st.nextToken().split(" ");						// get the next line - CAUTION!
                    if ( (Pattern.matches("[A-Z][a-z]",line[0])) && (Pattern.matches("[0-9]{2}",line[1])) ) { // is it a date?
                        delta=2;
                        tagS=line[1];
                        tag=Integer.parseInt(tagS);
                        if (tag < vortag) {     // so we've got a change of month!
                            monat=monat%12+1;
                            monatS=Integer.toString(monat);
                            if (monat<10) monatS="0"+monatS;
                            if (monat==1) {     // the year has to be changed as well
                                jahr++;
                                jahrS=Integer.toString(jahr);
                            }
                        }
                        vortag=tag;
                    } else delta=0;
                    if ( line.length>=2 ) {
                        String onb = line[1+delta].substring(1,3)+line[1+delta].substring(4,6);
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                        sb.append("SUMMARY:Reserve (RE6)\r\n");
                        sb.append("DESCRIPTION:Ort: "+where+"\r\n");
                        sb.append("END:VEVENT\r\n");
                    } else {
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                        sb.append("SUMMARY:Fehler!\r\n");
                        sb.append("DESCRIPTION:Bitte melden!\r\n");
                        sb.append("END:VEVENT\r\n");
                    }
                } else {													// the Standby ends the same day
                    String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                    String onb = line[2+delta].substring(6,8)+line[2+delta].substring(9,11);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                    sb.append("SUMMARY:Reserve (RE6)\r\n");
                    sb.append("DESCRIPTION:Ort: "+where+"\r\n");
                    sb.append("END:VEVENT\r\n");
                    
                }
            }
            
            // Reserve12:			RE12 FRA 06:00-
            // zumindest bei Cargo gibt es leider auch die Variante ohne Ort, d.h.  RE12 16:10-22:00
            else if ( line.length>=2+delta && line[0+delta].equals("RE12") ) {
                System.out.println("RE12 gefunden.");
                String where = line[1+delta];
                if (Pattern.matches("[A-Z]{3}", where)) {
                    System.out.println("Ort angegeben: " +  where);
                    delta+=1;
                }
                else {
                    System.out.println("Bei RE12 kein Ort angegeben");
                    where = "???";
                }
                if (line[1+delta].endsWith("-")) {						// so the Standby ends the next day
                    String offb = line[1+delta].substring(0,2)+line[1+delta].substring(3,5);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    line=st.nextToken().split(" ");						// get the next line - CAUTION!
                    if ( (Pattern.matches("[A-Z][a-z]",line[0])) && (Pattern.matches("[0-9]{2}",line[1])) ) { // is it a date?
                        delta=2;
                        tag++;                          // TODO: Monatswechsel wird hier nicht geprüft
                        tagS=Integer.toString(tag);
                        if (tag<10) tagS="0"+tagS;
                    } else delta=0;
                    if ( line.length>=2 ) {
                        String onb = line[1+delta].substring(1,3)+line[1+delta].substring(4,6);
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                        sb.append("SUMMARY:Reserve (RE12)\r\n");
                        sb.append("DESCRIPTION:Ort: "+where+"\r\n");
                        sb.append("END:VEVENT\r\n");
                    } else {
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                        sb.append("SUMMARY:Fehler!\r\n");
                        sb.append("DESCRIPTION:Bitte melden!\r\n");
                        sb.append("END:VEVENT\r\n");
                    }
                } else {													// the Standby ends the same day
                    String offb = line[1+delta].substring(0,2)+line[1+delta].substring(3,5);
                    String onb = line[1+delta].substring(6,8)+line[1+delta].substring(9,11);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                    sb.append("SUMMARY:Reserve (RE12)\r\n");
                    sb.append("DESCRIPTION:Ort: "+where+"\r\n");
                    sb.append("END:VEVENT\r\n");
                    
                }
            }
            
            // Reserve12_EX:   Do 09 RE12_EX HAM 03:00-20:00
            else if ( line.length>=3+delta && line[0+delta].equals("RE12_EX") ) {
                String where = line[1+delta];
                if (line[2+delta].endsWith("-")) {						// so the Standby ends the next day
                    String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    line=st.nextToken().split(" ");						// get the next line - CAUTION!
                    if ( (Pattern.matches("[A-Z][a-z]",line[0])) && (Pattern.matches("[0-9]{2}",line[1])) ) { // is it a date?
                        delta=2;
                        tag++;
                        tagS=Integer.toString(tag);
                        if (tag<10) tagS="0"+tagS;
                    } else delta=0;
                    if ( line.length>=2 ) {
                        String onb = line[1+delta].substring(1,3)+line[1+delta].substring(4,6);
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                        sb.append("SUMMARY:Reserve (RE12_EX)\r\n");
                        sb.append("DESCRIPTION:Ort: "+where+"\r\n");
                        sb.append("END:VEVENT\r\n");
                    } else {
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                        sb.append("SUMMARY:Fehler!\r\n");
                        sb.append("DESCRIPTION:Bitte melden!\r\n");
                        sb.append("END:VEVENT\r\n");
                    }
                } else {													// the Standby ends the same day
                    String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                    String onb = line[2+delta].substring(6,8)+line[2+delta].substring(9,11);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                    sb.append("SUMMARY:Reserve (RE12_EX)\r\n");
                    sb.append("DESCRIPTION:Ort: "+where+"\r\n");
                    sb.append("END:VEVENT\r\n");
                    
                }
            }        

            // Reserve12_DZ:   Do 09 RE12_DZ HAM 03:00-20:00
            else if ( line.length>=3+delta && line[0+delta].equals("RE12_DZ") ) {
                String where = line[1+delta];
                if (line[2+delta].endsWith("-")) {						// so the Standby ends the next day
                    String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    line=st.nextToken().split(" ");						// get the next line - CAUTION!
                    if ( (Pattern.matches("[A-Z][a-z]",line[0])) && (Pattern.matches("[0-9]{2}",line[1])) ) { // is it a date?
                        delta=2;
                        tag++;
                        tagS=Integer.toString(tag);
                        if (tag<10) tagS="0"+tagS;
                    } else delta=0;
                    if ( line.length>=2 ) {
                        String onb = line[1+delta].substring(1,3)+line[1+delta].substring(4,6);
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                        sb.append("SUMMARY:Reserve (RE12_DZ)\r\n");
                        sb.append("DESCRIPTION:Ort: "+where+"\r\n");
                        sb.append("END:VEVENT\r\n");
                    } else {
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                        sb.append("SUMMARY:Fehler!\r\n");
                        sb.append("DESCRIPTION:Bitte melden!\r\n");
                        sb.append("END:VEVENT\r\n");
                    }
                } else {													// the Standby ends the same day
                    String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                    String onb = line[2+delta].substring(6,8)+line[2+delta].substring(9,11);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                    sb.append("SUMMARY:Reserve (RE12_DZ)\r\n");
                    sb.append("DESCRIPTION:Ort: "+where+"\r\n");
                    sb.append("END:VEVENT\r\n");

                }
            }
            
            // Reserve13:			RE13 FRA 06:00-
            else if ( line.length>=3+delta && (line[0+delta].equals("RE13") || line[0+delta].equals("RE13_DZ")) ) {
                String where = line[1+delta];
                if (line[2+delta].endsWith("-")) {						// so the Standby ends the next day
                    String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    line=st.nextToken().split(" ");						// get the next line - CAUTION!
                    if ( (Pattern.matches("[A-Z][a-z]",line[0])) && (Pattern.matches("[0-9]{2}",line[1])) ) { // is it a date?
                        delta=2;
                        tag++;
                        tagS=Integer.toString(tag);
                        if (tag<10) tagS="0"+tagS;
                    } else delta=0;
                    if ( line.length>=2 ) {
                        String onb = line[1+delta].substring(1,3)+line[1+delta].substring(4,6);
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                        sb.append("SUMMARY:Reserve (RE13)\r\n");
                        sb.append("DESCRIPTION:Ort: "+where+"\r\n");
                        sb.append("END:VEVENT\r\n");
                    } else {
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                        sb.append("SUMMARY:Fehler!\r\n");
                        sb.append("DESCRIPTION:Bitte melden!\r\n");
                        sb.append("END:VEVENT\r\n");
                    }
                } else {													// the Standby ends the same day
                    String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                    String onb = line[2+delta].substring(6,8)+line[2+delta].substring(9,11);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                    sb.append("SUMMARY:Reserve (RE13)\r\n");
                    sb.append("DESCRIPTION:Ort: "+where+"\r\n");
                    sb.append("END:VEVENT\r\n");
                    
                }
            }

            // Reserve6:			RE6 FRA 06:00-
            else if ( line.length>=2+delta && line[0+delta].equals("REP") ) {
                String where = line[1+delta];
                if (Pattern.matches("[0-9]{2}:[0-9]{2}.*", where)) {
                    // so there is no location, RE6 is directly followed by the time...
                    where = "";
                    delta--;
                }
                if (line[2+delta].endsWith("-")) {						// so the Standby ends the next day
                    String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    line=st.nextToken().split(" ");						// get the next line - CAUTION!
                    if ( (Pattern.matches("[A-Z][a-z]",line[0])) && (Pattern.matches("[0-9]{2}",line[1])) ) { // is it a date?
                        delta=2;
                        tagS=line[1];
                        tag=Integer.parseInt(tagS);
                        if (tag < vortag) {     // so we've got a change of month!
                            monat=monat%12+1;
                            monatS=Integer.toString(monat);
                            if (monat<10) monatS="0"+monatS;
                            if (monat==1) {     // the year has to be changed as well
                                jahr++;
                                jahrS=Integer.toString(jahr);
                            }
                        }
                        vortag=tag;
                    } else delta=0;
                    if ( line.length>=2 ) {
                        String onb = line[1+delta].substring(1,3)+line[1+delta].substring(4,6);
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                        sb.append("SUMMARY:Reserve (REP)\r\n");
                        sb.append("DESCRIPTION:Ort: "+where+"\r\n");
                        sb.append("END:VEVENT\r\n");
                    } else {
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                        sb.append("SUMMARY:Fehler!\r\n");
                        sb.append("DESCRIPTION:Bitte melden!\r\n");
                        sb.append("END:VEVENT\r\n");
                    }
                } else {													// the Standby ends the same day
                    String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                    String onb = line[2+delta].substring(6,8)+line[2+delta].substring(9,11);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                    sb.append("SUMMARY:Reserve (REP)\r\n");
                    sb.append("DESCRIPTION:Ort: "+where+"\r\n");
                    sb.append("END:VEVENT\r\n");
                    
                }
            }
            
            // Standby dezentral:			Mo 08 SB_DEZ HAM 03:00-20:00
            else if ( line.length>=3+delta && line[0+delta].equals("SB_DEZ") ) {
                String where = line[1+delta];
                if (line[2+delta].endsWith("-")) {						// so the Standby ends the next day
                    String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    line=st.nextToken().split(" ");						// get the next line - CAUTION!
                    if ( (Pattern.matches("[A-Z][a-z]",line[0])) && (Pattern.matches("[0-9]{2}",line[1])) ) { // is it a date?
                        delta=2;
                        tag++;
                        tagS=Integer.toString(tag);
                        if (tag<10) tagS="0"+tagS;
                    } else delta=0;
                    if ( line.length>=2 ) {
                        String onb = line[1+delta].substring(1,3)+line[1+delta].substring(4,6);
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                        sb.append("SUMMARY:SB_DEZ\r\n");
                        sb.append("DESCRIPTION:Ort: "+where+"\r\n");
                        sb.append("END:VEVENT\r\n");
                    } else {
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                        sb.append("SUMMARY:Fehler!\r\n");
                        sb.append("DESCRIPTION:Bitte melden!\r\n");
                        sb.append("END:VEVENT\r\n");
                    }
                } else {	// the Standby ends the same day
                    String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                    String onb = line[2+delta].substring(6,8)+line[2+delta].substring(9,11);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                    sb.append("SUMMARY:SB_DEZ\r\n");
                    sb.append("DESCRIPTION:Ort: "+where+"\r\n");
                    sb.append("END:VEVENT\r\n");
                    
                }
            }
      
            // Briefingzeit LH alt
            else if ( line.length>=(2+delta) && line[0+delta].length()>16 && line[0+delta].substring(0,17).equals("Briefingzeit(LT):") ) {
                briefing=true;
                briefingS=line[0+delta]+" "+line[1+delta];
            }

            // Briefingzeit LH neu
            else if ( line.length>=(2+delta) && line[0+delta].equals("Briefingzeit(LT") ) {
                briefing=true;
                briefingS=line[3+delta]+" Uhr "+line[0+delta].substring(0,8)+" (LT "+line[1+delta].substring(0,line[1+delta].length()-1);
                System.out.println(briefingS);
            }
            
            // Flugdienst LH
            else if ( line.length>=(5+delta) && line[1+delta].length()>2 && (line[1+delta].substring(0,2).equals("LH") || line[1+delta].substring(0,2).equals("3S")) ) {
                Boolean hidden_x = false;
                String hidden_x_duty = "";
                String duty = line[1+delta];
                String from="", to="", offb="", onb ="";
                GregorianCalendar brief = new GregorianCalendar();

                brief.setTimeZone( TimeZone.getTimeZone("ECT") );
                brief.set(Calendar.DATE, tag);
                brief.set(Calendar.MONTH, monat-1);
                brief.set(Calendar.YEAR, jahr);

                // let's remove the "-1"-parts:
                if ( duty.indexOf("-") != -1)
                    duty = duty.substring(0,duty.indexOf("-"));
                String ac = line[2+delta];
                from = line[3+delta];
                if (line[4+delta].endsWith("-")) {						// so the duty ends the next day
                    offb = line[4+delta].substring(0,2)+line[4+delta].substring(3,5);
                    brief.set(Calendar.HOUR_OF_DAY, Integer.parseInt(line[4+delta].substring(0,2)));
                    brief.set(Calendar.MINUTE, Integer.parseInt(line[4+delta].substring(3,5)));

                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");

                    // unübliche Reihenfolge, damit es mit der Briefingzeit keine Probleme gibt
                    if (briefing) {
                        sb.append("DESCRIPTION:"+briefingS+" fuer "+duty+" auf "+ac+"\r\n");
                    }
                    else sb.append("DESCRIPTION:"+duty+" auf "+ac+"\r\n");
                    
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    line=st.nextToken().split(" ");						// get the next line - CAUTION!

                    if ( (Pattern.matches("[A-Z][a-z]",line[0])) && (Pattern.matches("[0-9]{2}",line[1])) ) { // is it a date?
                        if ( (line.length>=6) && (line[2].equals("Briefingzeit(LT")) ) {
                            delta=2;
                            briefing=true;  // Briefingzeit steht hier ausnahmsweise nach dem Dienstbeginn
                            briefingS=line[0+delta]+" "+line[1+delta]+" "+line[2+delta]+" "+line[3+delta];
                            line=st.nextToken().split(" ");     // falls in der nächsten Zeile fälschlicherweise eine Briefingzeit folgt!
                            delta=0;
                        }
                        else if ( (line.length >= 5) && line[2].equals("X")) {
                            hidden_x = true;
                            tagS = line[1];
                            if (Integer.parseInt(tagS) < 10)
                                tagS = "0" + Integer.parseInt(tagS);
                            monatS=Integer.toString(monat);
                            if (monat<10) monatS="0"+monatS;
                            jahrS=Integer.toString(jahr);
                            
                            String where = "";
                            hidden_x_duty += "BEGIN:VEVENT\r\n";
                            hidden_x_duty += "UID:"+uid+"-x\r\n";
                            hidden_x_duty += "DTSTAMP:"+dtstamp+"\r\n";
                            hidden_x_duty += "DTSTART;VALUE=DATE:"+jahrS+monatS+tagS+"\r\n";
                            hidden_x_duty += "DURATION:P1D\r\n";
                            if ( Pattern.matches("[A-Z]{3}", line[4]) ) 
                                where = " "+line[4];
                            hidden_x_duty += "SUMMARY:Daystop"+where+"\r\n";
                            hidden_x_duty += "END:VEVENT\r\n";
                            
                            delta = 2;
                        }
                        else delta=2;


                        GregorianCalendar cal = new GregorianCalendar();
                        cal.setTimeZone( TimeZone.getTimeZone("ECT") );
                        cal.set(Calendar.DATE, tag);
                        cal.set(Calendar.MONTH, monat-1);
                        cal.set(Calendar.YEAR, jahr);
                        cal.add(Calendar.DATE, 1);
                        tag = cal.get(Calendar.DATE);
                        monat = cal.get(Calendar.MONTH)+1;
                        jahr = cal.get(Calendar.YEAR);
                        vortag=tag;

                        tagS=Integer.toString(tag);
                        if (tag<10) tagS="0"+tagS;
                        monatS=Integer.toString(monat);
                        if (monat<10) monatS="0"+monatS;
                        jahrS=Integer.toString(jahr);
                    } else delta=0;
                    if ( line.length>=3 ) {
                        if (line[1].equals("Tg")) {
                            line = st.nextToken().split(" ");
                            delta = 2;
                        }
                        onb = line[1+delta].substring(1,3)+line[1+delta].substring(4,6);
                        to = line[2+delta];
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                        if ( long_days.isSelected() )
                            sb.append("SUMMARY:"+duty+": "+from+"-"+to+"\r\n");
                        else sb.append("SUMMARY:"+from+"-"+to+"\r\n");
                        sb.append("END:VEVENT\r\n");
                    } else {
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                        sb.append("SUMMARY:Fehler!\r\n");
                        sb.append("DESCRIPTION:Bitte melden!\r\n");
                        sb.append("END:VEVENT\r\n");
                    }
                } else {								// the duty ends the same day
                    offb = line[4+delta].substring(0,2)+line[4+delta].substring(3,5);
                    brief.set(Calendar.HOUR_OF_DAY, Integer.parseInt(line[4+delta].substring(0,2)));
                    brief.set(Calendar.MINUTE, Integer.parseInt(line[4+delta].substring(3,5)));
                    onb = line[4+delta].substring(6,8)+line[4+delta].substring(9,11);
                    if ( line.length>5 ) {
                        to = line[5+delta];
                        sb.append("BEGIN:VEVENT\r\n");
                        sb.append("UID:"+uid+"\r\n");
                        sb.append("DTSTAMP:"+dtstamp+"\r\n");
                        sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                        if ( long_days.isSelected() )
                            sb.append("SUMMARY:"+duty+": "+from+"-"+to+"\r\n");
                        else sb.append("SUMMARY:"+from+"-"+to+"\r\n");
                        if (briefing) {
                            sb.append("DESCRIPTION:"+briefingS+" fuer "+duty+" auf "+ac+"\r\n");
                        }
                        else sb.append("DESCRIPTION:"+duty+" auf "+ac+"\r\n");
                        sb.append("END:VEVENT\r\n");
                    } else {						// error: one entry is missing
                        sb.append("BEGIN:VEVENT\r\n");
                        sb.append("UID:"+uid+"\r\n");
                        sb.append("DTSTAMP:"+dtstamp+"\r\n");
                        sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                        sb.append("SUMMARY:Fehler\r\n");
                        sb.append("DESCRIPTION:Bitte melden!\r\n");
                        sb.append("END:VEVENT\r\n");
                    }
                }
                if (briefing) {
                    briefing=false;

                    brief.add(Calendar.HOUR_OF_DAY, -1);
                    brief.add(Calendar.MINUTE, -40);


                    tagS=Integer.toString(brief.get(Calendar.DATE));
                    if (brief.get(Calendar.DATE)<10) tagS="0"+tagS;
                    monatS=Integer.toString(brief.get(Calendar.MONTH)+1);
                    if (brief.get(Calendar.MONTH)+1<10) monatS="0"+monatS;
                    jahrS=Integer.toString(brief.get(Calendar.YEAR));
                    String h = Integer.toString(brief.get(Calendar.HOUR_OF_DAY));
                    if (brief.get(Calendar.HOUR_OF_DAY)<10) h="0"+h;
                    String m = Integer.toString(brief.get(Calendar.MINUTE));
                    if (brief.get(Calendar.MINUTE)<10) m="0"+m;
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:a"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+h+m+"00Z\r\n");
                    sb.append("DURATION:PT1H\r\n");
                    sb.append("SUMMARY:"+briefingS+"\r\n");
                    sb.append("DESCRIPTION:Bitte Anfangszeit ignorieren!\r\n");
                    sb.append("END:VEVENT\r\n");

                    // Änderungen an den Variablen zurücknehmen!
                    tagS=Integer.toString(tag);
                    if (tag<10) tagS = "0"+tagS;
                    monatS=Integer.toString(monat);
                    if (monat<10) monatS = "0"+monatS;
                    jahrS=Integer.toString(jahr);
                }
                
                if (hidden_x)
                    sb.append(hidden_x_duty);
            }

            // Flugdienst Condor
            else if ( line.length>=(5+delta) && line[1+delta].length()>2 && line[1+delta].substring(0,2).equals("DE") ) {
                String duty = line[1+delta];
                
                // let's remove the "-1"-parts:
                if ( duty.indexOf("-") != -1)
                    duty = duty.substring(0,duty.indexOf("-"));
                
                String ac = line[2+delta];
                String from = line[3+delta];
                if (line[4+delta].endsWith("-")) {						// so the duty ends the next day
                    String offb = line[4+delta].substring(0,2)+line[4+delta].substring(3,5);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    line=st.nextToken().split(" ");						// get the next line - CAUTION!
                    if ( (Pattern.matches("[A-Z][a-z]",line[0])) && (Pattern.matches("[0-9]{2}",line[1])) ) { // is it a date?
                        delta=2;
                        tag++;
                        tagS=Integer.toString(tag);
                        if (tag<10) tagS="0"+tagS;
                    } else delta=0;
                    if ( line.length>=3 ) {
                        String onb = line[1+delta].substring(1,3)+line[1+delta].substring(4,6);
                        String to = line[2+delta];
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                        if ( long_days.isSelected() )
                            sb.append("SUMMARY:"+duty+": "+from+"-"+to+"\r\n");
                        else sb.append("SUMMARY:"+from+"-"+to+"\r\n");
                        sb.append("DESCRIPTION:"+duty+" auf "+ac+"\r\n");
                        sb.append("END:VEVENT\r\n");
                    } else {
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                        sb.append("SUMMARY:Fehler!\r\n");
                        sb.append("DESCRIPTION:Bitte melden!\r\n");
                        sb.append("END:VEVENT\r\n");
                    }
                } else {													// the duty ends the same day
                    String offb = line[4+delta].substring(0,2)+line[4+delta].substring(3,5);
                    String onb = line[4+delta].substring(6,8)+line[4+delta].substring(9,11);
                    if ( line.length>5 ) {
                        String to = line[5+delta];
                        sb.append("BEGIN:VEVENT\r\n");
                        sb.append("UID:"+uid+"\r\n");
                        sb.append("DTSTAMP:"+dtstamp+"\r\n");
                        sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                        if ( long_days.isSelected() )
                            sb.append("SUMMARY:"+duty+": "+from+"-"+to+"\r\n");
                        else sb.append("SUMMARY:"+from+"-"+to+"\r\n");
                        sb.append("DESCRIPTION:"+duty+" auf "+ac+"\r\n");
                        sb.append("END:VEVENT\r\n");
                    } else {						// error: one entry is missing
                        sb.append("BEGIN:VEVENT\r\n");
                        sb.append("UID:"+uid+"\r\n");
                        sb.append("DTSTAMP:"+dtstamp+"\r\n");
                        sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                        sb.append("SUMMARY:Fehler\r\n");
                        sb.append("DESCRIPTION:Bitte melden!\r\n");
                        sb.append("END:VEVENT\r\n");
                    }
                }
            }
            
            // DH		430 DH LH430-1 B744 FRA 07:55-16:50 ORD
            // Achtung: Der Flugzeugtyp ist nicht immer angegeben
            else if ( line.length>=(6+delta) && line[1+delta].equals("DH") ) {
                String duty = line[2+delta];
                
                // let's remove the "-1"-parts:
                if ( duty.indexOf("-") != -1)
                    duty = duty.substring(0,duty.indexOf("-"));
                
                if (Pattern.matches("\\d\\d",line[5+delta].substring(0,2))) delta++;	// so a type is given
                String from = line[3+delta];
                if (line[4+delta].endsWith("-")) {						// so the proceeding ends the next day
                    String offb = line[4+delta].substring(0,2)+line[4+delta].substring(3,5);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    line=st.nextToken().split(" ");						// get the next line - CAUTION!
                    if ( (Pattern.matches("[A-Z][a-z]",line[0])) && (Pattern.matches("[0-9]{2}",line[1])) ) { // is it a date?
                        delta=2;
                        tag++;
                        tagS=Integer.toString(tag);
                        if (tag<10) tagS="0"+tagS;
                    } else delta=0;
                    if ( line.length>=3 ) {
                        String onb = line[1+delta].substring(1,3)+line[1+delta].substring(4,6);
                        String to = line[2+delta];
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                        sb.append("SUMMARY:DH/"+duty+": "+from+"-"+to+"\r\n");
                        sb.append("DESCRIPTION:\r\n");
                        sb.append("END:VEVENT\r\n");
                    } else {
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                        sb.append("SUMMARY:Fehler!\r\n");
                        sb.append("DESCRIPTION:Bitte melden!\r\n");
                        sb.append("END:VEVENT\r\n");
                    }
                } else {													// the proceeding ends the same day
                    String offb = line[4+delta].substring(0,2)+line[4+delta].substring(3,5);
                    String onb = line[4+delta].substring(6,8)+line[4+delta].substring(9,11);
                    if ( line.length>(5+delta) ) {
                        String to = line[5+delta];
                        sb.append("BEGIN:VEVENT\r\n");
                        sb.append("UID:"+uid+"\r\n");
                        sb.append("DTSTAMP:"+dtstamp+"\r\n");
                        sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                        sb.append("SUMMARY:DH/"+duty+": "+from+"-"+to+"\r\n");
                        sb.append("DESCRIPTION:\r\n");
                        sb.append("END:VEVENT\r\n");
                    } else {						// error: one entry is missing
                        sb.append("BEGIN:VEVENT\r\n");
                        sb.append("UID:"+uid+"\r\n");
                        sb.append("DTSTAMP:"+dtstamp+"\r\n");
                        sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                        sb.append("SUMMARY:Fehler\r\n");
                        sb.append("DESCRIPTION:Bitte melden!\r\n");
                        sb.append("END:VEVENT\r\n");
                    }
                }
            }
            
            // GT		8410G GT 8410G-1 FRA 03:05-05:05 CGN
            else if ( line.length>=(6+delta) && line[1+delta].equals("GT") ) {
                String duty = line[2+delta];
                if (Pattern.matches("\\d\\d",line[5+delta].substring(0,2))) delta++;	// so a type is given
                String from = line[3+delta];
                if (line[4+delta].endsWith("-")) {						// so the proceeding ends the next day
                    String offb = line[4+delta].substring(0,2)+line[4+delta].substring(3,5);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    line=st.nextToken().split(" ");						// get the next line - CAUTION!
                    if ( (Pattern.matches("[A-Z][a-z]",line[0])) && (Pattern.matches("[0-9]{2}",line[1])) ) { // is it a date?
                        delta=2;
                        tag++;
                        tagS=Integer.toString(tag);
                        if (tag<10) tagS="0"+tagS;
                    } else delta=0;
                    if ( line.length>=3 ) {
                        String onb = line[1+delta].substring(1,3)+line[1+delta].substring(4,6);
                        String to = line[2+delta];
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                        sb.append("SUMMARY:GT/"+duty+": "+from+"-"+to+"\r\n");
                        sb.append("DESCRIPTION:\r\n");
                        sb.append("END:VEVENT\r\n");
                    } else {
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                        sb.append("SUMMARY:Fehler!\r\n");
                        sb.append("DESCRIPTION:Bitte melden!\r\n");
                        sb.append("END:VEVENT\r\n");
                    }
                } else {													// the proceeding ends the same day
                    String offb = line[4+delta].substring(0,2)+line[4+delta].substring(3,5);
                    String onb = line[4+delta].substring(6,8)+line[4+delta].substring(9,11);
                    if ( line.length>(5+delta) ) {
                        String to = line[5+delta];
                        sb.append("BEGIN:VEVENT\r\n");
                        sb.append("UID:"+uid+"\r\n");
                        sb.append("DTSTAMP:"+dtstamp+"\r\n");
                        sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                        sb.append("SUMMARY:GT/"+duty+": "+from+"-"+to+"\r\n");
                        sb.append("DESCRIPTION:\r\n");
                        sb.append("END:VEVENT\r\n");
                    } else {						// error: one entry is missing
                        sb.append("BEGIN:VEVENT\r\n");
                        sb.append("UID:"+uid+"\r\n");
                        sb.append("DTSTAMP:"+dtstamp+"\r\n");
                        sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                        sb.append("SUMMARY:Fehler\r\n");
                        sb.append("DESCRIPTION:Bitte melden!\r\n");
                        sb.append("END:VEVENT\r\n");
                    }
                }
            }
            
            // SIM LSW BZW RZ
            // A300 OT2OP300 FRA 09:05-13:05 4 4 12 CHOPC
            else if (line.length>=1+delta && line[0+delta].equals("SIM")) {
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                line=st.nextToken().split(" ");						// get the next line - CAUTION! No check implemented yet
                if (line[0].equals("Bem:") || line[0].equals("Bemerkung:")) line=st.nextToken().split(" ");     // bis zu zwei Bemerkungszeilen überspringen
                if (line[0].equals("Bem:") || line[0].equals("Bemerkung:")) line=st.nextToken().split(" ");                
                String ac = line[0];
                String where = line[2];
                String offb = line[3].substring(0,2)+line[3].substring(3,5);
                sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                String onb="";
                if (line[3].length()==6) {       // Der SIM geht über Mitternacht
                    line=st.nextToken().split(" ");
                    onb = line[2].substring(1,3)+line[2].substring(4,6);

                    GregorianCalendar cal = new GregorianCalendar();
                    cal.setTimeZone( TimeZone.getTimeZone("ECT") );
                    cal.set(Calendar.DATE, tag);
                    cal.set(Calendar.MONTH, monat-1);
                    cal.set(Calendar.YEAR, jahr);
                    cal.add(Calendar.DATE, 1);
                    tag = cal.get(Calendar.DATE);
                    monat = cal.get(Calendar.MONTH)+1;
                    jahr = cal.get(Calendar.YEAR);

                    tagS=Integer.toString(tag);
                    if (tag<10) tagS="0"+tagS;
                    monatS=Integer.toString(monat);
                    if (monat<10) monatS="0"+monatS;
                    jahrS=Integer.toString(jahr);
                }
                else onb = line[3].substring(6,8)+line[3].substring(9,11);
                sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                sb.append("SUMMARY:"+ac+"-SIM\r\n");
                sb.append("DESCRIPTION:SIM-Ort: "+where+"\r\n");
                sb.append("END:VEVENT\r\n");
            }

            // OTX/O , Di 14 OTX/O DB002 05:35-08:00   - Anreise SIM
            else if ( line.length>=3+delta && line[0+delta].equals("OTX/O") ) {
                if (line[2+delta].endsWith("-")) {			// so the An-/Abreise ends the next day
                    String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    line=st.nextToken().split(" ");						// get the next line - CAUTION!
                    if ( (Pattern.matches("[A-Z][a-z]",line[0])) && (Pattern.matches("[0-9]{2}",line[1])) ) { // is it a date?
                        delta=2;
                        tag++;
                        tagS=Integer.toString(tag);
                        if (tag<10) tagS="0"+tagS;
                    } else delta=0;
                    if ( line.length>=2 ) {
                        String onb = line[1+delta].substring(1,3)+line[2+delta].substring(4,6);
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                        sb.append("SUMMARY:Anreise SIM\r\n");
                        sb.append("END:VEVENT\r\n");
                    } else {
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                        sb.append("SUMMARY:Fehler!\r\n");
                        sb.append("DESCRIPTION:Bitte melden!\r\n");
                        sb.append("END:VEVENT\r\n");
                    }
                } else {													// the Standby ends the same day
                    // Fr 27 OTX/O   LH114   MUC    15:25-16:30
                    // Das "MUC" scheint optional zu sein, insofern überspringe ich es ggf.
                    if (Pattern.matches("[A-Z]{3}",line[2+delta])) delta++;
                    String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                    String onb = line[2+delta].substring(6,8)+line[2+delta].substring(9,11);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                    sb.append("SUMMARY:Anreise SIM\r\n");
                    sb.append("END:VEVENT\r\n");
                }
            }
            
            // OTX/R , Di 14 OTX/O DB002 05:35-08:00   - Abreise SIM
            else if ( line.length>=3+delta && line[0+delta].equals("OTX/R") ) {
                if (line[2+delta].endsWith("-")) {			// so the An-/Abreise ends the next day
                    String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    line=st.nextToken().split(" ");						// get the next line - CAUTION!
                    if ( (Pattern.matches("[A-Z][a-z]",line[0])) && (Pattern.matches("[0-9]{2}",line[1])) ) { // is it a date?
                        delta=2;
                        tag++;
                        tagS=Integer.toString(tag);
                        if (tag<10) tagS="0"+tagS;
                    } else delta=0;
                    if ( line.length>=2 ) {
                        String onb = line[1+delta].substring(1,3)+line[2+delta].substring(4,6);
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                        sb.append("SUMMARY:Abreise SIM\r\n");
                        sb.append("END:VEVENT\r\n");
                    } else {
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                        sb.append("SUMMARY:Fehler!\r\n");
                        sb.append("DESCRIPTION:Bitte melden!\r\n");
                        sb.append("END:VEVENT\r\n");
                    }
                } else {													// the Standby ends the same day
                    String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                    String onb = line[2+delta].substring(6,8)+line[2+delta].substring(9,11);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                    sb.append("SUMMARY:Abreise SIM\r\n");
                    sb.append("END:VEVENT\r\n");
                }
            }

            // OTX/OR, Di 14 OTX/OR DB002 05:35-08:00   - An-/Abreise SIM
            else if ( line.length>=3+delta && Pattern.matches("OTX.*",line[0+delta]) ) {
                if (line[2+delta].endsWith("-")) {			// so the An-/Abreise ends the next day
                    String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    line=st.nextToken().split(" ");						// get the next line - CAUTION!
                    if ( (Pattern.matches("[A-Z][a-z]",line[0])) && (Pattern.matches("[0-9]{2}",line[1])) ) { // is it a date?
                        delta=2;
                        tag++;
                        tagS=Integer.toString(tag);
                        if (tag<10) tagS="0"+tagS;
                    } else delta=0;
                    if ( line.length>=2 ) {
                        String onb = line[1+delta].substring(1,3)+line[2+delta].substring(4,6);
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                        sb.append("SUMMARY:An-/Abreise SIM\r\n");
                        sb.append("END:VEVENT\r\n");
                    } else {
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                        sb.append("SUMMARY:Fehler!\r\n");
                        sb.append("DESCRIPTION:Bitte melden!\r\n");
                        sb.append("END:VEVENT\r\n");
                    }
                } else {
                    // Some OTX duties are followed by a location, like OTX/OR BER 05:35-08:00
                    if (Pattern.matches("[A-Z]{3}", line[2+delta]))
                        delta++;
                    String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                    String onb = line[2+delta].substring(6,8)+line[2+delta].substring(9,11);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                    sb.append("SUMMARY:An-/Abreise SIM\r\n");
                    sb.append("END:VEVENT\r\n");
                    
                }
            }

            // TLX/O , Di 14 TLX/O DB002 05:35-08:00   - Anreise SIM
            else if ( line.length>=3+delta && line[0+delta].equals("TLX/O") ) {
                if (line[2+delta].endsWith("-")) {			// so the An-/Abreise ends the next day
                    String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    line=st.nextToken().split(" ");						// get the next line - CAUTION!
                    if ( (Pattern.matches("[A-Z][a-z]",line[0])) && (Pattern.matches("[0-9]{2}",line[1])) ) { // is it a date?
                        delta=2;
                        tag++;
                        tagS=Integer.toString(tag);
                        if (tag<10) tagS="0"+tagS;
                    } else delta=0;
                    if ( line.length>=2 ) {
                        String onb = line[1+delta].substring(1,3)+line[2+delta].substring(4,6);
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                        sb.append("SUMMARY:Anreise SIM\r\n");
                        sb.append("END:VEVENT\r\n");
                    } else {
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                        sb.append("SUMMARY:Fehler!\r\n");
                        sb.append("DESCRIPTION:Bitte melden!\r\n");
                        sb.append("END:VEVENT\r\n");
                    }
                } else {
                    String offb = line[line.length-1].substring(0,2)+line[line.length-1].substring(3,5);
                    String onb = line[line.length-1].substring(6,8)+line[line.length-1].substring(9,11);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                    sb.append("SUMMARY:Anreise SIM\r\n");
                    sb.append("END:VEVENT\r\n");
                }
            }
            
            // TLX/R , Di 14 OTX/O DB002 05:35-08:00   - Abreise SIM
            else if ( line.length>=3+delta && line[0+delta].equals("TLX/R") ) {
                if (line[2+delta].endsWith("-")) {			// so the An-/Abreise ends the next day
                    String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    line=st.nextToken().split(" ");						// get the next line - CAUTION!
                    if ( (Pattern.matches("[A-Z][a-z]",line[0])) && (Pattern.matches("[0-9]{2}",line[1])) ) { // is it a date?
                        delta=2;
                        tag++;
                        tagS=Integer.toString(tag);
                        if (tag<10) tagS="0"+tagS;
                    } else delta=0;
                    if ( line.length>=2 ) {
                        String onb = line[1+delta].substring(1,3)+line[2+delta].substring(4,6);
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                        sb.append("SUMMARY:Abreise SIM\r\n");
                        sb.append("END:VEVENT\r\n");
                    } else {
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                        sb.append("SUMMARY:Fehler!\r\n");
                        sb.append("DESCRIPTION:Bitte melden!\r\n");
                        sb.append("END:VEVENT\r\n");
                    }
                } else {													// the Standby ends the same day
                    String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                    String onb = line[2+delta].substring(6,8)+line[2+delta].substring(9,11);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                    sb.append("SUMMARY:Abreise SIM\r\n");
                    sb.append("END:VEVENT\r\n");
                }
            }

            // TLX/OR, Di 14 OTX/OR DB002 05:35-08:00   - An-/Abreise SIM
            else if ( line.length>=3+delta && Pattern.matches("TLX.*",line[0+delta]) ) {
                if (line[2+delta].endsWith("-")) {			// so the An-/Abreise ends the next day
                    String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    line=st.nextToken().split(" ");						// get the next line - CAUTION!
                    if ( (Pattern.matches("[A-Z][a-z]",line[0])) && (Pattern.matches("[0-9]{2}",line[1])) ) { // is it a date?
                        delta=2;
                        tag++;
                        tagS=Integer.toString(tag);
                        if (tag<10) tagS="0"+tagS;
                    } else delta=0;
                    if ( line.length>=2 ) {
                        String onb = line[1+delta].substring(1,3)+line[2+delta].substring(4,6);
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                        sb.append("SUMMARY:An-/Abreise SIM\r\n");
                        sb.append("END:VEVENT\r\n");
                    } else {
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                        sb.append("SUMMARY:Fehler!\r\n");
                        sb.append("DESCRIPTION:Bitte melden!\r\n");
                        sb.append("END:VEVENT\r\n");
                    }
                } else {													// the Standby ends the same day
                    String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                    String onb = line[2+delta].substring(6,8)+line[2+delta].substring(9,11);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                    sb.append("SUMMARY:An-/Abreise SIM\r\n");
                    sb.append("END:VEVENT\r\n");
                    
                }
            }

            // TCX/O , Di 14 TLX/O DB002 05:35-08:00   - Anreise SIM
            else if ( line.length>=3+delta && line[0+delta].equals("TCX/O") ) {
                if (line[2+delta].endsWith("-")) {			// so the An-/Abreise ends the next day
                    String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    line=st.nextToken().split(" ");						// get the next line - CAUTION!
                    if ( (Pattern.matches("[A-Z][a-z]",line[0])) && (Pattern.matches("[0-9]{2}",line[1])) ) { // is it a date?
                        delta=2;
                        tag++;
                        tagS=Integer.toString(tag);
                        if (tag<10) tagS="0"+tagS;
                    } else delta=0;
                    if ( line.length>=2 ) {
                        String onb = line[1+delta].substring(1,3)+line[2+delta].substring(4,6);
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                        sb.append("SUMMARY:Anreise SIM\r\n");
                        sb.append("END:VEVENT\r\n");
                    } else {
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                        sb.append("SUMMARY:Fehler!\r\n");
                        sb.append("DESCRIPTION:Bitte melden!\r\n");
                        sb.append("END:VEVENT\r\n");
                    }
                } else {													// the Standby ends the same day
                    String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                    String onb = line[2+delta].substring(6,8)+line[2+delta].substring(9,11);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                    sb.append("SUMMARY:Anreise SIM\r\n");
                    sb.append("END:VEVENT\r\n");
                }
            }
            
            // TCX/R , Di 14 OTX/O DB002 05:35-08:00   - Abreise SIM
            else if ( line.length>=3+delta && line[0+delta].equals("TCX/R") ) {
                if (line[2+delta].endsWith("-")) {			// so the An-/Abreise ends the next day
                    String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    line=st.nextToken().split(" ");						// get the next line - CAUTION!
                    if ( (Pattern.matches("[A-Z][a-z]",line[0])) && (Pattern.matches("[0-9]{2}",line[1])) ) { // is it a date?
                        delta=2;
                        tag++;
                        tagS=Integer.toString(tag);
                        if (tag<10) tagS="0"+tagS;
                    } else delta=0;
                    if ( line.length>=2 ) {
                        String onb = line[1+delta].substring(1,3)+line[2+delta].substring(4,6);
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                        sb.append("SUMMARY:Abreise SIM\r\n");
                        sb.append("END:VEVENT\r\n");
                    } else {
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                        sb.append("SUMMARY:Fehler!\r\n");
                        sb.append("DESCRIPTION:Bitte melden!\r\n");
                        sb.append("END:VEVENT\r\n");
                    }
                } else {													// the Standby ends the same day
                    String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                    String onb = line[2+delta].substring(6,8)+line[2+delta].substring(9,11);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                    sb.append("SUMMARY:Abreise SIM\r\n");
                    sb.append("END:VEVENT\r\n");
                }
            }

            // TCX/OR, Di 14 OTX/OR DB002 05:35-08:00   - An-/Abreise SIM
            else if ( line.length>=3+delta && Pattern.matches("TCX.*",line[0+delta]) ) {
                if (line[2+delta].endsWith("-")) {			// so the An-/Abreise ends the next day
                    String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    line=st.nextToken().split(" ");						// get the next line - CAUTION!
                    if ( (Pattern.matches("[A-Z][a-z]",line[0])) && (Pattern.matches("[0-9]{2}",line[1])) ) { // is it a date?
                        delta=2;
                        tag++;
                        tagS=Integer.toString(tag);
                        if (tag<10) tagS="0"+tagS;
                    } else delta=0;
                    if ( line.length>=2 ) {
                        String onb = line[1+delta].substring(1,3)+line[2+delta].substring(4,6);
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                        sb.append("SUMMARY:An-/Abreise SIM\r\n");
                        sb.append("END:VEVENT\r\n");
                    } else {
                        sb.append("DTEND:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                        sb.append("SUMMARY:Fehler!\r\n");
                        sb.append("DESCRIPTION:Bitte melden!\r\n");
                        sb.append("END:VEVENT\r\n");
                    }
                } else {													// the Standby ends the same day
                    String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                    String onb = line[2+delta].substring(6,8)+line[2+delta].substring(9,11);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                    sb.append("SUMMARY:An-/Abreise SIM\r\n");
                    sb.append("END:VEVENT\r\n");
                    
                }
            }

            // Seminar SExxxx
            else if (line.length >= 4 && Pattern.matches("SE\\d{4}",line[1])) {
                Pattern p = Pattern.compile(".+(SE\\d{4})\\W?(\\w{3})?\\W+(\\d{2}:\\d{2}-\\d{2}:\\d{2})");
                Matcher m = p.matcher(whole_line);
                if (m.matches()) {
                    String seminar = m.group(1);
                    String time = m.group(m.groupCount());
                    String offb = time.substring(0,2) + time.substring(3,5);
                    String onb = time.substring(6,8) + time.substring(9,11);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:"+uid+"\r\n");
                    sb.append("DTSTAMP:"+dtstamp+"\r\n");
                    sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                    sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                    sb.append("SUMMARY:Seminar " + seminar + "\r\n");
                    sb.append("END:VEVENT\r\n");                                    
                }
                else System.out.println("Kein Match!");
            }
            
            // QF
            else if ( line.length>=2+delta && line[0+delta].equals("QF") ) {
                String offb = line[1+delta].substring(0,2)+line[1+delta].substring(3,5);
                String onb = line[1+delta].substring(6,8)+line[1+delta].substring(9,11);
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                sb.append("SUMMARY:QF\r\n");
                sb.append("END:VEVENT\r\n");
            }

            // QA
            else if ( line.length>=2+delta && line[0+delta].equals("QA") ) {
                String offb = line[1+delta].substring(0,2)+line[1+delta].substring(3,5);
                String onb = line[1+delta].substring(6,8)+line[1+delta].substring(9,11);
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                sb.append("SUMMARY:QA\r\n");
                sb.append("END:VEVENT\r\n");
            }
            
            // EH
            else if ( line.length>=4+delta && line[0+delta].equals("EH") ) {
                String where = line[2+delta];
                String offb = line[3+delta].substring(0,2)+line[3+delta].substring(3,5);
                String onb = line[3+delta].substring(6,8)+line[3+delta].substring(9,11);
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                sb.append("SUMMARY:EH in "+where+"\r\n");
                sb.append("END:VEVENT\r\n");
            }

            // TKFIT
            else if ( line.length>=3+delta && line[0+delta].equals("TKFIT") ) {
                String where = line[1+delta];
                String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                String onb = line[2+delta].substring(6,8)+line[2+delta].substring(9,11);
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                sb.append("SUMMARY:TKFIT in "+where+"\r\n");
                sb.append("END:VEVENT\r\n");
            }
            
            // EMCRM
            else if ( line.length>=4+delta && line[0+delta].equals("EMCRM") ) {
                String where = line[2+delta];
                String offb = line[3+delta].substring(0,2)+line[3+delta].substring(3,5);
                String onb = line[3+delta].substring(6,8)+line[3+delta].substring(9,11);
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                sb.append("SUMMARY:EMCRM in "+where+"\r\n");
                sb.append("END:VEVENT\r\n");
            }

            // TK/SEF
            else if ( line.length>=3+delta && line[0+delta].equals("TK/SEF") ) {
                String where = line[1+delta];
                String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                String onb = line[2+delta].substring(6,8)+line[2+delta].substring(9,11);
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                sb.append("SUMMARY:TK/SEF in "+where+"\r\n");
                sb.append("END:VEVENT\r\n");
            }
            
            // DS_F
            else if ( line.length>=2+delta && line[0+delta].equals("DS_F") ) {
                String offb = line[1+delta].substring(0,2)+line[1+delta].substring(3,5);
                String onb = line[1+delta].substring(6,8)+line[1+delta].substring(9,11);
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                sb.append("SUMMARY:Teamtisch F\r\n");
                sb.append("END:VEVENT\r\n");
            }

            // DS_M
            else if ( line.length>=2+delta && line[0+delta].equals("DS_M") ) {
                String offb = line[1+delta].substring(0,2)+line[1+delta].substring(3,5);
                String onb = line[1+delta].substring(6,8)+line[1+delta].substring(9,11);
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                sb.append("SUMMARY:Teamtisch M\r\n");
                sb.append("END:VEVENT\r\n");
            }
            
            // DS_S
            else if ( line.length>=2+delta && line[0+delta].equals("DS_S") ) {
                String offb = line[1+delta].substring(0,2)+line[1+delta].substring(3,5);
                String onb = line[1+delta].substring(6,8)+line[1+delta].substring(9,11);
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                sb.append("SUMMARY:Teamtisch S\r\n");
                sb.append("END:VEVENT\r\n");
            }
            
            // EK
            else if ( line.length>=2+delta && line[0+delta].equals("EK") ) {
                String offb = line[1+delta].substring(0,2)+line[1+delta].substring(3,5);
                String onb = line[1+delta].substring(6,8)+line[1+delta].substring(9,11);
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                sb.append("SUMMARY:EK\r\n");
                sb.append("END:VEVENT\r\n");
            }
            
            // DT
            else if ( line.length>=2+delta && line[0+delta].equals("DT") ) {
                String offb = line[1+delta].substring(0,2)+line[1+delta].substring(3,5);
                String onb = line[1+delta].substring(6,8)+line[1+delta].substring(9,11);
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                sb.append("SUMMARY:DT\r\n");
                sb.append("END:VEVENT\r\n");
            }

            // EMCRM
            else if ( line.length>=2+delta && line[0+delta].equals("EMCRM") ) {
                String where = line[2+delta]; 
                String offb = line[3+delta].substring(0,2)+line[3+delta].substring(3,5);
                String onb = line[3+delta].substring(6,8)+line[3+delta].substring(9,11);
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                sb.append("SUMMARY:EMCRM\r\n");
                sb.append("DESCRIPTION:"+where+"\r\n");
                sb.append("END:VEVENT\r\n");
            }

            // EM
            else if (line.length==5+delta && line[0+delta].equals("EM") ) {
                String where = line[3+delta];
                String what = line[2+delta];
                String offb = line[4+delta].substring(0,2)+line[4+delta].substring(3,5);
                String onb = line[4+delta].substring(6,8)+line[4+delta].substring(9,11);
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                sb.append("SUMMARY:EM\r\n");
                sb.append("DESCRIPTION:"+what+" - "+where+"\r\n");
                sb.append("END:VEVENT\r\n");
            }
            
            // EM in der zweiten Variante
            else if (line.length==4+delta && line[0+delta].equals("EM") ) {
                String where = line[2+delta];
                String what = line[1+delta];
                String offb = line[3+delta].substring(0,2)+line[3+delta].substring(3,5);
                String onb = line[3+delta].substring(6,8)+line[3+delta].substring(9,11);
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                sb.append("SUMMARY:EM\r\n");
                sb.append("DESCRIPTION:"+what+" - "+where+"\r\n");
                sb.append("END:VEVENT\r\n");
            }

            // DP_PILO - Pilots meeting Langstrecke
            else if ( line.length>=2+delta && line[0+delta].equals("DP_PILO") ) {
                String offb = line[1+delta].substring(0,2)+"00";
                String onb = line[1+delta].substring(6,8)+"00";
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                sb.append("SUMMARY:Pilotsmeeting\r\n");
                sb.append("DESCRIPTION:Langstrecke\r\n");
                sb.append("END:VEVENT\r\n");
            }
            
            // DP_PIM - Pilots meeting Kurzstrecke
            else if ( line.length>=2+delta && line[0+delta].equals("DP_PIM") ) {
                String offb = line[1+delta].substring(0,2)+"00";
                String onb = line[1+delta].substring(6,8)+"00";
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                sb.append("SUMMARY:Pilotsmeeting\r\n");
                sb.append("DESCRIPTION:Kurzstrecke\r\n");
                sb.append("END:VEVENT\r\n");
            }

            // MED
            else if ( line.length>=3+delta && line[0+delta].equals("MED") ) {
                if (line[1+delta].length() == 1)
                    delta++;
                String where = line[1+delta];
                String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                String onb = line[2+delta].substring(6,8)+line[2+delta].substring(9,11);
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                sb.append("SUMMARY:Medical\r\n");
                sb.append("DESCRIPTION:"+where+"\r\n");
                sb.append("END:VEVENT\r\n");
            }

            // Conversion
            else if ( line.length>=1+delta && line[0+delta].endsWith("-CONVERSION") ) {
                String what = line[0+delta];

                line=st.nextToken().split(" ");
                if (line.length >= 3) {
                    String offb = line[2].substring(0, 2) + line[2].substring(3, 5);
                    String onb = line[2].substring(6, 8) + line[2].substring(9, 11);
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:" + uid + "\r\n");
                    sb.append("DTSTAMP:" + dtstamp + "\r\n");
                    sb.append("DTSTART:" + jahrS + monatS + tagS + "T" + offb + "00Z\r\n");
                    sb.append("DTEND:" + jahrS + monatS + tagS + "T" + onb + "00Z\r\n");
                    sb.append("SUMMARY:"+what+"\r\n");
                    sb.append("END:VEVENT\r\n");
                }
            }
            
            // Allgemeines Format: xx DIENST  ORT 08:30-17:00
            else if ( line.length >= 3 + delta && Pattern.matches("\\w{2,}",line[1 + delta]) && Pattern.matches("\\d{2}:\\d{2}-\\d{2}:\\d{2}",line[2 + delta])) {
                String offb = line[2+delta].substring(0,2)+line[2+delta].substring(3,5);
                String onb = line[2+delta].substring(6,8)+line[2+delta].substring(9,11);
                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:"+uid+"\r\n");
                sb.append("DTSTAMP:"+dtstamp+"\r\n");
                sb.append("DTSTART:"+jahrS+monatS+tagS+"T"+offb+"00Z\r\n");
                sb.append("DTEND:"+jahrS+monatS+tagS+"T"+onb+"00Z\r\n");
                sb.append("SUMMARY:" + line[0 + delta] + "\r\n");
                sb.append("LOCATION:" + line[1 + delta] + "\r\n");
                sb.append("END:VEVENT\r\n");                
            }


            
            else {
                // Zeile entspricht keinem bekannten Format und wird ignoriert.
            }
            
            
        }
        
        // we've got to finish the ics data:
        sb.append("END:VCALENDAR\r\n");
        
        // now we've got to save the ics data to the selected file
        Writer fw = null;
        try {
            fw = new FileWriter(datei);
            fw.write(sb.toString());
        } catch ( IOException e ) {
            System.err.println( "Konnte Datei nicht erstellen" );
        } finally {
            if ( fw != null )
                try { fw.close(); } catch ( IOException e ) { e.printStackTrace(); }
        }
        
        return true;
    }
    
    
    /**
     * Liefert eine kurze, stabile Kennung für einen Namen (die ersten
     * 12 Hex-Zeichen des SHA-256-Hashes). Wird in den Termin-UIDs statt des
     * Klarnamens verwendet.
     */
    static String nameHash(String n) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(n.replace(" ", "").getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 6; i++)
                hex.append(String.format("%02x", digest[i] & 0xff));
            return hex.toString();
        } catch (Exception e) {     // SHA-256 und UTF-8 sind in jeder JVM vorhanden
            return Integer.toHexString(n.replace(" ", "").hashCode());
        }
    }

    /** Aktiviert Speichern-Knopf und Plan-Informationen nach erfolgreichem Einlesen. */
    public void enableVisuals() {
        saveB.setEnabled(true);			// enable the save button
        nameL.setText("Name: "+name);
        nameL.setEnabled(true);
        stationierungL.setText("Stationierung: "+base);
        stationierungL.setEnabled(true);
        zeitraumL.setText("Zeitraum: "+dutyPeriod);
        zeitraumL.setEnabled(true);
    }
    
    /** Setzt die Oberfläche zurück, wenn ein Plan nicht eingelesen werden konnte. */
    public void disableVisuals() {
        saveB.setEnabled(false);			// disable the save button
        nameL.setText("Name: ");
        nameL.setEnabled(false);
        stationierungL.setText("Stationierung: ");
        stationierungL.setEnabled(false);
        zeitraumL.setText("Zeitraum: ");
        zeitraumL.setEnabled(false);
    }
    
    /** Reagiert auf die Knöpfe "open" (PDF einlesen) und "save" (.ics schreiben). */
    public void actionPerformed(ActionEvent event) {
        String temp = event.getActionCommand();
        if (temp.equals("open")) {	// the user wants to open a pdf file
            JFileChooser openFC = new JFileChooser(prefs.get("open", ""));
            openFC.setFileFilter(new PDFFilter());		// activate the pdf filter
            int status = openFC.showOpenDialog(f);		// get the status of the closed file chooser
            if (status == JFileChooser.APPROVE_OPTION) {	// the user has selected a file - hopefully valid
                File datei = openFC.getSelectedFile();
                if (decodeFile(datei)) {				// decoding was successful
                    enableVisuals();					// enable the buttons and labels
                    prefs.put("open", datei.getParent());    // store the location of the last pdf
                } else {								// so there was an error in decoding
                    disableVisuals();				// deactivate the buttons and labels
                    JOptionPane.showMessageDialog( f, "Es ist ein Fehler aufgetreten:\nEntweder konnte auf die Datei nicht zugegriffen\nwerden oder sie enthält fehlerhafte Daten.","Fehler",JOptionPane.ERROR_MESSAGE);
                }
            }
        } else if (temp.equals("save")) {	// the user wants to save the plan
            JFileChooser saveFC = new JFileChooser(prefs.get("save", ""));
            String new_file_name = name;
            if (new_file_name.length() > 8)
                new_file_name = new_file_name.substring(0, 8);
            File datei = new File(new_file_name + " - "+dutyPeriod+".ics");
            saveFC.setSelectedFile(datei);
            int status = saveFC.showSaveDialog(f);		// get the status of the closed file chooser
            if (status == JFileChooser.APPROVE_OPTION) {	// the user has selected a file - hopefully valid
                datei = saveFC.getSelectedFile();
                if (encodeFile(datei)) {				// encoding was successful
                    prefs.put("save", datei.getParent());
                    JOptionPane.showMessageDialog( f, "Die Datei wurde erfolgreich geschrieben.","Hinweis",JOptionPane.INFORMATION_MESSAGE);
                } else {								// so there was an error in decoding
                    JOptionPane.showMessageDialog( f, "Beim Schreiben der Datei ist ein Fehler aufgetreten.\nBitte prüfen Sie die Zugriffsrechte.","Fehler",JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }
    
    
    /** Erzeugt die Oberfläche; muss im Event-Dispatch-Thread aufgerufen werden. */
    private static void createAndShowGUI() {
        LH_DPC converter = new LH_DPC();
    }
    
    public static void main(String[] args) {
        // Swing-Komponenten müssen im Event-Dispatch-Thread erzeugt werden.
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                createAndShowGUI();
            }
        });
    }
}

/** Dateifilter für den Öffnen-Dialog: zeigt nur Dateien mit der Endung .pdf an (Groß-/Kleinschreibung egal). */
class PDFFilter extends javax.swing.filechooser.FileFilter {
    
    public boolean accept(File f) {
        String extension = f.getName();
        String ext = null;
        int i = extension.lastIndexOf('.');
        
        if (i > 0 &&  i < extension.length() - 1) {
            ext = extension.substring(i+1).toLowerCase();
        }
        
        if (ext != null) {
            if (ext.equals("pdf") || ext.equals("PDF") ) {
                return true;
            } else {
                return false;
            }
        }
        return false;
    }
    
    public String getDescription() {
        return "PDF-Dateien";
    }
}