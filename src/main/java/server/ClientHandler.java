package server;

import static syslog.Syslog.syslog;

import config.Config;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class ClientHandler implements Runnable {
  private final String facility;
  private final Config config = new Config().loadConfig();
  private final Socket socket;
  private BlockingQueue<String> inputQueue;
  private Thread helperThread;
  String userInput;
  private DataOutputStream dataOut;
  private final String[] commands = new String[config.getCommands().size()];
  private final String shutdownMessage;
  private final Semaphore timeoutSemaphore;
  private final Semaphore shutdownSemaphore;
  private final List<Long> threadList;

  ClientHandler(
      Socket socket,
      String facility,
      Semaphore timeoutSemaphore,
      Semaphore shutdownSemaphore,
      List<Long> threadList,
      String shutdownMessage) {
    this.shutdownMessage = shutdownMessage;
    this.facility = "CH" + facility;
    this.socket = socket;
    this.threadList = threadList;
    try {
      this.dataOut = new DataOutputStream(socket.getOutputStream());
      if (shutdownMessage.isEmpty()) {
        syslog(this.facility, 8, "Accepted new Client");
      }
    } catch (IOException e) {
      syslog(this.facility, 1, "Could not establish output stream");
      closeSocket();
    }

    this.timeoutSemaphore = timeoutSemaphore;
    this.shutdownSemaphore = shutdownSemaphore;

    if (shutdownMessage.isEmpty()) {
      this.inputQueue =
          new LinkedBlockingDeque<>(config.getPackageLength()); // config.getMessageQueueLength();
      ClientHandlerStreamConsumer clientHandlerStreamConsumer =
          new ClientHandlerStreamConsumer(this.socket, facility, this.inputQueue);
      helperThread = new Thread(clientHandlerStreamConsumer);
      helperThread.start();
    }
    for (int i = 0;
        i < config.getCommands().size();
        i++) { // Initialize list of understood commands
      commands[i] = config.getCommands().get(i).split(" ")[0];
    }
  }

  @Override
  public void run() {
    threadList.add(Thread.currentThread().threadId());

    if (!shutdownMessage.isEmpty()) {
      messageToClient(shutdownMessage);
      handleBye();
    }

    while (!socket.isClosed()
        && helperThread.isAlive()
        && !helperThread.isInterrupted()
        && !Thread.currentThread().isInterrupted()) {

      try {
        userInput = inputQueue.poll(1000, TimeUnit.MILLISECONDS); // !! THE MAGIC
        if (userInput == null) {
          continue;
        }
        syslog(facility,8,String.format("Getting %s",userInput.replace("\r","\\r")));
        try {
          timeoutSemaphore.acquire();
        } catch (InterruptedException e) {
          syslog(facility, 2, "timeoutSemaphore acquire was interrupted");
        }
      } catch (InterruptedException e) {
        syslog(facility, 1, "Was interrupted when taking out of input queue");
        closeSocket();
      }
      StringBuilder responseMessage = new StringBuilder();

      if (validateCommand(userInput)) {
        String response = handleMessage(userInput);
        if (response == null) {
          responseMessage.append("ERROR while handling message\n");
          continue;
        } if (response.equals("\r")) {
          messageToClient(responseMessage.toString());
        } else {
        responseMessage.append("OK ").append(response);
        }

      } else {
        messageToClient("ERROR UNKNOWN COMMAND");
      }
    }
    closeSocket();
  }

  private boolean validateCommand(String message) {
    for (String command : commands) {
      if (message.indexOf(command)
          == 0) { // message.strip().inde[...] to remove trailing and leading white spaces
        if (command.equals("BYE")) return true;
        if (command.contains("\r")) return true; // Byepass for \r for Stream consumer Messages

        if (message.indexOf(" ") == command.length()) return true;
      }
    }
    return false;
  }

  // TODO Hier muss aufjedenfall dafür gesorgt werden das die Nachricht bei dem
  // TODO \n getrennt wird und in die Queue kommt. alternativ geht das natürlich auch direkt im
  // ClientHandlerStreamComsumer
  // TODO bei \r soll die nachricht nicht akzeptiert werden und ein Error zurückkommen.
  private String handleMessage(String message) {
    syslog(facility, 8, "Handling message: " + message);

    String[] messageSplit = message.split(" ", 2);
    String command = messageSplit[0];
    syslog(facility,8,command);
    return switch (command) {
      case "LOWERCASE" -> messageSplit[1].toLowerCase();
      case "UPPERCASE" -> messageSplit[1].toUpperCase();
      case "REVERSE" -> new StringBuilder(messageSplit[1]).reverse().toString();
      case "BYE" -> {
        handleBye();
        yield command;
      }
      case "SHUTDOWN" -> {
        if (messageSplit[1].replaceAll("\n", "").equals(config.getPassword())) {
          messageToClient("Initializing shutdown");
          try {
            shutdownSemaphore.acquire();
          } catch (InterruptedException e) {
            syslog(facility, 2, "shutdownSemaphore acquire was interrupted");
          }
          handleBye();
          yield command;
        } else {
          yield "Wrong Password";
        }
      }
      case "\r " -> messageSplit[1];
      case "\r" -> messageSplit[0];
      default -> null;
    };
  }

  private void handleBye() {
    closeSocket();
    Thread.currentThread().interrupt();
  }

  private void closeSocket() {
    try {
      if (helperThread != null) helperThread.interrupt();
      dataOut.close();
      socket.close();
      syslog(facility, 1, String.valueOf(Thread.currentThread().threadId()));
      threadList.remove(Thread.currentThread().threadId());
      Thread.currentThread().interrupt();
    } catch (IOException e) {
      syslog(facility, 1, "Could not close socket");
    }
  }

  public void messageToClient(String message) {
    syslog(facility, 8, "Sending message to client: " + message + "\n");
    try {
      dataOut.writeBytes(message + "\n"); // Fehler bei formatierung
    } catch (IOException e) {
      syslog(facility, 1, "Could not message client");
    }
  }
}
