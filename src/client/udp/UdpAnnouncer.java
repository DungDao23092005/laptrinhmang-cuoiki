package client.udp;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * Lắng nghe bản tin UDP trong LAN để biết server đang online.
 * In ra console (có thể nối UI tuỳ ý).
 */
public class UdpAnnouncer implements Runnable {
    private final int port;
    private volatile boolean running = true;

    public UdpAnnouncer(int port) {
        this.port = port;
    }

    public void stop() { running = false; }

    @Override
    public void run() {
        byte[] buf = new byte[512];
        try (DatagramSocket socket = new DatagramSocket(port)) {
            while (running) {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                socket.receive(p);
                String msg = new String(p.getData(), 0, p.getLength());
                System.out.println("[UDP] " + p.getAddress().getHostAddress() + ": " + msg);
                // Nếu muốn nối vào UI:
                // SwingUtilities.invokeLater(() -> gameBoard.appendLog("[UDP] " + ...));
            }
        } catch (Exception e) {
            System.out.println("[UDP] listener stopped: " + e.getMessage());
        }
    }
}
