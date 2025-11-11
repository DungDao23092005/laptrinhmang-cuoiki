package server;

import server.udp.UdpBroadcaster;
import shared.Constants;
import shared.GameMessage;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;

public class ServerApp {

    public static ClientHandler waitingPlayer = null;

    public static synchronized void placePlayerInWaitingPool(ClientHandler player) {
        player.setPartner(null);
        player.setMyTurn(false);

        if (waitingPlayer == null) {
            waitingPlayer = player;
            player.setMark("X");
            player.sendMessage(new GameMessage(GameMessage.MSG_CHAT,
                    "Bạn là Người chơi 1 (X). Đang chờ đối thủ..."));
            Log.L.fine("Đưa player vào hàng chờ (đánh X).");

        } else {
            ClientHandler p1 = waitingPlayer; // người chờ
            ClientHandler p2 = player;        // người mới

            ClientHandler.clearStaticBoard(); // trận mới

            p2.setMark("O");
            p1.setPartner(p2);
            p2.setPartner(p1);
            waitingPlayer = null;

            p2.sendMessage(new GameMessage(GameMessage.MSG_CHAT,
                    "Đã tìm thấy trận! Bạn là Người chơi 2 (O)."));
            p1.sendMessage(new GameMessage(GameMessage.MSG_CHAT,
                    "Đã tìm thấy trận! Bạn là Người chơi 1 (X). Bạn đi trước."));

            p1.setMyTurn(true);
            p2.setMyTurn(false);

            Log.L.info("Ghép cặp thành công: P1(X) đi trước, P2(O) chờ.");
        }
    }

    public static void main(String[] args) {
        Log.L.info("Server đang khởi động...");

        // UDP broadcast (không bắt buộc)
        try {
            new Thread(new UdpBroadcaster(Constants.UDP_PORT), "udp-bc").start();
            Log.L.info("UDP broadcaster chạy trên cổng " + Constants.UDP_PORT);
        } catch (Throwable t) {
            Log.L.log(Level.WARNING, "Không bật được UDP broadcaster", t);
        }

        try (ServerSocket serverSocket = new ServerSocket(Constants.PORT)) {
            Log.L.info("Server đã sẵn sàng trên cổng: " + Constants.PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                Log.L.info("Client mới đã kết nối: " + clientSocket.getInetAddress());

                ClientHandler clientHandler = new ClientHandler(clientSocket);
                placePlayerInWaitingPool(clientHandler);
                new Thread(clientHandler, "cli-" + clientSocket.getPort()).start();
            }

        } catch (IOException e) {
            Log.L.log(Level.SEVERE, "Lỗi Server", e);
        }
    }
}
