package server;

import config.Config;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

import static syslog.Syslog.syslog;

public class ClientHandler implements Runnable {

    private Config config = new Config().readConfigFromFile("src/main/resources/main/config.json");
    private final Socket socket;
    private ClientHandlerStreamConsumer clientHandlerStreamConsumer;
    private BlockingQueue<String> inputQueue;
    private Thread thread;
    String userInput;
    private DataOutputStream dataOut;

    ClientHandler(Socket socket) {
        this.socket = socket;
        try {
            this.dataOut = new DataOutputStream(socket.getOutputStream());
            syslog(1,8,"Accepted new Client");
        } catch (IOException e) {
            syslog(1,1,"Could not establish output stream");
            throw new RuntimeException(e);
        }
        this.inputQueue = new LinkedBlockingDeque<>(config.getMessageQueueLength());
        clientHandlerStreamConsumer = new ClientHandlerStreamConsumer(this.socket, this.inputQueue);
        thread = new Thread(clientHandlerStreamConsumer);
        thread.start();
    }

    @Override
    public void run(){

        while (!socket.isClosed() && thread.isAlive()) {
            // welcomeClient();
            messageToClient("Der Server erwartet eine eingabe:\n");

            try {
                userInput = inputQueue.take();
            } catch (InterruptedException e) {
                syslog(1,1,"Was interrupted when taking out of input queue");
                throw new RuntimeException(e);
            }

            if (validateCommand(userInput)) {
                String response = handleMessage(userInput);

                messageToClient("OK " + response);
            } else {
                messageToClient("ERROR Message not compliant\n");
            }
            // TODO needs to be in validation Method
        }
        try {
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private boolean validateCommand(String message) {
        syslog(1,8,message);
        String[] commands = new String[config.getCommands().size()];
        for (int i = 0; i < config.getCommands().size(); i++) {
            commands[i] = config.getCommands().get(i).split(" ")[0];
        }

        for (String command : commands) {
            if (message.indexOf(command) == 0) {
                if (command.equals("BYE") && message.length() == 4) return true;
                else if (command.equals("SHUTDOWN") && message.length() == 9) return true;
                else if (command.equals("BYE") || command.equals("SHUTDOWN")) return false;

                if (message.indexOf(" ") == command.length()) return true;
            }
        }
        return false;
    }
    private String handleMessage(String message) { // TODO
        syslog(1,8, "Handling message: " + message);
        String[] messageSplit = message.split(" ");
        String command = messageSplit[0];
        return switch (command) {
            case "LOWERCASE" -> messageSplit[1].toLowerCase();
            case "UPPERCASE" -> messageSplit[1].toUpperCase();
            case "REVERSE" -> new StringBuilder(messageSplit[1]).reverse().toString();
            case "BYE" -> {
                handleBye();
                yield command;
            }
            case "SHUTDOWN" -> {
                handleShutdown();
                yield command;
            }
            default -> message;
        };
    }
    private void handleBye() {
        syslog(1,8,"Invoked Bye");
    }
    private void handleShutdown() {
        syslog(1,8, "Invoked Shutdown");
    }
    private boolean checkMaxPackageLength(int messageLength) {
        if (messageLength > config.getPackageLength()) {
            return false;
        }
        return true;
    }
    private void closeSocket() {
        try {
            socket.close();
        } catch (IOException e) {
            syslog(1,1,"Could not close socket");
            throw new RuntimeException(e);
        }
    }
    private Boolean completedMessage() {
        return true;
    }


    private void messageToClient(String message) {
        syslog(1,8,"Sending message to client: " + message + "\n");
        try {
            dataOut.writeBytes(message + "\n");
        } catch (IOException e) {
            syslog(1,1,"Could not message client");
        }
    }

    private void welcomeClient(){
        messageToClient(config.getWelcomeMSG());
        for (String command : config.getCommands()) {
            messageToClient(command);
        }
    }
    private String convertToUTF8(byte[] arr) {
        return StringUtils.toEncodedString(arr, StandardCharsets.UTF_8); // apache commons-lang 3:3.6 libary
    }


}
