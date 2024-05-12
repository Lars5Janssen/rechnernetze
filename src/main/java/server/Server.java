package server;

import config.Config;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

import static syslog.Syslog.syslog;

public class Server {
  private String facility = "SERVER";

  private Config config = new Config().loadConfig();
  private ServerSocket serverSocket;
  private ExecutorService pool;
  private ArrayList<Thread> clientHandlers;
  private Semaphore timeoutSemaphore = new Semaphore(1);
  private Semaphore shutdownSemaphore = new Semaphore(1);
  private Boolean shutdownFlag = false;


  public Server () throws IOException {
    this.serverSocket = new ServerSocket(config.getPort());
    this.clientHandlers = new ArrayList<>();
  }

  private void startServer() {
    ServerThread serverThread = new ServerThread();
    Thread thread = new Thread(serverThread);
    thread.start();


    long lastMsgTime = System.currentTimeMillis();
    long shutdownMsgTime = System.currentTimeMillis();

    while (!serverSocket.isClosed()) {
        long sysTime = System.currentTimeMillis();
        if (timeoutSemaphore.availablePermits() == 0) {
            timeoutSemaphore.release();
            lastMsgTime = sysTime;
        }

        if (shutdownSemaphore.availablePermits() == 0) {
            shutdownSemaphore.release();
            shutdownFlag = true;
            lastMsgTime = sysTime;
        }

        if (shutdownFlag) {
            if (!(sysTime - lastMsgTime <= config.getTimeToShutdown()*1000)) {
                thread.interrupt();
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    syslog(facility,1,"Cannot close socket");
                }
                try {
                    Thread.sleep(Duration.ofSeconds(5));
                } catch (InterruptedException e) {
                    syslog(facility,1,"Interrupted during Timeout. Exiting with STATUS 1");
                    System.exit(1);
                }
                return;
            } else if (sysTime - shutdownMsgTime > 1000) {
                syslog(facility,7,
                        String.format("%s second until server shutsdown", (config.getTimeToShutdown() - (sysTime-lastMsgTime)/1000)));
                shutdownMsgTime = sysTime;
            }
        }
    }
  }

  public static void main(String[] args) throws IOException {
    syslog("SERVER",8,"START");
    Server server = new Server();
    server.startServer();
    syslog("SERVER",8,"SERVER IS SHUTTING DOWN");
    System.exit(0);
  }

    class ServerThread implements Runnable {

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Socket acceptedSocket = serverSocket.accept();
                    String shutdownMessage = "";
                    if (clientHandlers.size() >= 3 || shutdownFlag) {
                        if (shutdownFlag) {
                            syslog(facility,4,"Client tried to connect. Shutdown initiated. Connection refused");
                            shutdownMessage = "Shutdown initiated. Connection refused";
                        }
                        else {
                            syslog(facility,4,"Client tried to connect. Max connections reached. Connection refused");
                            shutdownMessage = "Exceeded max number of allowed clients. closing connection.";
                        }
                        new Thread(new ClientHandler(acceptedSocket,"-1",timeoutSemaphore,shutdownSemaphore, shutdownMessage)).start();
                    } else {
                        clientHandlers.addLast(new Thread(new ClientHandler(acceptedSocket, String.valueOf(clientHandlers.size()), timeoutSemaphore,shutdownSemaphore,shutdownMessage)));
                        clientHandlers.getLast().start();
                    }
                } catch (IOException e) {
                    syslog(facility,4,"Could not create client handler");
                }
            }
            if (Thread.currentThread().isInterrupted()) {
                for (Thread clienthandler : clientHandlers) {
                    clienthandler.interrupt();
                }
            }
        }
    }
}
