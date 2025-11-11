package server;

import shared.Constants;
import shared.GameMessage;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerApp {
    
    public static ClientHandler waitingPlayer = null;

    public static synchronized void placePlayerInWaitingPool(ClientHandler player) {
        
        player.setPartner(null);
        player.setMyTurn(false);

        if (waitingPlayer == null) {
            waitingPlayer = player;
            player.setMark("X");
            player.sendMessage(new GameMessage(GameMessage.MSG_CHAT, "Bạn là Người chơi 1 (X). Đang chờ đối thủ..."));
            
        } else {
            // Hàng chờ đã có người! Ghép cặp họ
            ClientHandler p1 = waitingPlayer; // Người chờ
            ClientHandler p2 = player;         // Người mới
            
            // >>>>> SỬA LỖI Ở ĐÂY <<<<<
            // Reset bàn cờ logic cho trận đấu MỚI
            ClientHandler.clearStaticBoard();
            
            p2.setMark("O");
            p1.setPartner(p2);
            p2.setPartner(p1);

            waitingPlayer = null; 

            p2.sendMessage(new GameMessage(GameMessage.MSG_CHAT, "Đã tìm thấy trận! Bạn là Người chơi 2 (O)."));
            p1.sendMessage(new GameMessage(GameMessage.MSG_CHAT, "Đã tìm thấy trận! Bạn là Người chơi 1 (X). Bạn đi trước."));

            p1.setMyTurn(true); 
            p2.setMyTurn(false);
        }
    }

    public static void main(String[] args) {
        System.out.println("Server đang khởi động...");
        
        try (ServerSocket serverSocket = new ServerSocket(Constants.PORT)) {
            System.out.println("Server đã sẵn sàng trên cổng: " + Constants.PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client mới đã kết nối: " + clientSocket.getInetAddress());

                ClientHandler clientHandler = new ClientHandler(clientSocket);
                placePlayerInWaitingPool(clientHandler);
                new Thread(clientHandler).start();
            }
            
        } catch (IOException e) {
            System.err.println("Lỗi Server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}