package client;

import server.FCpacket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.BlockingQueue;

public class FileCopyClientSend implements Runnable {
    private DatagramSocket socket;
    private BlockingQueue<FCpacket> queue;
    private final int UDP_PACKET_SIZE;
    private final InetAddress SERVER_IP;
    private final int SERVER_PORT;

    public FileCopyClientSend(int UDP_PACKET_SIZE, int SERVER_PORT, DatagramSocket socket, BlockingQueue<FCpacket> queue) {
        this.UDP_PACKET_SIZE = UDP_PACKET_SIZE;
        this.SERVER_IP = InetAddress.getLoopbackAddress();
        this.SERVER_PORT = SERVER_PORT;
        this.socket = socket;
        this.queue = queue;
    }


    @Override
    public void run() {
        while (true) {
        FCpacket packet;
        try {
            packet = queue.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        try {
            socket.send(new DatagramPacket(packet.getSeqNumBytesAndData(), UDP_PACKET_SIZE, SERVER_IP, SERVER_PORT));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    }
}
