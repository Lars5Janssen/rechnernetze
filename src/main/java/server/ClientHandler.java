package server;

import static syslog.Syslog.syslog;

import config.Config;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;

import org.apache.commons.lang3.StringUtils;

public class ClientHandler implements Runnable {
  private String facility;

  private Config config = new Config().loadConfig();
  private final Socket socket;
  private ClientHandlerStreamConsumer clientHandlerStreamConsumer;
  private BlockingQueue<String> inputQueue;
  private Thread thread;
  String userInput;
  private DataOutputStream dataOut;
  private String[] commands = new String[config.getCommands().size()];
  private String shutdownMessage;
  private Semaphore timeoutSemaphore;
  private Semaphore shutdownSemaphore;

  ClientHandler(Socket socket, String facility, Semaphore timeoutSemaphore, Semaphore shutdownSemaphore, String shutdownMessage) {
    this.shutdownMessage = shutdownMessage;
    this.facility = "CH" + facility;
    this.socket = socket;
    try {
      this.dataOut = new DataOutputStream(socket.getOutputStream());
      if (shutdownMessage.equals("")) {
        syslog(this.facility, 8, "Accepted new Client");
      }
    } catch (IOException e) {
      syslog(this.facility, 1, "Could not establish output stream");
      closeSocket();
    }

    this.timeoutSemaphore = timeoutSemaphore;
    this.shutdownSemaphore = shutdownSemaphore;

    if (shutdownMessage.equals("")) {
      this.inputQueue = new LinkedBlockingDeque<>(config.getMessageQueueLength());
      clientHandlerStreamConsumer =
              new ClientHandlerStreamConsumer(this.socket, facility, this.inputQueue);
      thread = new Thread(clientHandlerStreamConsumer);
      thread.start();
    }
    for (int i = 0;
         i < config.getCommands().size();
         i++) { // Initialize list of understood commands
      commands[i] = config.getCommands().get(i).split(" ")[0];
    }
  }

  @Override
  public void run() {

    if (!shutdownMessage.equals("")) {
      messageToClient(shutdownMessage);
      handleBye();
    }

    while (!socket.isClosed() && thread.isAlive() && !Thread.currentThread().isInterrupted()) {

      // welcomeClient();
      messageToClient("Der Server erwartet eine eingabe:\n");

      try {
        userInput = inputQueue.take();
        try {
          timeoutSemaphore.acquire();
        } catch (InterruptedException e) {
          syslog(facility,2,"timeoutSemaphore acquire was interrupted");
        }
      } catch (InterruptedException e) {
        syslog(facility, 1, "Was interrupted when taking out of input queue");
        closeSocket();
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
    closeSocket();
  }

  private boolean validateCommand(String message) {
    for (String command : commands) {
      if (message.indexOf(command) == 0) { // message.strip().inde[...] to remove trailing and leading white spaces
        if (command.equals("BYE") && message.length() == 4) return true;
        else if (command.equals("BYE")) return false;

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
        if (messageSplit[1].replaceAll("\n","").equals(config.getPassword())) {
          messageToClient("Initializing shutdown");
          try {
              shutdownSemaphore.acquire();
          } catch (InterruptedException e) {
            syslog(facility,2,"shutdownSemaphore acquire was interrupted");
          }
          handleBye();
          yield command;
        } else {
          yield "Wrong Password";
        }
      }
      default -> null;
    };
  }

  private void handleBye() {
    closeSocket();
    Thread.currentThread().interrupt();
  }

  private void handleShutdown() {
    syslog(facility, 8, "Invoked Shutdown");
  }

  private void closeSocket() {
    try {
      if (thread != null) {
        thread.interrupt();
      }
      dataOut.close();
      socket.close();
    } catch (IOException e) {
      syslog(facility, 1, "Could not close socket");
    }
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
}