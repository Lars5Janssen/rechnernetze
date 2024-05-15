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
  private StringBuilder userInputBuild;
  private boolean errorFlag = false;

  public ClientHandlerStreamConsumer(
      Socket socket, String facility, BlockingQueue<String> inputQueue) {
    this.socket = socket;
    this.facility = "IH" + facility;
    try {
      this.dataIn = new DataInputStream(socket.getInputStream());
    } catch (IOException e) {
      syslog(this.facility, 1, "Could not establish intput stream");
    }
    this.inputQueue = inputQueue;
  }

  @Override
  public void run() {
    while (!socket.isClosed() && !errorFlag && !Thread.currentThread().isInterrupted()) {
      getUserInput();
      /**String userInput = getUserInput(); // The point of view
      if (userInput == null) {
        if (socket.isClosed()) {
          syslog(facility, 1, "Error while reading input");
        }
        return;
      }
      if (!inputQueue.add(userInput)) {
        syslog(
            facility,
            1,
            String.format(
                "Error in adding to queue. Might have more then %s Messages",
                config.getMessageQueueLength()));
      }**/
    }
    try {
      dataIn.close();
      socket.close();
    } catch (IOException e) {
      syslog(facility, 1, "Could not close dataIn or Socket");
    }
  }

  private boolean checkMaxPackageLength(int messageLength) {
    return messageLength <= config.getPackageLength();
  }

  private String convertToUTF8(byte[] arr) {
    return StringUtils.toEncodedString(
        arr, StandardCharsets.UTF_8); // apache commons-lang 3:3.6 library
  }

  private void discardStream(String message) {
    try {
      dataIn.skip(Long.MAX_VALUE);
    } catch (IOException e) {
      syslog(facility,1,"Could not skip message");
    } finally {
      inputQueue.add("\r" + message); // TODO change clientHandler to use \r
    }
  }

  private void getUserInput() {
    userInputBuild = new StringBuilder();
    byte[] streamBuffer;
    int messageLength = 0;

    // Break when one command found
    while (!socket.isClosed() && !errorFlag && !Thread.currentThread().isInterrupted()) {
      // Get set length of bytes from input stream (length set in config)
      // and transfer them to stringBuilder, while cutting null bytes added by dataIn.read
      streamBuffer = new byte[config.getPackageLength()+1];

      try {
        messageLength = dataIn.read(streamBuffer, 0, streamBuffer.length);
      } catch (IOException e) {
        if (!Thread.currentThread().isInterrupted()) {
          syslog(facility, 1, "Could not read from dataIn");
        }
        errorFlag = true;
      }

      if (messageLength>config.getPackageLength()) {
          discardStream("ERROR Your message is over the size limit");
      }

      userInputBuild.append(convertToUTF8(Arrays.copyOfRange(streamBuffer, 0, messageLength)));

      if (userInputBuild.length() > config.getPackageLength() || userInputBuild.indexOf("\r") != -1)  {
        discardStream("ERROR Your message either got to long over several packages or has illegal character \\r");

      } else if (userInputBuild.indexOf("\n") != -1) {
          while (userInputBuild.indexOf("\n") != -1) {
            int nLIndex = userInputBuild.indexOf("\n");
            String substring = userInputBuild.substring(0,nLIndex);

            if(!inputQueue.add(substring)) {
              syslog(facility,1,"COULD NOT ADD TO QUEUE");
            }
            syslog(facility,8,"Shared Array: " + Arrays.toString(inputQueue.toArray()));
            userInputBuild.delete(0,nLIndex+1);
          }
          if (!userInputBuild.isEmpty()) {
            inputQueue.add("\r" + "ERROR Your message contained an protocol non-conforming part");


          }
          inputQueue.add("\r"); // \r is signal flag to ClientHandler that one package has been processed. \r can not be input from user, per protocol
      }








      /**if (checkMaxPackageLength(messageLength)) {
        try {
          streamBuffer =
              Arrays.copyOfRange(
                  streamBuffer, 0, messageLength); // cut array to the actual message length
        } catch (IllegalArgumentException e) {
          if (socket.isClosed()) {
            syslog(facility, 1, "messageLength not set, could not read from dataIn");
          }
          errorFlag = true;
          return null;
        }
      }
      ;

      // Check if there are more bytes
      // if there are, there were more than max packageLength of bytes.\
      // and everything needs to be discarded.
      // TODO vllt in eine Methode und direkt unter zeile 99?
      // TODO (Or a new package has arrived right between dataIn.read (above) and now.)
      try {
        if (dataIn.available() > 0) {
          syslog(facility, 4, "More data is available than expected");
          // TODO handle case / reset everything (handleMessageOverSizeLimit() )
          break;
        }
      } catch (IOException e) {
        if (!Thread.currentThread().isInterrupted()) {
          syslog(facility, 1, "Could not get remaining length from dataIn");
        }
      }

      System.out.println("Stream buffer in UTF: " + convertToUTF8(streamBuffer));
      // Append the byteArray of correct length to the stringBuilder
      userInputBuild.append(convertToUTF8(streamBuffer));

      // Check if the stringBuilder has exceeded the maximum package size,
      // if so, the command is invalid and everything needs to be discarded.
      // TODO Die package length und ob alle verbindungen geschlossen werden müssen wird doch schon
      // TODO mit checkMessageLength überprüft. Wieso hier nochmal?
      if (userInputBuild.toString().getBytes(StandardCharsets.UTF_8).length
          > config.getPackageLength()) { // TODO refactor 255 magic number
        syslog(facility, 4, "User input has exceeded maximum length");
        // TODO handle case / reset everything (handleMessageOverSizeLimit() )
        break;
      }

      int newLineIndex = userInputBuild.indexOf("\n");
      int lastNewLineIndex = userInputBuild.lastIndexOf("\n");
      int stringLengthFromZero = userInputBuild.length() - 1;
      // TODO wenn ein \r enthalten ist dann soll ein Error zurückgegeben werden.

      if (!Thread.currentThread().isInterrupted()) {syslog(
          facility,
          8,
          String.format(
              "\n"
                  + "First NL: %s\n"
                  + "Last NL: %s\n"
                  + "Length (from 0): %s\n"
                  + "String: \n"
                  + "========================================\n"
                  + "%s"
                  + "========================================\n",
              newLineIndex, lastNewLineIndex, stringLengthFromZero, userInputBuild));}

      // Exit condition and newline conformity checks
      if (newLineIndex != -1) { // We have at least one newline

        if (newLineIndex != lastNewLineIndex) { // We have more than one newline
          syslog(facility, 4, "More than one newline found");
          // TODO handle case
          // Aufsplitten des Strings in vor \n und nach \n
            // jksadbfkjbdsaj\niudsafiraesf = 2 blöcke

        } else if (lastNewLineIndex != stringLengthFromZero) { // The one newline is not at the end
          syslog(facility, 4, "Newline is not at the end of the input");
          // TODO handle case
          // Kein newline bedeutet glaube ich keine weiterverarbeitung. ERROR?
        } else { // everything is fine. the string is ok. Exit while loop
          syslog(facility, 8, "The user input might be valid. Proceeding to validation.");
          break;
        }
      }**/
    }

    //return userInputBuild.toString();
  }
}
