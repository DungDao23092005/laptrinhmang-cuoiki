package server;

import shared.Constants;
import shared.GameMessage;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private ObjectOutputStream outStream;
    private ObjectInputStream inStream;

    private ClientHandler partner;     // đối thủ hiện tại (nếu có)
    private String mark;               // "X" hoặc "O"
    private boolean myTurn = false;    // đang tới lượt mình?
    private boolean agreedToPlayAgain = false;

    // Heartbeat
    private volatile long lastSeen = System.currentTimeMillis();

    // Bàn cờ logic dùng chung cho trận (clear khi bắt đầu trận mới / chơi lại)
    private static final String[][] board = new String[Constants.BOARD_SIZE][Constants.BOARD_SIZE];

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public void setPartner(ClientHandler partner) { this.partner = partner; }
    public void setMark(String mark) { this.mark = mark; }
    public void setMyTurn(boolean myTurn) { this.myTurn = myTurn; }

    public static synchronized void clearStaticBoard() {
        for (int i = 0; i < Constants.BOARD_SIZE; i++) {
            for (int j = 0; j < Constants.BOARD_SIZE; j++) {
                board[i][j] = "";
            }
        }
    }

    static {
        clearStaticBoard();
    }

    @Override
    public void run() {
        // Watcher thread: timeout nếu không thấy nhịp PING nào > 15s
        new Thread(() -> {
            try {
                while (!socket.isClosed()) {
                    if (System.currentTimeMillis() - lastSeen > 15000) {
                        System.out.println("Timeout client: " + socket.getInetAddress());
                        try { socket.close(); } catch (IOException ignore) {}
                        break;
                    }
                    Thread.sleep(2000);
                }
            } catch (InterruptedException ignore) {}
        }, "hb-" + socket.getPort()).start();

        try {
            outStream = new ObjectOutputStream(socket.getOutputStream());
            inStream = new ObjectInputStream(socket.getInputStream());

            while (true) {
                try {
                    GameMessage msg = (GameMessage) inStream.readObject();
                    lastSeen = System.currentTimeMillis(); // cập nhật heartbeat

                    // Xử lý heartbeat
                    if (GameMessage.MSG_PING.equals(msg.getType())) {
                        sendMessage(new GameMessage(GameMessage.MSG_PONG, null));
                        continue;
                    }

                    handleMessage(msg);

                } catch (ClassNotFoundException e) {
                    System.err.println("Client gửi đối tượng không xác định.");
                } catch (EOFException e) {
                    // Client đóng kết nối
                    System.out.println("Client ngắt kết nối: " + socket.getInetAddress());

                    if (partner != null) {
                        // Báo cho đối thủ: partner đã thoát -> client kia sẽ tự thoát
                        partner.sendMessage(new GameMessage(GameMessage.MSG_PARTNER_QUIT, null));
                        partner.partner = null;
                    }
                    // Nếu mình đang ở hàng chờ, bỏ mình khỏi hàng chờ
                    if (ServerApp.waitingPlayer == this) {
                        ServerApp.waitingPlayer = null;
                    }
                    break;
                }
            }
        } catch (IOException e) {
            System.out.println("Loi I/O voi Client: " + e.getMessage());
        } 
        finally {
            closeConnections();
        }
    }

    private void handleMessage(GameMessage msg) {
        switch (msg.getType()) {
            case GameMessage.MSG_MOVE: {
                if (!myTurn || partner == null) {
                    sendMessage(new GameMessage(GameMessage.MSG_CHAT, "Chưa đến lượt của bạn!"));
                    return;
                }

                String data = (String) msg.getData();
                String[] parts = data.split(",");
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);

                synchronized (board) {
                    if (!board[x][y].equals("")) {
                        sendMessage(new GameMessage(GameMessage.MSG_CHAT, "Nước đi không hợp lệ!"));
                        return;
                    }
                    board[x][y] = this.mark;
                }

                // Cập nhật UI cho cả hai bên
                String updateData = x + "," + y + "," + this.mark;
                GameMessage updateMsg = new GameMessage(GameMessage.MSG_UPDATE_BOARD, updateData);
                sendMessage(updateMsg);
                if (partner != null) partner.sendMessage(updateMsg);

                // Kiểm tra thắng
                if (checkWin(x, y)) {
                    sendMessage(new GameMessage(GameMessage.MSG_WIN, "Bạn thắng!"));
                    if (partner != null) partner.sendMessage(new GameMessage(GameMessage.MSG_LOSE, "Bạn thua!"));
                    this.myTurn = false;
                    if (partner != null) partner.setMyTurn(false);
                } else {
                    // Đổi lượt + thông báo
                    this.myTurn = false;
                    if (partner != null) {
                        partner.setMyTurn(true);
                        this.sendMessage(new GameMessage(GameMessage.MSG_CHAT, "Bạn đã đi. Chờ đối thủ..."));
                        partner.sendMessage(new GameMessage(GameMessage.MSG_CHAT, "Tới lượt bạn."));
                    }
                }
                break;
            }

            case GameMessage.MSG_CHAT: {
                if (partner != null) partner.sendMessage(msg);
                break;
            }

            case GameMessage.MSG_PLAY_AGAIN_REQUEST: {
                this.agreedToPlayAgain = true;

                if (partner != null && partner.agreedToPlayAgain) {
                    // Cả hai đồng ý -> reset ván mới và đảo người đi trước
                    if ("X".equals(this.mark)) {
                        // Lượt mới: partner sẽ là X (đi trước), mình là O
                        this.setMark("O");
                        this.setMyTurn(false);
                        partner.setMark("X");
                        partner.setMyTurn(true);
                    } else {
                        this.setMark("X");
                        this.setMyTurn(true);
                        partner.setMark("O");
                        partner.setMyTurn(false);
                    }

                    this.agreedToPlayAgain = false;
                    partner.agreedToPlayAgain = false;

                    clearStaticBoard();

                    GameMessage resetMsg = new GameMessage(GameMessage.MSG_GAME_RESET, null);
                    sendMessage(resetMsg);
                    partner.sendMessage(resetMsg);

                    if (this.myTurn) {
                        sendMessage(new GameMessage(GameMessage.MSG_CHAT, "Chơi lại! Bạn là X. Bạn đi trước."));
                        partner.sendMessage(new GameMessage(GameMessage.MSG_CHAT, "Chơi lại! Bạn là O. Chờ đối thủ đi."));
                    } else {
                        sendMessage(new GameMessage(GameMessage.MSG_CHAT, "Chơi lại! Bạn là O. Chờ đối thủ đi."));
                        partner.sendMessage(new GameMessage(GameMessage.MSG_CHAT, "Chơi lại! Bạn là X. Bạn đi trước."));
                    }

                } else if (partner != null) {
                    // Mình đã đồng ý, chờ đối thủ
                    sendMessage(new GameMessage(GameMessage.MSG_CHAT, "Bạn đã đồng ý chơi lại. Đang chờ đối thủ..."));
                    partner.sendMessage(new GameMessage(GameMessage.MSG_CHAT, "Đối thủ muốn chơi lại! Bạn có đồng ý không?"));
                }
                break;
            }

            case GameMessage.MSG_PLAY_AGAIN_DECLINE: {
                // Người chơi này từ chối chơi lại
                this.agreedToPlayAgain = false;

                if (partner != null) {
                    // Thông báo cho đối thủ và đưa họ về hàng chờ
                    partner.agreedToPlayAgain = false;
                    partner.sendMessage(new GameMessage(GameMessage.MSG_CHAT, "Đối thủ không muốn chơi lại."));
                    partner.sendMessage(new GameMessage(GameMessage.MSG_FIND_NEW_OPPONENT, null));

                    // Cắt liên kết cặp
                    ClientHandler remain = partner;
                    remain.setPartner(null);
                    remain.setMyTurn(false);
                    this.partner = null;

                    clearStaticBoard();

                    // Đưa đối thủ còn lại về hàng chờ để ghép trận mới
                    ServerApp.placePlayerInWaitingPool(remain);
                }
                // Người từ chối sẽ tự đóng app ở client
                break;
            }

            default:
                // các loại khác (nếu có)...
                break;
        }
    }

    // Thuật toán kiểm tra thắng 5 liên tiếp (hàng/cột/chéo)
    private boolean checkWin(int x, int y) {
        String m = this.mark;
        if (m == null || m.isEmpty()) return false;

        // Hàng
        int cnt = 0;
        for (int j = 0; j < Constants.BOARD_SIZE; j++) {
            if (m.equals(board[x][j])) { cnt++; if (cnt >= 5) return true; } else cnt = 0;
        }

        // Cột
        cnt = 0;
        for (int i = 0; i < Constants.BOARD_SIZE; i++) {
            if (m.equals(board[i][y])) { cnt++; if (cnt >= 5) return true; } else cnt = 0;
        }

        // Chéo chính
        cnt = 0;
        int iStart = x, jStart = y;
        while (iStart > 0 && jStart > 0) { iStart--; jStart--; }
        while (iStart < Constants.BOARD_SIZE && jStart < Constants.BOARD_SIZE) {
            if (m.equals(board[iStart][jStart])) { cnt++; if (cnt >= 5) return true; } else cnt = 0;
            iStart++; jStart++;
        }

        // Chéo phụ
        cnt = 0;
        iStart = x; jStart = y;
        while (iStart > 0 && jStart < Constants.BOARD_SIZE - 1) { iStart--; jStart++; }
        while (iStart < Constants.BOARD_SIZE && jStart >= 0) {
            if (m.equals(board[iStart][jStart])) { cnt++; if (cnt >= 5) return true; } else cnt = 0;
            iStart++; jStart--;
        }

        return false;
    }

    public void sendMessage(GameMessage msg) {
        if (outStream == null) return;
        try {
            outStream.writeObject(msg);
            outStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void closeConnections() {
        try { if (inStream != null) inStream.close(); } catch (IOException ignore) {}
        try { if (outStream != null) outStream.close(); } catch (IOException ignore) {}
        try { if (socket != null) socket.close(); } catch (IOException ignore) {}
    }
}
