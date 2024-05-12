package server;

import config.Config;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static syslog.Syslog.syslog;

public class Server {

  private Config config; // TODO change path

  private ServerSocket serverSocket;

  private ExecutorService pool;

  public Server () throws IOException {
    this.config = new Config().readConfigFromFile("src/main/resources/config.json");
    if (this.config == null) {
      syslog(1,4,"ERROR: Could not read config, revert to fallback filepath");
      // TODO
    }
    this.serverSocket = new ServerSocket(config.getPort());
    pool = Executors.newFixedThreadPool(config.getMaxClients()); // Hält nicht weitere Connection davon ab connectet zu werden.
  }

  public void startServer() {
    try {
      while (true) {
        pool.execute(new ClientHandler(serverSocket.accept(), config));
      }
    } catch (IOException e) {
      syslog(1,4,"Connection zum Client konnte nicht hergestellt werden");
    }
  }

  public static void main(String[] args) throws IOException {
    syslog(1,8,"START");
    Server server = new Server();
    server.startServer();
  }

}
