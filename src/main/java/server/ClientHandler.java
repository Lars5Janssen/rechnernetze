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

    private byte[] streamBuffer = new byte[255];
    private StringBuilder userInputBuild;
    private DataInputStream dataIn;
    private DataOutputStream dataOut ;

    ClientHandler(Socket socket) {
        this.socket = socket;
        try {
            this.dataIn = new DataInputStream(socket.getInputStream());
            this.dataOut = new DataOutputStream(socket.getOutputStream());
            syslog(1,8,"Accepted new Client");
        } catch (IOException e) {
            syslog(1,1,"Could not establish input or output stream");
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run(){

        while (!socket.isClosed()) {
            messageToClient("Der Server erwartet eine eingabe:\n");
            String userInput = getUserInput();

            validateCommand(streamBuffer);
            String responseUtf8 = convertToUTF8(streamBuffer);
            String response = handleCommand(responseUtf8);

            messageToClient(response);
            // TODO needs to be in validation Method
        }
        try {
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private String getUserInput() {
        userInputBuild = new StringBuilder();
        byte[] streamBuffer = new byte[config.getPackageLength()];
        int messageLength = 0;
        boolean continueToReadInputStream = true;

        while (continueToReadInputStream) {
            // Get set length of bytes from input stream (length set in config)
            // and transfer them to stringBuilder, while cutting null bytes added by dataIn.read
            streamBuffer = new byte[config.getPackageLength()];
            try {
                messageLength = dataIn.read(streamBuffer, 0, streamBuffer.length);
            } catch (IOException e) {
                syslog(1,1, "Could not read from dataIn");
            }
            streamBuffer = Arrays.copyOfRange(streamBuffer, 0, messageLength); // cut array to the actual message length

            // Check if there are more bytes
            // if there are, there were more than max packageLength of bytes.\
            // and everything needs to be discarded.
            // TODO (Or a new package has arrived right between dataIn.read (above) and now.)
            try {
                if (dataIn.available() > 0) {
                    syslog(1,4, "More data is available than expected");
                    // TODO handle case / reset everything (handleMessageOverSizeLimit() )
                    break;
                }
            } catch (IOException e) {
                syslog(1,1,"Could not get remaining length from dataIn");
            }

            // Append the byteArray of correct length to the stringBuilder
            userInputBuild.append(convertToUTF8(streamBuffer));

            // Check if the stringBuilder has exceeded the maximum package size,
            // if so, the command is invalid and everything needs to be discarded.
            if (userInputBuild.toString().getBytes(StandardCharsets.UTF_8).length > config.getPackageLength()) { // TODO refactor 255 magic number
                syslog(1,4, "User input has exceeded maximum length");
                // TODO handle case / reset everything (handleMessageOverSizeLimit() )
                break;
            }

            int newLineIndex = userInputBuild.indexOf("\n");
            int lastNewLineIndex = userInputBuild.lastIndexOf("\n");
            int stringLengthFromZero = userInputBuild.length() - 1;

            syslog(1,8,String.format(
                    "\nFirst NL: %s\nLast NL: %s\nLength (from 0): %s\nString: \n\"\"\"%s\"\"\"\n",
                    newLineIndex, lastNewLineIndex, stringLengthFromZero, userInputBuild));

            // Exit condition and newline conformity checks
            if (newLineIndex != -1) { // We have at least one newline

                if (newLineIndex != lastNewLineIndex) { // We have more than one newline
                    syslog(1,4,"More than one newline found");
                    // TODO handle case

                } else if (lastNewLineIndex != stringLengthFromZero) { // The one newline is not at the end
                    syslog(1,4,"Newline is not at the end of the input");
                    // TODO handle case

                } else { // everything is fine. the string is ok. Exit while loop
                    syslog(1,8,"The user input is valid.");
                    continueToReadInputStream = false;
                    // TODO handle exit

                }
            }
        }

        return userInputBuild.toString();
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
        int messageLength = 0;// encoded in modified UTF-8 format
        messageLength = dataIn.read(streamBuffer, 0, streamBuffer.length);

        streamBuffer = Arrays.copyOfRange(streamBuffer, 0, messageLength); // cut array to the actual message length
    }
    private void handleMessageOverSizeLimit() throws IOException{
        syslog(1, 4, "Message over 255 bytes received");
        messageToClient("Nachricht ist laenger als 255 Zeichen!");
        dataIn.skip(dataIn.available());
        userInputBuild = new StringBuilder();
        streamBuffer = new byte[255];
    }

    private void messageToClient(String message) {
        syslog(1,8,"Sending message to client: " + message);
        try {
            dataOut.writeUTF(message);
        } catch (IOException e) {
            syslog(1,1,"Could not message client");
        }
    }

    private void welcomeClient(){
        try {
            dataOut.writeUTF(config.getWelcomeMSG());
            for (String command : config.getCommands()) {
                dataOut.writeUTF(command);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        
    }

    private String validateCommand(byte[] command) {
        return null;
    }

    private String convertToUTF8(byte[] arr) {
       return StringUtils.toEncodedString(arr, StandardCharsets.UTF_8); // apache commons-lang 3:3.6 libary
    }


}
