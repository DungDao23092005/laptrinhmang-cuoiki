package client;

import client.network.ServerListener;
import client.view.GameBoard;
import shared.Constants;
import javax.swing.*;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ClientApp {

    public static void main(String[] args) {
        
        // Chúng ta KHÔNG thể dùng try-with-resources cho Socket ở đây
        // vì luồng main() sẽ đóng nó trước khi luồng listener kịp chạy
        Socket socket = null;
        ObjectOutputStream outStream = null;
        ObjectInputStream inStream = null;
        
        try {
            // 1. Khởi tạo kết nối và stream
            socket = new Socket(Constants.HOST, Constants.PORT);
            outStream = new ObjectOutputStream(socket.getOutputStream());
            inStream = new ObjectInputStream(socket.getInputStream());
            
            System.out.println("Đã kết nối tới Server.");

            // 2. Khởi tạo Giao diện (UI)
            GameBoard gameBoard = new GameBoard(outStream);
            SwingUtilities.invokeLater(() -> {
                gameBoard.setVisible(true);
            });

            // 3. Khởi tạo Luồng lắng nghe (Network Listener)
            ServerListener listener = new ServerListener(inStream, gameBoard);
            Thread listenerThread = new Thread(listener);
            listenerThread.start();

            // 4. Bắt luồng main() chờ luồng listener
            // (Nếu listener chết (mất kết nối), .join() sẽ kết thúc
            // và khối finally sẽ dọn dẹp)
            listenerThread.join(); 

        } catch (Exception e) {
            System.err.println("Lỗi kết nối Client hoặc kết nối đã đóng: " + e.getMessage());
            JOptionPane.showMessageDialog(null, "Không thể kết nối hoặc đã mất kết nối.", "Lỗi", JOptionPane.ERROR_MESSAGE);
        } finally {
            // 5. Dọn dẹp tài nguyên theo cách thủ công
            // (Đây là lý do lỗi "Socket closed" ở client 2 xảy ra)
            try {
                if (outStream != null) outStream.close();
                if (inStream != null) inStream.close();
                if (socket != null) socket.close();
            } catch (Exception e) {
                // Bỏ qua lỗi khi dọn dẹp
            }
        }
    }
}