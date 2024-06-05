package client;

/* FileCopyClient.java
 Version 0.1 - Muss erg�nzt werden!!
 Praktikum 3 Rechnernetze BAI4 HAW Hamburg
 Autoren:
 Janssen, Lars
 Lüdeke, Leonhard
 */

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import server.FC_Timer;
import server.FCpacket;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

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

  public FCpacket[] sendPuffer; // Sendepuffer mit N Plätzen (N: Window-Größe)

  int sendBaseIdx = 0; // spiegelt den idx im sendPuffer wieder (Sequenznummer des ältesten Pakets im Sendepuffer)

  int nextSeqNum = 1; // (Sequenznummer des nächsten zu sendenden Pakets)

  private byte[] buffer = new byte[UDP_PACKET_SIZE]; // wirklich die packet size?
  
  private String facility = "Client";

  // Constructor
  public FileCopyClient(String serverArg, String sourcePathArg,
    String destPathArg, String windowSizeArg, String errorRateArg) throws SocketException {
    servername = serverArg;
    sourcePath = sourcePathArg;
    destPath = destPathArg;
    windowSize = Integer.parseInt(windowSizeArg);
    serverErrorRate = Long.parseLong(errorRateArg);
    socket = new DatagramSocket();
    sendPuffer = new FCpacket[windowSize];
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


  public void runFileCopyClient() throws Exception {
      File file = new File(sourcePath);
      FileInputStream fileInputStream = new FileInputStream(file);
      long fileSizeInBytes = file.length();
      syslog(facility,1,"FileSize: " + fileSizeInBytes);

      FCpacket fcControl = makeControlPacket();

     // FCpacket testPacket = new FCpacket(0,seqBytes,seqBytes.length);
      socket.send(new DatagramPacket(fcControl.getSeqNumBytesAndData(), fcControl.getLen(),InetAddress.getLoopbackAddress(),SERVER_PORT));

      DatagramPacket ackPacket = new DatagramPacket(buffer, buffer.length);
      socket.receive(ackPacket);
      FCpacket fCpacket = new FCpacket(ackPacket.getData(), ackPacket.getLength());
      syslog(facility,8,"Ack Packet: " + fCpacket.toString());

      syslog(facility,8,"Initial Packet gesendet.");
      //Solange bytesFromSourceData noch daten hat
      while (fileSizeInBytes > 0) {
        byte[] bytesToSend = new byte[UDP_PACKET_SIZE];

        //bytesToSend befüllen
        for (int i = 0; i < UDP_PACKET_SIZE; i++) {
          bytesToSend[i] = (byte)fileInputStream.read();
        }
        FCpacket fcPacket = new FCpacket(nextSeqNum,bytesToSend,bytesToSend.length);
        sendPuffer[nextSeqNum - 1] = fcPacket;

        DatagramPacket sendetData = new DatagramPacket(fcPacket.getData(), UDP_PACKET_SIZE,InetAddress.getLoopbackAddress(),SERVER_PORT);

        fileSizeInBytes -= UDP_PACKET_SIZE;
        nextSeqNum++;
        socket.send(sendetData);

        DatagramPacket dataackPacket = new DatagramPacket(buffer, buffer.length);
        socket.receive(dataackPacket);
        String recieve = new String(dataackPacket.getData());
        syslog(facility,8,"Ack Packet: " + recieve);
      }
      socket.close();
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
    return new FCpacket(0, sendData, sendData.length);
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
