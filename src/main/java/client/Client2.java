package client;

import config.Config;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

import static syslog.Syslog.syslog;

public class Client2 {


    private Socket clientSocket;
    private BufferedReader bufferedReader;
    private BufferedWriter bufferedWriter;
    private Config config = new Config().readConfigFromFile("src/main/resources/main/config.json");
    private boolean flag = false;

    public Client2() {
        buildClientSocket();
        try {
            assert config != null;
            this.bufferedReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream())); // Character Stream, inside we have byte stream
            this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

        } catch (IOException e) {
            closeEverything(clientSocket, bufferedReader, bufferedWriter);
            System.out.println("Error creating client: " + e.getMessage());
        }
    }

    //Blocking operation (because of the while loop), so we need to run it in a separate thread.
    public void sendMessage() {
        try {
            Scanner scanner = new Scanner(System.in);

            while (clientSocket.isConnected() && !clientSocket.isClosed() && !flag) {
                String messageToSend = scanner.nextLine();
                bufferedWriter.write(messageToSend + "\n");
                bufferedWriter.flush();
                if ("BYESHUTDOWN".contains(messageToSend)) {
                    flag = true;
                }
            }
        } catch (Exception e) {
            closeEverything(clientSocket, bufferedReader, bufferedWriter);
            System.out.println("Error Server not reachable. Closing.");
        }
    }

    // Blocking operation (because of the while loop), so we need to run it in a separate thread.
    public void listenForMessages() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String msgReceived;

                while (clientSocket.isConnected() && !clientSocket.isClosed()) {
                    try {
                        msgReceived = bufferedReader.readLine(); // TODO Readline keep in mind
                        if (msgReceived == null || msgReceived.equals(config.getDisconnectMSG())) {
                            clientSocket.close();
                            Thread.currentThread().interrupt();
                            return;
                        }
                        System.out.println(msgReceived);
                    } catch (IOException e) {
                        closeEverything(clientSocket, bufferedReader, bufferedWriter);
                        System.out.println("Error listening messages: " + e.getMessage());
                    }
                }
            }
        }).start();
    }

    public void runClient() {
        listenForMessages();
        sendMessage();
    }

    private void closeEverything(Socket socket, BufferedReader bufferedReader, BufferedWriter bufferedWriter) {
        try {
            if (socket != null) {
                socket.close();
            }
            if (bufferedReader != null) {
                bufferedReader.close();
            }
            if (bufferedWriter != null) {
                bufferedWriter.close();
            }
        } catch (IOException e) {
            System.out.println("Error closing client: " + e.getMessage());
        }
    }

    private void buildClientSocket() {
        try {
            this.clientSocket = new Socket("localhost", 4242);
        } catch (IOException e) {
            syslog(1,8,"Error could not build clientSocket." + e.getMessage());
        }
    }

    public static void main(String[] args) throws IOException {
        Client2 client = new Client2();
        client.runClient();
    }

}
