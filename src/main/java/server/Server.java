package server;

import config.Config;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static syslog.Syslog.syslog;

public class Server {
  private String facility = "SERVER";

  private Config config = new Config().readConfigFromFile("src/main/resources/main/config.json"); // TODO change path

  private ServerSocket serverSocket;

  private ExecutorService pool;
  private ArrayList<Thread> clientHandlers;


  public Server () throws IOException {
    this.serverSocket = new ServerSocket(config.getPort());
    this.clientHandlers = new ArrayList<>();
  }

  private void startServer() {
    while (true) {
        try {
            Socket acceptedSocket = serverSocket.accept();
            if (clientHandlers.size() >= 3) {
              syslog(facility,4,"Client tried to connect. Max connections reached. Connection refused");
              new Thread(new ClientHandler(acceptedSocket,"-1", true)).start();
            } else {
                clientHandlers.addLast(new Thread(new ClientHandler(acceptedSocket, String.valueOf(clientHandlers.size()), false)));
                clientHandlers.getLast().start();
            }
        } catch (IOException e) {
          syslog(facility,4,"Could not create client handler");
        }
    }
  }

  public static void main(String[] args) throws IOException {
    syslog("SERVER",8,"START");
    Server server = new Server();
    server.startServer();
  }

}
