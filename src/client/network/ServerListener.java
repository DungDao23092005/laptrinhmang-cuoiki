package client.network;

import client.view.GameBoard;
import shared.GameMessage;
import javax.swing.SwingUtilities;
import java.io.EOFException;
import java.io.ObjectInputStream;

public class ServerListener implements Runnable {
    
    private ObjectInputStream inStream;
    private GameBoard gameBoard; // Để cập nhật UI

    public ServerListener(ObjectInputStream inStream, GameBoard gameBoard) {
        this.inStream = inStream;
        this.gameBoard = gameBoard;
    }

    @Override
    public void run() {
        try {
            // Vòng lặp vô tận để lắng nghe tin nhắn từ Server
            while (true) {
                // Đọc tin nhắn
                GameMessage msg = (GameMessage) inStream.readObject();

                // **QUAN TRỌNG**: Cập nhật UI phải qua luồng của Swing
                // Dùng SwingUtilities.invokeLater để đảm bảo an toàn
                SwingUtilities.invokeLater(() -> {
                    gameBoard.handleServerMessage(msg);
                });
            }
        } catch (EOFException e) {
            System.out.println("Server đã đóng kết nối.");
        } catch (Exception e) {
            System.err.println("Lỗi lắng nghe Server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}