package client;

import client.udp.UdpAnnouncer;
import client.network.ServerListener;
import client.view.GameBoard;
import shared.Constants;

import javax.swing.*;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.logging.Level;

public class ClientApp {

    public static void main(String[] args) {

        Socket socket = null;
        ObjectOutputStream outStream = null;
        ObjectInputStream inStream = null;

        try {
            Log.L.info("Kết nối tới " + Constants.HOST + ":" + Constants.PORT + " ...");
            socket = new Socket(Constants.HOST, Constants.PORT);

            outStream = new ObjectOutputStream(socket.getOutputStream());
            inStream = new ObjectInputStream(socket.getInputStream());
            Log.L.info("Đã kết nối tới Server.");

            // UDP announcer (LAN)
            new Thread(new UdpAnnouncer(Constants.UDP_PORT), "udp-listener").start();

            // UI
            GameBoard gameBoard = new GameBoard(outStream);
            SwingUtilities.invokeLater(() -> gameBoard.setVisible(true));

            // Listener
            ServerListener listener = new ServerListener(inStream, gameBoard);
            Thread listenerThread = new Thread(listener, "listener");
            listenerThread.start();

            // Wait until disconnected
            listenerThread.join();

        } catch (Exception e) {
            Log.L.log(Level.SEVERE, "Lỗi kết nối Client hoặc kết nối đã đóng", e);
            JOptionPane.showMessageDialog(
                null,
                "Không thể kết nối hoặc đã mất kết nối.",
                "Lỗi",
                JOptionPane.ERROR_MESSAGE
            );
        } finally {
            try { if (outStream != null) outStream.close(); } catch (Exception ignore) {}
            try { if (inStream != null) inStream.close(); } catch (Exception ignore) {}
            try { if (socket != null) socket.close(); } catch (Exception ignore) {}
            Log.L.info("Đã đóng tài nguyên client.");
        }
    }
}
