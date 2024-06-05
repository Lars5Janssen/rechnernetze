package client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.BlockingQueue;

public class FileCopyClientSend implements Runnable {
    private DatagramSocket socket;
    private BlockingQueue<byte[]> queue;
    private final int UDP_PACKET_SIZE;
    private final InetAddress SERVER_IP;
    private final int SERVER_PORT;

    public FileCopyClientSend(int UDP_PACKET_SIZE, DatagramSocket socket, BlockingQueue<byte[]> queue) {
        this.UDP_PACKET_SIZE = UDP_PACKET_SIZE;
        this.SERVER_IP = socket.getInetAddress();
        this.SERVER_PORT = socket.getPort();
        this.socket = socket;
        this.queue = queue;
    }


    @Override
    public void run() {
        while (true) {
            byte[] packet;
            try {
                packet = queue.take();
                socket.send(new DatagramPacket(packet, UDP_PACKET_SIZE, SERVER_IP, SERVER_PORT));
            } catch (InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}