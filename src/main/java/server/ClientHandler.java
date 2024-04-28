package server;

import config.Config;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static syslog.Syslog.syslog;

public class ClientHandler implements Runnable {

    private Config config = new Config().readConfigFromFile("src/main/resources/config.json");
    private final Socket socket;

    private DataInputStream dataIn;

    private DataOutputStream dataOut;
    ClientHandler(Socket socket) { this.socket = socket; }

    @Override
    public void run() {
        try {
            // TODO in while loop
            syslog(1,8,"Accepted new Client");
             // new BufferedInputStream(socket.getInputStream())
            dataIn = new DataInputStream(socket.getInputStream());
            dataOut = new DataOutputStream(socket.getOutputStream());
            welcomeClient(dataOut);

            while (!socket.isClosed()) {

                byte[] arr = new byte[255];
                String command = String.valueOf(dataIn.read(arr,0, arr.length));// encoded in modified UTF-8 format

                if (dataIn.available() > 0) { // Check if the stream have anything inside anymore
                    syslog(1,4,"Message over 255 bytes received");
                    messageToClient("Nachricht ist laenger als 255 Zeichen!\n");
                    dataIn.skip(dataIn.available());

                } else {
                    dataOut.writeUTF("Der Server erwaret eine eingabe:\n");

                    validateCommand(arr);
                    String response = handleCommand(command);

                    dataOut.writeUTF(response);
                    // TODO needs to be in validation Method
                }
              /*  syslog(1,8, Arrays.toString(arr));*/
            }

            socket.close();
        } catch (IOException e) {
            syslog(1,4,"Could not establish connection to new client");
        }
    }

    private String handleCommand(String command) { // TODO
        syslog(1,8, "Handling command: " + command + "\n");
        return command;
    }

    private String validateCommand(byte[] command) {
        return null;
    }

    private void welcomeClient(DataOutputStream dataOut) throws IOException {
        dataOut.writeUTF(config.getWelcomeMSG());
        for (String command : config.getCommands()) {
            dataOut.writeUTF(command);
        }
    }

    private void messageToClient(String message) throws IOException {
        dataOut.writeUTF(message);
    }
}
