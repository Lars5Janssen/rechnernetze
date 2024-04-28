package server;

import config.Config;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

import static syslog.Syslog.syslog;

public class ClientHandler implements Runnable {

    private Config config = new Config().readConfigFromFile("src/main/resources/config.json");
    private final Socket socket;

    private DataInputStream dataIn;
    private byte[] streamBuffer = new byte[255];
    private StringBuilder userInput;
    private boolean whileFlag;
    private DataOutputStream dataOut;

    ClientHandler(Socket socket) { this.socket = socket; }

    @Override
    public void run() {
        try {
            syslog(1,8,"Accepted new Client");
            dataIn = new DataInputStream(socket.getInputStream());
            dataOut = new DataOutputStream(socket.getOutputStream());

            boolean whileFlag; //TODO RENAME

            welcomeClient();

            while (!socket.isClosed()) {
                messageToClient("\nDer Server erwartet eine eingabe:\n");
                whileFlag = false;

                while (!whileFlag) {
                    readSocketStream();

                    if (dataIn.available() > 0) { // Check if the stream have anything inside anymore
                        handleMessageOverSizeLimit();
                        break;
                    }

                    userInput.append(convertToUTF8(streamBuffer));

                    if (userInput.toString().getBytes(StandardCharsets.UTF_8).length > 255) {
                        handleMessageOverSizeLimit();
                        break;
                    }

                    int newLineIndex = userInput.indexOf("\n");
                    if (newLineIndex != -1) {
                        if (newLineIndex != userInput.length() || newLineIndex != userInput.lastIndexOf("\n")) {
                            // TODO handle this
                        } else {
                            whileFlag = true;
                        }
                    }
                }

                validateCommand(streamBuffer);
                String responseUtf8 = convertToUTF8(streamBuffer);
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

    private Boolean completedMessage() {
        return true;
    }
    private void readSocketStream() throws IOException{
        streamBuffer = new byte[255];
        int messageLength = dataIn.read(streamBuffer, 0, streamBuffer.length);// encoded in modified UTF-8 format
        streamBuffer = Arrays.copyOfRange(streamBuffer, 0, messageLength); // cut array to the actual message length
    }
    private void handleMessageOverSizeLimit() throws IOException{
        syslog(1, 4, "Message over 255 bytes received");
        messageToClient("Nachricht ist laenger als 255 Zeichen!");
        dataIn.skip(dataIn.available());
        userInput = new StringBuilder();
        streamBuffer = new byte[255];
    }

    private void messageToClient(String message) throws IOException {
        syslog(1,8,"Sending message to client: " + message);
        dataOut.writeUTF(message);
    }

    private void welcomeClient() throws IOException {
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
