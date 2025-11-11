package server.udp;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Phát bản tin UDP broadcast "CaroServer:online" mỗi 3s để client phát hiện server trong LAN.
 */
public class UdpBroadcaster implements Runnable {
    private final int port;
    private volatile boolean running = true;

    public UdpBroadcaster(int port) {
        this.port = port;
    }

    public void stop() { running = false; }

    @Override
    public void run() {
        byte[] data = "CaroServer:online".getBytes();
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            InetAddress broadcast = InetAddress.getByName("255.255.255.255");

            while (running) {
                DatagramPacket packet = new DatagramPacket(data, data.length, broadcast, port);
                socket.send(packet);
                Thread.sleep(3000);
            }
        } catch (Exception e) {
            System.out.println("[UDP-BC] stopped: " + e.getMessage());
        }
    }
}
