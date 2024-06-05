package client;

/* FileCopyClient.java
 Version 0.1 - Muss erg�nzt werden!!
 Praktikum 3 Rechnernetze BAI4 HAW Hamburg
 Autoren:
 Janssen, Lars
 Lüdeke, Leonhard
 */

import server.FC_Timer;
import server.FCpacket;

import java.io.*;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static syslog.Syslog.syslog;

public class FileCopyClient extends Thread {

  // -------- Constants
  public final static boolean TEST_OUTPUT_MODE = false;

  public final int SERVER_PORT = 23000;

  public final int UDP_PACKET_SIZE = 1008;

  // -------- Public parms
  public String servername;

  public String sourcePath;

  public String destPath;

  public int windowSize;

  public long serverErrorRate;

  // -------- Variables
  // current default timeout in nanoseconds
  private long timeoutValue = 100000000L;

  // ... ToDo
  private DatagramSocket socket;

  private FileInputStream fileInputStream;

  private FCpacket[] sendetPackets; // Sendepuffer mit N Plätzen (N: Window-Größe)

  public final int PACKET_SIZE_WITHOUT_SEQ = UDP_PACKET_SIZE - 8;

  int nextSeqNum = 1; // (Sequenznummer des nächsten zu sendenden Pakets)

  public BlockingQueue<FCpacket> revieceQueue = new LinkedBlockingQueue<>();

  public BlockingQueue<byte[]> sendQueue = new LinkedBlockingQueue<>();
  
  private String facility = "Client";

  // Constructor
  public FileCopyClient(String serverArg, String sourcePathArg,
    String destPathArg, String windowSizeArg, String errorRateArg) throws SocketException {
    servername = serverArg;
    sourcePath = sourcePathArg;
    destPath = destPathArg;
    fileInputStream = openFileInputStream();
    windowSize = Integer.parseInt(windowSizeArg);
    serverErrorRate = Long.parseLong(errorRateArg);
    socket = new DatagramSocket(SERVER_PORT);
    sendetPackets = new FCpacket[windowSize];
  }

  private byte intToByte(int x) {
    return (byte) (x & 0xFF);
  }

  /**
   * Kombiniert die drei Eingaben in ein einzelnes Byte-Array mit Semikolons als Trennzeichen.
   *
   * @param dest Das Byte-Array für den Zielpfad.
   * @param window Das Byte für die Fenstergröße.
   * @param errorRate Das Byte-Array für die Fehlerrate.
   * @return Ein Byte-Array, das alle Eingaben mit Semikolons getrennt kombiniert.
   */
  public static byte[] combineArrays(byte[] dest, byte window, byte[] errorRate) {
    byte semicolon = (byte) ';';
    int combinedLength = dest.length + 1 + 1 + 1 + errorRate.length; // dest + ';' + window + ';' + errorRate
    byte[] combined = new byte[combinedLength];
    int index = 0;

    // Kopiere das dest-Array
    System.arraycopy(dest, 0, combined, index, dest.length);
    index += dest.length;

    combined[index++] = semicolon; // Füge das Semikolon hinzu
    combined[index++] = window; // Füge das window-Byte hinzu
    combined[index++] = semicolon; // Füge das Semikolon hinzu

    // Kopiere das errorRate-Array
    System.arraycopy(errorRate, 0, combined, index, errorRate.length);

    return combined;
  }

  private FileInputStream openFileInputStream() {
    if (sourcePath != null) {
      File file = new File(sourcePath);
      try {
        return new FileInputStream(file);
      } catch (FileNotFoundException e) {
        syslog(facility,1,"ERROR: File not Found");
      }
    }
    syslog(facility, 1, "ERROR: SourcePath is Null.");
    return null;
  }

  public void runFileCopyClient() throws Exception {
    while (socket.isConnected() && !socket.isClosed()) {
      FCpacket controlPacket = makeControlPacket();
      sendQueue.add(controlPacket.getSeqNumBytesAndData());

      FileCopyClientSend fileSend = new FileCopyClientSend(UDP_PACKET_SIZE, socket, sendQueue);

      fileSend.run();
      FileCopyClientRecive reviece = new FileCopyClientRecive(UDP_PACKET_SIZE, socket, revieceQueue);
      reviece.run();
      while (fileInputStream.available() > 0) {
        byte[] bytesToSend = new byte[PACKET_SIZE_WITHOUT_SEQ];

        //bytesToSend befüllen und die ersten 8 plätze freilassen.
        for (int i = 7; i < PACKET_SIZE_WITHOUT_SEQ; i++) {
          bytesToSend[i] = (byte)fileInputStream.read();
        }
        sendetPackets[nextSeqNum] = new FCpacket(nextSeqNum,bytesToSend);

      }
    }
      // 1. Sende kontroll packet
      // 2. Starte Konsole frage nach Parameter
      // 3. File öffnen und bytes einlesen und in ein FCPacket packen. Wollen wir das File einlesen hier machen?
      // 4. Timer starten
      // 5. senden (loop)
      // 6. nach ack Timer stoppen
      // 7. connection close
      // ToDo!!
  }

  /**
  * Timer Operations
  */
  public void startTimer(FCpacket packet) {
    /* Create, save and start timer for the given FCpacket */
    FC_Timer timer = new FC_Timer(timeoutValue, this, packet.getSeqNum());
    packet.setTimer(timer);
    timer.start();
  }

  public void cancelTimer(FCpacket packet) {
    /* Cancel timer for the given FCpacket */
    testOut("Cancel Timer for packet" + packet.getSeqNum());

    if (packet.getTimer() != null) {
      packet.getTimer().interrupt();
    }
  }

  /**
   * Implementation specific task performed at timeout
   * TODO Selective Repeat Verfahren
   */
  public void timeoutTask(long seqNum) {
  // ToDo
  }


  /**
   *
   * Computes the current timeout value (in nanoseconds)
   */
  public void computeTimeoutValue(long sampleRTT) {
  // ToDo
  }


  /**
   *
   * Return value: FCPacket with (0 destPath;windowSize;errorRate)
   */
  public FCpacket makeControlPacket() {
   /* Create first packet with seq num 0. Return value: FCPacket with
     (0 destPath ; windowSize ; errorRate) */
    String sendString = destPath + ";" + windowSize + ";" + serverErrorRate;
    byte[] sendData = null;
    syslog(facility, 8, "SendString: " + sendString);
    try {
      sendData = sendString.getBytes("UTF-8");
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    return new FCpacket(0, sendData);
  }

  public void testOut(String out) {
    if (TEST_OUTPUT_MODE) {
      System.err.printf("%,d %s: %s\n", System.nanoTime(), Thread
          .currentThread().getName(), out);
    }
  }

  public static void main(String argv[]) throws Exception {
    FileCopyClient myClient = new FileCopyClient(argv[0], argv[1], argv[2],
        argv[3], argv[4]);
    myClient.runFileCopyClient();
  }

}
