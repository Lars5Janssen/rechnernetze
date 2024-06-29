package client;

import server.FCpacket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.BlockingQueue;

import static syslog.Syslog.syslog;

public class FileCopyClientReceive implements Runnable{

    private DatagramSocket socket;
    private BlockingQueue<FCpacket> queue;
    private final int UDP_PACKET_SIZE;
    private final InetAddress SERVER_IP;
    private final int SERVER_PORT;
    private String facility = "REVICE";

    public FileCopyClientReceive(int UDP_PACKET_SIZE, String SERVER_IP, int SERVER_PORT, DatagramSocket socket, BlockingQueue<FCpacket> queue) throws UnknownHostException {
        this(UDP_PACKET_SIZE, InetAddress.getByName(SERVER_IP), SERVER_PORT, socket, queue);
    }

    public FileCopyClientReceive(int UDP_PACKET_SIZE, InetAddress SERVER_IP, int SERVER_PORT, DatagramSocket socket, BlockingQueue<FCpacket> queue) {
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
                syslog(facility,5,"Interrupted");
                break;
            }
            try {
                DatagramPacket receivedPacket = new DatagramPacket(new byte[UDP_PACKET_SIZE], UDP_PACKET_SIZE);
                socket.receive(receivedPacket);
                FCpacket fCpacket = new FCpacket(receivedPacket.getData(), receivedPacket.getLength());
                fCpacket.setTimestamp(System.nanoTime());
                fCpacket.setValidACK(true); // TODO this should be done on another place
                syslog(facility,8, "Recived packet with seqnum: " + fCpacket.getSeqNum());
                queue.add(fCpacket);
            } catch (IOException e) {
                syslog(facility,4,"IO Exception");
            }

        }
    }
}
