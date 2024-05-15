package client;

import static syslog.Syslog.syslog;

import config.Config;
import java.io.*;
import java.net.*;
import java.util.*;

public class Client {

  private Socket clientSocket;
  private final Config config = new Config().loadConfig();
  private final BufferedReader bufferedReader;
  private final BufferedWriter bufferedWriter;

  public Client() {
    try {
      buildClientSocket();
      this.bufferedReader =
          new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
      this.bufferedWriter =
          new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
      syslog(1, 8, "Client are created.");
    } catch (Exception e) {
      syslog(1, 1, "Could not establish input or output stream");
      throw new RuntimeException(e);
    }
  }

  private void sendMessage() {
    try (Scanner scanner = new Scanner(System.in)) {
      while (clientSocket.isConnected() && !clientSocket.isClosed()) {
        String messageToSend = scanner.nextLine();
        bufferedWriter.write(messageToSend);
        bufferedWriter.flush();
      }
    } catch (Exception e) {
      closeClient();
      syslog(1, 8, "Error Server not reachable. Close connection.");
    }
  }

  private void listenForMessages() {
    new Thread(
            new Runnable() {
              @Override
              public void run() {
                String msgReceived;

                while (clientSocket.isConnected() && !clientSocket.isClosed()) {
                  try {
                    msgReceived = bufferedReader.readLine();
                    if (msgReceived.equals(config.getDisconnectMSG())) {
                      clientSocket.close();
                      Thread.currentThread().interrupt();
                      return;
                    }
                    System.out.println(msgReceived);
                  } catch (IOException e) {
                    closeClient();
                    syslog(1, 1, "Error listening for messages: " + e.getMessage());
                  }
                }
              }
            })
        .start();
  }

  private void runClient() {
    sendMessage();
    listenForMessages();
  }

  /** This Helper Method close the client socket and DataIn and DataOutputstream. */
  private void closeClient() {
    try {
      if (this.clientSocket != null) {
        clientSocket.close();
      }
      if (this.bufferedReader != null) {
        this.bufferedReader.close();
      }
      if (this.bufferedWriter != null) {
        this.bufferedWriter.close();
      }
    } catch (IOException e) {
      syslog(1, 8, "Error closing client: " + e.getMessage());
    }
  }

  private void buildClientSocket() {
    try {
      this.clientSocket = new Socket("localhost", 4242);
    } catch (IOException e) {
      syslog(1, 8, "Error could not build clientSocket." + e.getMessage());
    }
  }

  public static void main(String[] args) {
    Client client = new Client();
    client.runClient();
  }
}
