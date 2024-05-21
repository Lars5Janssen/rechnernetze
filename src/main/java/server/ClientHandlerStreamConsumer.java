package server;

import static syslog.Syslog.syslog;

import config.Config;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import org.apache.commons.lang3.StringUtils;

public class ClientHandlerStreamConsumer implements Runnable {
  private final String facility;
  private final Config config = new Config().loadConfig();
  private final Socket socket;
  private DataInputStream dataIn;
  private final BlockingQueue<String> inputQueue;
  private boolean errorFlag = false;

  public ClientHandlerStreamConsumer(
      Socket socket, String facility, BlockingQueue<String> inputQueue) {
    this.socket = socket;
    this.facility = "IH" + facility;
    try {
      this.dataIn = new DataInputStream(socket.getInputStream());
    } catch (IOException e) {
      syslog(this.facility, 1, "Could not establish input stream");
    }
    this.inputQueue = inputQueue;
  }

  @Override
  public void run() {
    while (!socket.isClosed() && !errorFlag && !Thread.currentThread().isInterrupted()) {
      getUserInput();
    }
    try {
      dataIn.close();
      socket.close();
    } catch (IOException e) {
      syslog(facility, 1, "Could not close dataIn or Socket");
    }
  }

  private String convertToUTF8(byte[] arr) {
    String retString =
        StringUtils.toEncodedString(
            arr, StandardCharsets.UTF_8); // apache commons-lang 3:3.6 library
    return retString;
  }

  private boolean appendToStream(String message) {
    syslog(facility, 8, String.format("Adding %s", message.replace("\r", "\\r")));
    return inputQueue.add(message);
  }

  private void discardStream(String message) {
    try {
      syslog(facility, 6, "Input to long, skipping all");
      byte[] clearArray = new byte[dataIn.available()];
      dataIn.read(clearArray);
    } catch (IOException e) {
      syslog(facility, 1, "Could not skip message");
    } finally {
      appendToStream("\r " + message); // TODO change clientHandler to use \r
    }
  }

  private void getUserInput() {
    StringBuilder userInputBuild = new StringBuilder();
    byte[] streamBuffer;
    int messageLength = 0;

    // Break when one command found
    while (!socket.isClosed() && !errorFlag && !Thread.currentThread().isInterrupted()) {
      // Get set length of bytes from input stream (length set in config)
      // and transfer them to stringBuilder, while cutting null bytes added by dataIn.read
      streamBuffer = new byte[config.getPackageLength() + 1];

      try {
        messageLength = dataIn.read(streamBuffer, 0, streamBuffer.length);
      } catch (IOException e) {
        if (!Thread.currentThread().isInterrupted()) {
          syslog(facility, 1, "Could not read from dataIn");
        }
        errorFlag = true;
      }

      if (messageLength > config.getPackageLength()) {
        discardStream("ERROR Your message is over the size limit");
        appendToStream("\r");
        continue;
      }
      userInputBuild.append(convertToUTF8(Arrays.copyOfRange(streamBuffer, 0, messageLength)));

      if (userInputBuild.length() > config.getPackageLength()) {
        discardStream("ERROR Your message either got to long over several packages");

      } else if (userInputBuild.indexOf("\n") != -1) {
        while (userInputBuild.indexOf("\n") != -1) {
          int nLIndex = userInputBuild.indexOf("\n");
          String substring = userInputBuild.substring(0, nLIndex);
          if (substring.contains("\r")) {
            appendToStream("\r ERROR Your message contained \\r");
          } else {
            if (!appendToStream(substring)) syslog(facility, 1, "COULD NOT ADD TO QUEUE");
          }
          userInputBuild.delete(0, nLIndex + 1);
        }
        if (!userInputBuild.isEmpty()) {
          appendToStream("\r " + "ERROR Your message contained an protocol non-conforming part");
        }
      }
      appendToStream(
          "\r"); // \r is signal flag to ClientHandler that one package has been processed. \r can
      // not be input from user, per protocol
    }
  }
}
