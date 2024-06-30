package client;

/* FileCopyClient.java
Version 0.1 - Muss erg�nzt werden!!
Praktikum 3 Rechnernetze BAI4 HAW Hamburg
Autoren:
Janssen, Lars
Lüdeke, Leonhard
*/

import static syslog.Syslog.syslog;
import static syslog.Syslog.writeToFile;

import com.google.common.primitives.Ints;
import java.io.*;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import server.FC_Timer;
import server.FCpacket;

public class FileCopyClient extends Thread {

  // -------- Constants
  public static final boolean TEST_OUTPUT_MODE = false;
  private static final String facility = "Client";

  public final int SERVER_PORT = 23000;
  public final int UDP_PACKET_SIZE = 1008;
  public final int PACKET_SIZE_WITHOUT_SEQ = UDP_PACKET_SIZE - 8;
  private final double X = 0.25;
  private final double Y = 0.125;

  // -------- Public parms
  public String servername;
  public String sourcePath;
  public String destPath;
  public int windowSize;
  public long serverErrorRate;

  // -------- Variables
  // current default timeout in nanoseconds
  private long timeoutValue = 100000000L;
  private double expRTT = timeoutValue;
  private double jitter = 1.0;
  private long meassuredRTT = 0;
  private StringBuilder sb;
  private final DatagramSocket socket;
  private final FileInputStream fileInputStream;

  private final List<FCpacket> window;
  private final List<Boolean> ackWindow;
  private final Semaphore windowSemaphore;
  private long seqNumBeforeWindow;
  private long nextSeqNum;

  private int sentPackets = -1;
  private int resentPackets = 0;

  public BlockingQueue<FCpacket> revieceQueue = new LinkedBlockingQueue<>();
  public BlockingQueue<byte[]> sendQueue = new LinkedBlockingQueue<>();

  private FileCopyClientSend fileSend;
  private FileCopyClientReceive receive;
  private final Thread receiveThread;
  private final Thread fileSendThread;

  // Time Counter
  LocalTime startTime = null;
    LocalTime endTime = null;

  // Constructor
  public FileCopyClient(
      String serverArg,
      String sourcePathArg,
      String destPathArg,
      String windowSizeArg,
      String errorRateArg)
      throws SocketException {
    servername = serverArg;
    sourcePath = sourcePathArg;
    destPath = destPathArg;
    fileInputStream = openFileInputStream();
    windowSize = Integer.parseInt(windowSizeArg);
    serverErrorRate = Long.parseLong(errorRateArg);
    socket = new DatagramSocket();

    try {
      fileSend =
          new FileCopyClientSend(UDP_PACKET_SIZE, servername, SERVER_PORT, socket, sendQueue);
      receive =
          new FileCopyClientReceive(UDP_PACKET_SIZE, servername, SERVER_PORT, socket, revieceQueue);
    } catch (UnknownHostException e) {
      syslog(facility, 1, "UnknownHost");
    }

    receiveThread = new Thread(receive);
    fileSendThread = new Thread(fileSend);
    window =
        Collections.synchronizedList(
            new ArrayList<>(
                windowSize)); // https://docs.oracle.com/javase/6/docs/api/java/util/Collections.html#synchronizedList(java.util.List)
    ackWindow = Collections.synchronizedList(new ArrayList<>(windowSize - 1));
    windowSemaphore = new Semaphore(1);
    seqNumBeforeWindow = 0; // seqPointer
    nextSeqNum = 1; // currentSeq
  }

  public void runFileCopyClient() throws IOException, InterruptedException {
    startTime = LocalTime.now();
    syslog(facility, 8, "Client started at: " + startTime);
    receiveThread.start();
    fileSendThread.start();

    sb = new StringBuilder();

    FCpacket controlPacket = makeControlPacket();
    sendPackage(controlPacket);

    for (int i = 0; i <= windowSize - 1; i++) {
      window.add(null);
    }
    for (int i = 0; i <= windowSize - 2; i++) {
      ackWindow.add(false);
    }
    sb.append("Window size: ").append(windowSize).append("\n");
    sb.append("Server error Rate: ").append(serverErrorRate).append("\n");
    sb.append("expRTT,jitter,timeoutValue\n");
    sb.append(String.format("%s,%s,%s,%s\n", meassuredRTT , expRTT, jitter, timeoutValue));

    int perfecNumOfPackets =
        (int) Math.ceil((double) fileInputStream.available() / PACKET_SIZE_WITHOUT_SEQ);

    revieceQueue.take(); // Clear ACK of controlPacket

    windowSemaphore.acquire();
    fillWindow();
    windowSemaphore.release();

    while (threadsAlive()) {
      if (!revieceQueue.isEmpty()) {
        FCpacket recivedPacket = revieceQueue.take();
        syslog(facility, 9, "Enter received packet for: " + recivedPacket.getSeqNum());

        if (recivedPacket.isValidACK()) {
          windowSemaphore.acquire();
          markAsAcked(
              recivedPacket.getSeqNum(),
              recivedPacket.getTimestamp()); // Set the measuredRTT inside the method | may result in the

          // first window index == null
          syslog(
              facility,
              8,
              "After " + recivedPacket.getSeqNum()
                  + "\nMeasured: " + meassuredRTT
                  + "\nexpRTT: " + expRTT
                  + "\njitter: " + jitter
                  + "\nTimeout: " + timeoutValue);
          syslog(facility, 8, "RTT for " + recivedPacket.getSeqNum() + " is " + meassuredRTT);
          if (window.getFirst() == null) {
            moveUpWindow();
            fillWindow();
          }
          windowSemaphore.release();
        }
      }

      if (fileInputStream.available() == 0 && windowIsNull()) {
        syslog(facility, 8, "EXIT NOW!");
        receiveThread.interrupt();
        fileSendThread.interrupt();
        socket.close();
      }
    }
    syslog(facility, 8, X);
    syslog(facility, 8, Y);

    syslog(
        facility,
        8,
        "Sent packets W/O errors " + perfecNumOfPackets
            + "\nbut sent " + sentPackets + " Packets\n"
            + resentPackets + " where resent");

    writeToFile();

    FileWriter myWriter = new FileWriter("RTT.csv");
    myWriter.write(sb.toString());
    myWriter.close();
    endTime = LocalTime.now();
    Duration duration = Duration.between(startTime, endTime);
    syslog(facility, 8, "Client ended at: " + endTime);
    syslog(facility, 8, "Duration: " + duration);
  }

  private void markAsAcked(long seqNum, long timestamp) {
    if (!isInWindow(seqNum)) return;

    int index = convertSeqNumToIndex(seqNum);
    FCpacket windowPacket = window.get(index);

    meassuredRTT = timestamp - windowPacket.getTimestamp();
    cancelTimer(windowPacket);
    computeTimeoutValue(false);

    if (window.getFirst().getSeqNum() == seqNum) {
      window.set(0, null);
      seqNumBeforeWindow++;
    } else {
      ackWindow.set(index - 1, true);
    }
  }

  private FCpacket getPacketFromWindow(long seqNum) {
    if (!isInWindow(seqNum)) return null;
    return window.get(convertSeqNumToIndex(seqNum)); // FCPacket
  }

  private void fillWindow() { // Fill up window / Setup

    try {
      if (fileInputStream.available() == 0) {
        return;
      }
    } catch (IOException e) {
      syslog(facility, 1, "ERROR: FileInputStream exception");
    }
    syslog(facility, 8, "BEFORE fillWindow():\n" + Arrays.toString(window.toArray()));

    for (int i = 0; i <= windowSize - 1; i++) {
      if (window.get(i) == null) {
        FCpacket newPacket = lacePackage();
        window.set(i, newPacket);
        startTimer(newPacket);
        sendPackage(newPacket);
        newPacket.setTimestamp(System.nanoTime());
        // seqNumBeforeWindow++;
      }
    }
    syslog(facility, 8, "AFTER fillWindow():\n" + Arrays.toString(window.toArray()));
  }

  private boolean windowIsNull() {
    for (int i = 0; i <= windowSize - 1; i++) {
      if (window.get(i) != null) {
        return false;
      }
    }
    return true;
  }

  private void moveUpWindow() {
    // Do these steps until the first element is not null or all elements are null
    while (window.getFirst() == null && !windowIsNull()) {
      // for all indexes in window but the last copy the next index's value to here
      for (int i = 0; i <= windowSize - 1; i++) {
        if (i != windowSize - 1) window.set(i, window.get(i + 1));
      }
      window.set(windowSize - 1, null); // fill the last index with null, as it has to be empty

      // if the first bool is true, the previous second FCpacket was also acked, therefore kill it!
      // if ackWindow.getFirst() is false, the information is not needed,
      // as the corresponding (first in window) packet not being null contains the same information
      if (ackWindow.getFirst()) {
        window.set(0, null);
        seqNumBeforeWindow++;
      }

      // for all indexes in ackWindow but the last copy the next index's value to here
      for (int i = 0; i <= windowSize - 2; i++) {
        if (i != windowSize - 2) ackWindow.set(i, ackWindow.get(i + 1));
      }
      ackWindow.set(windowSize - 2, false); // fill the last index with null, as it has to be empty
    }
  }

  /** Timer Operations */
  public void startTimer(FCpacket packet) {
    /* Create, save and start timer for the given FCpacket */
    FC_Timer timer = new FC_Timer(timeoutValue, this, packet.getSeqNum());
    packet.setTimer(timer);
    timer.start();
  }

  public void cancelTimer(FCpacket packet, String facilitySTRING) {
    if (packet == null) return;
    /* Cancel timer for the given FCpacket */
    syslog(facilitySTRING, 8, "Cancel Timer for packet: " + packet.getSeqNum());
    if (packet.getTimer() != null) {
      packet.getTimer().interrupt();
    }
  }

  public void cancelTimer(FCpacket packet) {
    cancelTimer(packet, facility);
  }

  /** Implementation specific task performed at timeout meaning selective repeat */
  public void timeoutTask(long seqNum) {
    String facilityTwo = "TIMEOUT FOR " + seqNum;

    try {
      windowSemaphore.acquire();
      if (!isInWindow(seqNum)) return;
      FCpacket packetToRestart = getPacketFromWindow(seqNum);
      if (packetToRestart == null) return;

      // cancelTimer(packetToRestart, facilityTwo);
      syslog(facilityTwo, 8, "NOW SENDING PACKAGE " + seqNum);
      sendPackage(packetToRestart);
      resentPackets++;
      computeTimeoutValue(true);
      startTimer(packetToRestart);
      getPacketFromWindow(seqNum).setTimestamp(System.nanoTime());
      windowSemaphore.release();
    } catch (InterruptedException e) {
      if (isInWindow(seqNum))
        syslog(facilityTwo, 1, "timeoutTask(" + seqNum + ") was interrupted during SEMAPHORE");
    }
  }

  /** Computes the current timeout value (in nanoseconds) */
  public void computeTimeoutValue(boolean isRetransmitt) {
    if (isRetransmitt) {
      timeoutValue *= 2L;
    } else {
      expRTT = (1.0 - Y) * expRTT + Y * (double) meassuredRTT;
      jitter = (1.0 - X) * jitter + X * Math.abs((double) meassuredRTT - expRTT);
      timeoutValue = (long) (expRTT + 4.0 * jitter);
      // if (isRetransmitt) timeoutValue*=2.0;
    }
    if (timeoutValue > 500000000L) timeoutValue = 500000000L;
    sb.append(String.format("%s,%s,%s,%s\n", meassuredRTT , expRTT, jitter, timeoutValue));
  }

  /** At this point wh have prooved methods. */
  private boolean isInWindow(long seqNum) {
    return seqNum > seqNumBeforeWindow;
  }

  private int convertSeqNumToIndex(long seqNum) {
    if (!isInWindow(seqNum)) {
      throw new RuntimeException("DID NOT UPHOLD CONVENTION OF USING convertCheck() first!");
    }
    return Ints.checkedCast(seqNum - seqNumBeforeWindow - 1);
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
    return new FCpacket(nextSeqNum++, fileInputArray, fileInputArray.length);
  }

  private void sendPackage(FCpacket packet) {
    sentPackets++;
    syslog(facility, 10, "SENDPACKAGE " + packet.getSeqNum());
    sendQueue.add(packet.getSeqNumBytesAndData());
  }

  private FileInputStream openFileInputStream() {
    if (sourcePath != null) {
      File file = new File(sourcePath);
      try {
        return new FileInputStream(file);
      } catch (FileNotFoundException e) {
        syslog(facility, 1, "ERROR: File not Found");
      }
    }
    syslog(facility, 1, "ERROR: SourcePath is Null.");
    return null;
  }

  private boolean threadsAlive() {
    return receiveThread.isAlive()
        && fileSendThread.isAlive()
        && !receiveThread.isInterrupted()
        && !fileSendThread.isInterrupted();
  }

  /** Return value: FCPacket with (0 destPath;windowSize;errorRate) */
  public FCpacket makeControlPacket() {
    /* Create first packet with seq num 0. Return value: FCPacket with
    (0 destPath ; windowSize ; errorRate) */
    String sendString = destPath + ";" + windowSize + ";" + serverErrorRate;
    byte[] sendData = null;
    syslog(facility, 8, "Making Controllpackage with string of: " + sendString);
      sendData = sendString.getBytes(StandardCharsets.UTF_8);
      return new FCpacket(0, sendData, sendData.length);
  }

  public void testOut(String out) {
    if (TEST_OUTPUT_MODE) {
      syslog(facility, 8, String.format("Thread %s: %s", Thread.currentThread().getName(), out));
    }
  }

  public static void main(String[] argv) throws Exception {
    FileCopyClient myClient;
      myClient = new FileCopyClient(argv[0], argv[1], argv[2], argv[3], argv[4]);
      myClient.runFileCopyClient();
  }
}
