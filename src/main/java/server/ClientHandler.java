package server;

import config.Config;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
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
            syslog(1,8,"Accepted new Client");
            dataIn = new DataInputStream(socket.getInputStream());
            dataOut = new DataOutputStream(socket.getOutputStream());
            welcomeClient(dataOut);

            while (!socket.isClosed()) {
                messageToClient("Der Server erwartet eine eingabe:\n");

                byte[] userInput = new byte[255];

                int messageLength = dataIn.read(userInput,0, userInput.length);// encoded in modified UTF-8 format
                userInput = Arrays.copyOfRange(userInput, 0, messageLength);

                if (dataIn.available() > 0) { // Check if the stream have anything inside anymore
                    syslog(1,4,"Message over 255 bytes received");
                    messageToClient("Nachricht ist laenger als 255 Zeichen!\n");
                    dataIn.skip(dataIn.available());
                    continue;
                }

                syslog(1,8,"Before " + Arrays.toString(userInput));
                /*validateCommand(userInput);*/
                String responseUtf8 = convertToUTF8(userInput);
                syslog(1,8,"Between: " + responseUtf8);
                String response = handleCommand(responseUtf8);

                messageToClient(response);
                // TODO needs to be in validation Method
            }

            socket.close();
        } catch (IOException e) {
            syslog(1,4,"Could not establish connection to new client");
        }
    }

    private String handleCommand(String command) { // TODO
        syslog(1,8, "Handling command: " + command);
        return command;
    }

    private void messageToClient(String message) throws IOException {
        syslog(1,8,"Sending message to client: " + message);
        dataOut.writeUTF("Server antwort: " + message);
    }

    private void welcomeClient(DataOutputStream dataOut) throws IOException {
        dataOut.writeUTF(config.getWelcomeMSG());
        for (String command : config.getCommands()) {
            dataOut.writeUTF(command);
        }
    }

    private String validateCommand(byte[] command) {
        return null;
    }

    private String convertToUTF8(byte[] arr) {
       return StringUtils.toEncodedString(arr, StandardCharsets.UTF_8); // apache commons-lang 3:3.6 libary
    }


}
