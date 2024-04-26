package server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import static syslog.Syslog.syslog;

public class ClientHandler implements Runnable {

    private final Socket socket;
    ClientHandler(Socket socket) { this.socket = socket; }

    @Override
    public void run() {
        try {
            DataInputStream dataIn = new DataInputStream(socket.getInputStream()); // new BufferedInputStream(socket.getInputStream())
            DataOutputStream dataOut = new DataOutputStream(socket.getOutputStream());

            String command = DataInputStream.readUTF(dataIn); // encoded in modified UTF-8 format

            String response = handleCommand(command);

            dataOut.writeUTF(response);

            socket.close();
        } catch (IOException e) {
            syslog(1,4,"Connection zum Client konnte nicht hergestellt werden");
        }
    }

    private String handleCommand(String command) {
        System.out.println("In handleCommand: ");
        return command;
    }
}
