package client;

/* FileCopyClient.java
 Version 0.1 - Muss erg�nzt werden!!
 Praktikum 3 Rechnernetze BAI4 HAW Hamburg
 Autoren:
 */

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
  }

  private byte intToByte(int x) {
    return (byte) (x & 0xFF);
  }

  private void sendInitialPacket() {
    byte[] dest = destPath.getBytes(StandardCharsets.UTF_8);
    byte window = intToByte(windowSize);
    byte[] errorRate = Longs.toByteArray(serverErrorRate);
    byte[] params = combineArrays(dest, window, errorRate);
    FCpacket fCpacketTmp = new FCpacket(0,params,params.length);
    DatagramPacket initialPacket = new DatagramPacket(fCpacketTmp.getData(),params.length,InetAddress.getLoopbackAddress(),SERVER_PORT);
    try {
      socket.send(initialPacket);
    } catch (IOException e) {
      syslog(facility, 1, "Could not send initial packet");
    }

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

  public void runFileCopyClient() throws IOException {
      FileInputStream fileInputStream = new FileInputStream(sourcePath);
      sendInitialPacket();
      //Solange bytesFromSourceData noch daten hat
      /*while (fileInputStream.available() > 0) {
        byte[] bytesToSend = new byte[UDP_PACKET_SIZE];
        //TODO Unsere ersten 8 sequenzbytes in bytes to Send
        //bytesToSend befüllen
        for (int i = 0; i < UDP_PACKET_SIZE; i++) {
          bytesToSend[i] = (byte)fileInputStream.read();
        }
        FCpacket fcPacket = new FCpacket(0,bytesToSend,bytesToSend.length);
        DatagramPacket sendetData = new DatagramPacket(fcPacket.getData(), UDP_PACKET_SIZE,InetAddress.getLoopbackAddress(),SERVER_PORT);
        syslog(facility,8, "Byte Array: " + Arrays.toString(sendetData.getData()));
        socket.send(sendetData);
        // neues FCpacket absenden
      }*/
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
