package client;

import server.FCpacket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.BlockingQueue;

public class FileCopyClientRecive implements Runnable{

    private DatagramSocket socket;
    private BlockingQueue<FCpacket> queue;
    private final int UDP_PACKET_SIZE;
    private final InetAddress SERVER_IP;
    private final int SERVER_PORT;

    public FileCopyClientRecive(int UDP_PACKET_SIZE, String SERVER_IP, int SERVER_PORT, DatagramSocket socket, BlockingQueue<FCpacket> queue ) throws UnknownHostException {
        this(UDP_PACKET_SIZE, InetAddress.getByName(SERVER_IP), SERVER_PORT, socket, queue);
    }
    public FileCopyClientRecive(int UDP_PACKET_SIZE, byte[] SERVER_IP, int SERVER_PORT, DatagramSocket socket, BlockingQueue<FCpacket> queue ) throws UnknownHostException {
        this(UDP_PACKET_SIZE, InetAddress.getByAddress(SERVER_IP), SERVER_PORT, socket, queue);
    }

    public FileCopyClientRecive(int UDP_PACKET_SIZE, int SERVER_PORT, DatagramSocket socket, BlockingQueue<FCpacket> queue ) {
        this(UDP_PACKET_SIZE, InetAddress.getLoopbackAddress(), SERVER_PORT, socket, queue);
    }
    public FileCopyClientRecive(int UDP_PACKET_SIZE, InetAddress SERVER_IP, int SERVER_PORT, DatagramSocket socket, BlockingQueue<FCpacket> queue) {
        this.UDP_PACKET_SIZE = UDP_PACKET_SIZE;
        this.SERVER_IP = SERVER_IP;
        this.SERVER_PORT = SERVER_PORT;
        this.socket = socket;
        this.queue = queue;
    }

    @Override
    public void run() {
        while (true) {
            DatagramPacket receivedPacket = new DatagramPacket(new byte[UDP_PACKET_SIZE], UDP_PACKET_SIZE);
            try {
                socket.receive(receivedPacket);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            FCpacket fCpacket = new FCpacket(receivedPacket.getData());
            queue.add(fCpacket);

        }
    }
}