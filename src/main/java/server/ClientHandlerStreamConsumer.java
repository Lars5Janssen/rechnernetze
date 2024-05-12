package server;

import config.Config;
import org.apache.commons.lang3.StringUtils;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;

import static syslog.Syslog.syslog;

public class ClientHandlerStreamConsumer implements Runnable{
    private Config config = new Config().readConfigFromFile("src/main/resources/main/config.json");
    private Socket socket;
    private DataInputStream dataIn;
    private BlockingQueue<String> inputQueue;
    private byte[] streamBuffer = new byte[config.getPackageLength()];
    private StringBuilder userInputBuild;
    private boolean errorFlag = false;

    public ClientHandlerStreamConsumer(Socket socket, BlockingQueue<String> inputQueue) {
        this.socket = socket;
        try {
            this.dataIn = new DataInputStream(socket.getInputStream());
        } catch (IOException e) {
            syslog(1,1, "Could not establish intput stream");
            throw new RuntimeException(e);
        }
        this.inputQueue = inputQueue;
    }
    @Override
    public void run() {
        while (!socket.isClosed() && !errorFlag) {
            String userInput = getUserInput();
            if (userInput == null ) {
                syslog(1,1,"Error while reading input");
                return;
            }
            if(!inputQueue.add(userInput)) {
                syslog(1,4,String.format("Error in adding to queue. Might have more then %s Messages", config.getMessageQueueLength()));
            }
        }
    }
    private boolean checkMaxPackageLength(int messageLength) {
        if (messageLength > config.getPackageLength()) {
            return false;
        }
        return true;
    }
    private String convertToUTF8(byte[] arr) {
        return StringUtils.toEncodedString(arr, StandardCharsets.UTF_8); // apache commons-lang 3:3.6 libary
    }
    private void handleMessageOverSizeLimit() throws IOException{
        syslog(1, 4, "Message over 255 bytes received");
        //messageToClient("Nachricht ist laenger als 255 Zeichen!");
        dataIn.skip(dataIn.available());
        userInputBuild = new StringBuilder();
        streamBuffer = new byte[255];
    }
    private String getUserInput() {
        userInputBuild = new StringBuilder();
        byte[] streamBuffer;
        int messageLength = 0;

        // Break when one command found
        while (!socket.isClosed() && !errorFlag) {
            // Get set length of bytes from input stream (length set in config)
            // and transfer them to stringBuilder, while cutting null bytes added by dataIn.read
            streamBuffer = new byte[config.getPackageLength()];

            try {
                messageLength = dataIn.read(streamBuffer, 0, streamBuffer.length);
            } catch (IOException e) {
                syslog(1,1, "Could not read from dataIn");
                errorFlag = true;
            }

            if (checkMaxPackageLength(messageLength)) {
                try {
                    streamBuffer = Arrays.copyOfRange(streamBuffer, 0, messageLength); // cut array to the actual message length
                } catch (IllegalArgumentException e) {
                    syslog(1,1,"messageLength not set, could not read from dataIn");
                    errorFlag = true;
                    return null;
                }
                };

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
                    "\nFirst NL: %s\nLast NL: %s\nLength (from 0): %s\nString: \n========================================\n%s\n========================================\n",
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
                    break;
                }
            }
        }

        return userInputBuild.toString();
    }
}
