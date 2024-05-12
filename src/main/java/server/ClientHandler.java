package server;

import static syslog.Syslog.syslog;

import config.Config;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import org.apache.commons.lang3.StringUtils;

public class ClientHandler implements Runnable {
  private String facility;

  private Config config = new Config().readConfigFromFile("src/main/resources/main/config.json");
  private final Socket socket;
  private ClientHandlerStreamConsumer clientHandlerStreamConsumer;
  private BlockingQueue<String> inputQueue;
  private Thread thread;
  String userInput;
  private DataOutputStream dataOut;
  private String[] commands = new String[config.getCommands().size()];
  private Boolean instantClose;

  ClientHandler(Socket socket, String facility, Boolean instantClose) {
    this.instantClose = instantClose;
    this.facility = "CH" + facility;
    this.socket = socket;
    try {
      this.dataOut = new DataOutputStream(socket.getOutputStream());
      syslog(this.facility, 8, "Accepted new Client");
    } catch (IOException e) {
      syslog(this.facility, 1, "Could not establish output stream");
      throw new RuntimeException(e);
    }
    this.inputQueue = new LinkedBlockingDeque<>(config.getMessageQueueLength());
    clientHandlerStreamConsumer =
        new ClientHandlerStreamConsumer(this.socket, facility, this.inputQueue);
    thread = new Thread(clientHandlerStreamConsumer);
    thread.start();

    for (int i = 0;
        i < config.getCommands().size();
        i++) { // Initialize list of understood commands
      commands[i] = config.getCommands().get(i).split(" ")[0];
    }
  }

  @Override
  public void run() {

    if (instantClose) {
      messageToClient("Exeeded max number of allowed clients. closing connection.");
      handleBye();
    }

    while (!socket.isClosed() && thread.isAlive() && !Thread.currentThread().isInterrupted()) {

      // welcomeClient();
      messageToClient("Der Server erwartet eine eingabe:\n");

      try {
        userInput = inputQueue.take();
      } catch (InterruptedException e) {
        syslog(facility, 1, "Was interrupted when taking out of input queue");
        throw new RuntimeException(e);
      }

      if (validateCommand(userInput)) {
        String response = handleMessage(userInput);
        if (response == null) {
          messageToClient("ERROR while handling message");
          continue;
        }
        messageToClient("OK " + response);

      } else {
        messageToClient("ERROR UNKNOWN COMMAND");
      }
    }
    try {
      socket.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private boolean validateCommand(String message) {
    for (String command : commands) {
      if (message.indexOf(command)
          == 0) { // message.strip().inde[...] to remove trailing and leading white spaces
        if (command.equals("BYE") && message.length() == 4) return true;
        else if (command.equals("SHUTDOWN") && message.length() == 9) return true;
        else if (command.equals("BYE") || command.equals("SHUTDOWN")) return false;

        if (message.indexOf(" ") == command.length()) return true;
      }
    }
    return false;
  }

  private String handleMessage(String message) {
    syslog(facility, 8, "Handling message: " + message);
    String[] messageSplit = message.split(" ", 2);
    String command = messageSplit[0].replace("\n", "");
    return switch (command) {
      case "LOWERCASE" -> messageSplit[1].toLowerCase();
      case "UPPERCASE" -> messageSplit[1].toUpperCase();
      case "REVERSE" -> new StringBuilder(messageSplit[1]).reverse().toString();
      case "BYE" -> {
        handleBye();
        yield command;
      }
      case "SHUTDOWN" -> {
        handleShutdown();
        yield command;
      }
      default -> null;
    };
  }

  private void handleBye() {
    closeSocket();
    thread.interrupt();
    Thread.currentThread().interrupt();
  }

  private void handleShutdown() {
    syslog(facility, 8, "Invoked Shutdown");
  }

  private boolean checkMaxPackageLength(int messageLength) {
    if (messageLength > config.getPackageLength()) {
      return false;
    }
    return true;
  }

  private void closeSocket() {
    try {
      thread.interrupt();
      dataOut.close();
      socket.close();
    } catch (IOException e) {
      syslog(facility, 1, "Could not close socket");
      throw new RuntimeException(e);
    }
  }

  private Boolean completedMessage() {
    return true;
  }

  public void messageToClient(String message) {
    syslog(facility, 8, "Sending message to client: " + message + "\n");
    try {
      dataOut.writeBytes(message + "\n");
    } catch (IOException e) {
      syslog(facility, 1, "Could not message client");
    }
  }

  private void welcomeClient() {
    messageToClient(config.getWelcomeMSG());
    for (String command : config.getCommands()) {
      messageToClient(command);
    }
  }

  private String convertToUTF8(byte[] arr) {
    return StringUtils.toEncodedString(
        arr, StandardCharsets.UTF_8); // apache commons-lang 3:3.6 libary
  }
}
