package client;

/* FileCopyClient.java
 Version 0.1 - Muss erg�nzt werden!!
 Praktikum 3 Rechnernetze BAI4 HAW Hamburg
 Autoren:
 Janssen, Lars
 Lüdeke, Leonhard
 */

import com.google.common.primitives.Ints;
import server.FC_Timer;
import server.FCpacket;

import java.io.*;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

import static syslog.Syslog.syslog;

public class FileCopyClient extends Thread {
// TODO receive rechtschreibung
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
  private long timeoutValue = 10000000L;

  // ... ToDo
  private DatagramSocket socket;

  private FileInputStream fileInputStream;

  private List<FCpacket> window;
  private List<Boolean> ackWindow;
  private Semaphore windowSemaphore;
  private long seqPointer;
  private long currentSeq;

  public final int PACKET_SIZE_WITHOUT_SEQ = UDP_PACKET_SIZE - 8;

  private String facility = "Client";

  public BlockingQueue<FCpacket> revieceQueue = new LinkedBlockingQueue<>();
  public BlockingQueue<byte[]> sendQueue = new LinkedBlockingQueue<>();

  private FileCopyClientSend fileSend;
  private FileCopyClientReceive receive;
  private Thread receiveThread;
  private Thread fileSendThread;

  // Constructor
  public FileCopyClient(String serverArg, String sourcePathArg,
    String destPathArg, String windowSizeArg, String errorRateArg) throws SocketException {
    servername = serverArg;
    sourcePath = sourcePathArg;
    destPath = destPathArg;
    fileInputStream = openFileInputStream();
    windowSize = Integer.parseInt(windowSizeArg);
    serverErrorRate = Long.parseLong(errorRateArg);
    socket = new DatagramSocket();

    try {
      fileSend = new FileCopyClientSend(UDP_PACKET_SIZE, servername, SERVER_PORT, socket, sendQueue);
      receive = new FileCopyClientReceive(UDP_PACKET_SIZE, servername, SERVER_PORT, socket, revieceQueue);
    } catch (UnknownHostException e) {
      syslog(facility, 1, "UnknownHost");
    }

    receiveThread = new Thread(receive);
    fileSendThread = new Thread(fileSend);
    window = Collections.synchronizedList(new ArrayList<>(windowSize)); // https://docs.oracle.com/javase/6/docs/api/java/util/Collections.html#synchronizedList(java.util.List)
    ackWindow = Collections.synchronizedList(new ArrayList<>(windowSize-1));
    windowSemaphore = new Semaphore(1);
    seqPointer = 1;
    currentSeq = 1;
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

  private boolean threadsAlive() {
    return receiveThread.isAlive() &&
            fileSendThread.isAlive() &&
            !receiveThread.isInterrupted() &&
            !fileSendThread.isInterrupted();
  }

  private FCpacket lacePackage() {
    byte[] fileInputArray = new byte[0];
    try {
      int availableBytes = fileInputStream.available();

      if (availableBytes > UDP_PACKET_SIZE - 8) {
        fileInputArray = fileInputStream.readNBytes(UDP_PACKET_SIZE - 8);
      } else {
        fileInputArray = fileInputStream.readAllBytes();
      }
    } catch (IOException e) {
      syslog(facility, 1, "ERROR while reading from fileInputStream");
    }
    return new FCpacket(currentSeq++,fileInputArray, fileInputArray.length);
  }

  private void sendPackage(FCpacket packet) {
    sendQueue.add(packet.getSeqNumBytesAndData());
  }

  private void markAsAcked(long seqNum) {
    try {
      windowSemaphore.acquire();
      if (seqPointer + 1 == seqNum) {
        window.addFirst(null);
      } else {
        ackWindow.add(convertSeqNumToIndex(seqNum)-1, true);
      }
      windowSemaphore.release();
    } catch (InterruptedException e) {
      syslog(facility,2, "ERROR: Semaphore interrupted");
    }
  }

  private int convertSeqNumToIndex(long seqNum) {
    if (seqNum < seqPointer) {
      syslog(facility,1,"ERROR: SeqNum to low");
      return -1;
    }
    return Ints.checkedCast(seqNum - seqPointer);
  }

  private FCpacket getPacket(long seqNum) {
    // TODO sanity checks
    // TODO but for what? i forgor...
    try {
      windowSemaphore.acquire();
      FCpacket packet = window.get(convertSeqNumToIndex(seqNum));
      windowSemaphore.release();
      return packet;
    } catch (InterruptedException e) {
      syslog(facility,2, "ERROR: Semaphore interrupted");
      return null;
    }
  }

  private boolean sanityCheckWindow() {
    boolean sanityFlag = false;

    try {
      windowSemaphore.acquire();
      for (int i = 0; i < windowSize - 1; i++) {
        if (sanityFlag && window.get(i) != null) {
          syslog(facility, 1, "Sanitycheck failed!!!");
          throw new RuntimeException("Sanitycheck failed!!!");
          //return false;
        } else if (window.get(i) == null) {
          sanityFlag = true;
        }
        windowSemaphore.release();
      }

      return true;
    } catch (InterruptedException e) {
      syslog(facility,2, "ERROR: Semaphore interrupted");
      return false;
    }
  }

  private void fillWindow() { // Fill up window / Setup
    sanityCheckWindow();

    try {
      windowSemaphore.acquire();
      for (int i = 0; i < windowSize - 1; i++) {
        if (window.get(i) == null) {
          FCpacket newPacket = lacePackage();
          window.add(i, newPacket);
          startTimer(newPacket);
          sendPackage(newPacket);
        }
      }
      windowSemaphore.release();
    } catch (InterruptedException e) {
      syslog(facility,2, "ERROR: Semaphore interrupted");
    }
  }

  private void moveUpWindow() {
    try {
      windowSemaphore.acquire();
      // Do these steps until the first element is not null or all elements are null
      while (window.getFirst() == null && !window.isEmpty()) { // TODO isEmpty() --> is null element considered empty?
        // for all indexes in window but the last copy the next index's value to here
        for (int i = 0; i < windowSize - 1; i++) {
          if (i != windowSize - 1) window.add(i, window.get(i + 1));
        }
        window.addLast(null); // fill the last index with null, as it has to be empty
        seqPointer++; // one iteration over the list has been done --> move pointer +1

        // if the first bool is true, the previous second FCpacket was also acked, therefore kill it!
        // if ackWindow.getFirst() is false, the information is not needed,
        // as the corresponding (first in window) packet not being null contains the same information
        if (ackWindow.getFirst()) {
          window.addFirst(null);
        }

        // for all indexes in ackWindow but the last copy the next index's value to here
        for (int i = 0; i < windowSize - 2; i++) {
          if (i != windowSize - 2) ackWindow.add(i, ackWindow.get(i + 1));
        }
        ackWindow.addLast(false); // fill the last index with null, as it has to be empty
      }
      windowSemaphore.release();
    } catch (InterruptedException e) {
      syslog(facility,2, "ERROR: Semaphore interrupted");
    }
  }

  public void runFileCopyClient() throws Exception {
    receiveThread.start();
    fileSendThread.start();

    FCpacket controlPacket = makeControlPacket();
    sendPackage(controlPacket);

    for (int i = 0; i < windowSize - 1; i++) {
      window.add(i,null);
    }
    for (int i = 0; i < windowSize - 2; i++) {
      ackWindow.add(i, false);
    }

    fillWindow();

    while (threadsAlive()) {
      if (!revieceQueue.isEmpty()) {
        FCpacket recivedPacket = revieceQueue.take();

        if (recivedPacket.isValidACK()) {
          markAsAcked(recivedPacket.getSeqNum()); // may result in the first window index == null
          if (window.getFirst() == null) {
            moveUpWindow();
            fillWindow();
            sanityCheckWindow(); // just to be sure
          }
        }
      }

      if (fileInputStream.available() == 0 && window.isEmpty()) {
        receiveThread.interrupt();
        fileSendThread.interrupt();
        socket.close();
      }
    }

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
    syslog(facility,8,"Cancel Timer for packet" + packet.getSeqNum());
    if (packet.getTimer() != null) {
      packet.getTimer().interrupt();
    }
  }

  /**
   * Implementation specific task performed at timeout
   * meaning selective repeat
   */
  public void timeoutTask(long seqNum) {
    FCpacket packetToRestart = getPacket(seqNum);

    cancelTimer(packetToRestart);
    sendPackage(packetToRestart);
    startTimer(packetToRestart);
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
    syslog(facility, 8, "Making Controllpackage with string of: " + sendString);
    try {
      sendData = sendString.getBytes("UTF-8");
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    return new FCpacket(0, sendData, sendData.length);
  }

  public void testOut(String out) {
    if (TEST_OUTPUT_MODE) {
      syslog(facility,8,String.format("Thread %s: %s", Thread.currentThread().getName(), out));
    }
  }

  public static void main(String argv[]) throws Exception {
    FileCopyClient myClient = new FileCopyClient(argv[0], argv[1], argv[2],
        argv[3], argv[4]);
    myClient.runFileCopyClient();
  }

}
