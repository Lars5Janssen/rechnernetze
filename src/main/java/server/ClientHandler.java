package server;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;

import static syslog.Syslog.syslog;

public class ClientHandler implements Runnable {

    private final Socket socket;
    ClientHandler(Socket socket) { this.socket = socket; }

    @Override
    public void run() {
        try {
            // TODO in while loop
            syslog(1,8,"Accepted new Client");
            DataInputStream dataIn = new DataInputStream(socket.getInputStream()); // new BufferedInputStream(socket.getInputStream())
            DataOutputStream dataOut = new DataOutputStream(socket.getOutputStream());

            byte[] arr = new byte[255];
            String command = String.valueOf(dataIn.read(arr,0, arr.length)); // encoded in modified UTF-8 format
            if (dataIn.available() > 0) {syslog(1,4,"Message over 255 bytes received");} // TODO needs to be in validation Method
            syslog(1,8, Arrays.toString(arr));

            validateCommand(arr); // TODO

            String response = handleCommand(command);

            dataOut.writeUTF(response);

            socket.close();
        } catch (IOException e) {
            syslog(1,4,"Could not establish connection to new client");
        }
    }

    private String handleCommand(String command) { // TODO
        syslog(1,8, "Handling command: " + command);
        return command;
    }

    private String validateCommand(byte[] command) {
        return null;
    }
}
