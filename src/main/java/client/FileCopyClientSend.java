package client;

import server.FCpacket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.BlockingQueue;

import static syslog.Syslog.syslog;

public class FileCopyClientSend implements Runnable {
    private DatagramSocket socket;
    private BlockingQueue<byte[]> queue;
    private final int UDP_PACKET_SIZE;
    private final InetAddress SERVER_IP;
    private final int SERVER_PORT;
    private String facility = "SEND";

    public FileCopyClientSend(int UDP_PACKET_SIZE, String SERVER_IP, int SERVER_PORT, DatagramSocket socket, BlockingQueue<byte[]> queue) throws UnknownHostException {
        this(UDP_PACKET_SIZE, InetAddress.getByName(SERVER_IP), SERVER_PORT, socket, queue);
    }
    public FileCopyClientSend(int UDP_PACKET_SIZE, InetAddress SERVER_IP, int SERVER_PORT, DatagramSocket socket, BlockingQueue<byte[]> queue) {
        this.UDP_PACKET_SIZE = UDP_PACKET_SIZE;
        this.SERVER_IP = SERVER_IP;
        this.SERVER_PORT = SERVER_PORT;
        this.socket = socket;
        this.queue = queue;
    }


    @Override
    public void run() {
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                syslog(facility, 5, "Interrupted");
                break;
            }
            try {
                byte[] packet;
                packet = queue.take();
                socket.send(new DatagramPacket(packet, packet.length, SERVER_IP, SERVER_PORT));
                FCpacket fCpacket = new FCpacket(packet, packet.length);
                syslog(facility,8,"Sent Packet with seqnum: " + fCpacket.getSeqNum());
            } catch (InterruptedException | IOException e) {
                syslog(facility,5,"Interrupted");
                break;
            }
        }
    }
}