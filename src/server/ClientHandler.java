package server;

import shared.Constants;
import shared.GameMessage;
import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private Socket socket;
    private ObjectOutputStream outStream;
    private ObjectInputStream inStream;
    
    private ClientHandler partner; 
    private String mark; 
    private boolean myTurn = false; 
    private boolean agreedToPlayAgain = false; // Vẫn giữ để xử lý 2 người cùng nhấn CÓ

    // Bàn cờ logic (static)
    private static String[][] board = new String[Constants.BOARD_SIZE][Constants.BOARD_SIZE];
    
    // Đã sửa thành public
    public static synchronized void clearStaticBoard() {
        for(int i=0; i<Constants.BOARD_SIZE; i++) {
            for(int j=0; j<Constants.BOARD_SIZE; j++) {
                board[i][j] = "";
            }
        }
    }
    // Khởi tạo bàn cờ lần đầu
    static { clearStaticBoard(); } 

    public ClientHandler(Socket socket) { this.socket = socket; }
    public void setPartner(ClientHandler partner) { this.partner = partner; }
    public void setMark(String mark) { this.mark = mark; }
    public void setMyTurn(boolean myTurn) { this.myTurn = myTurn; }

    @Override
    public void run() {
        try {
            outStream = new ObjectOutputStream(socket.getOutputStream());
            inStream = new ObjectInputStream(socket.getInputStream());

            while (true) {
                try {
                    GameMessage msg = (GameMessage) inStream.readObject();
                    handleMessage(msg);
                } catch (ClassNotFoundException e) {
                    System.err.println("Client đã gửi một đối tượng không xác định.");
                } catch (EOFException e) {
                    // --- LOGIC MỚI KHI CLIENT NGẮT KẾT NỐI (NHẤN "KHÔNG" HOẶC TẮT) ---
                    System.out.println("Client đã ngắt kết nối: " + socket.getInetAddress());
                    
                    if (partner != null) { 
                        // Bất kể lý do gì, nếu tôi thoát và tôi có đối thủ,
                        // hãy BÁO cho đối thủ của tôi phải thoát.
                        partner.sendMessage(new GameMessage(GameMessage.MSG_PARTNER_QUIT, null));
                        partner.partner = null; // Hủy ghép cặp
                    }
                    
                    if (ServerApp.waitingPlayer == this) {
                        // Nếu tôi đang ở hàng chờ và tôi thoát
                        ServerApp.waitingPlayer = null;
                    }
                    break; // Thoát khỏi vòng lặp run()
                    // --- KẾT THÚC LOGIC MỚI ---
                }
            }
        } catch (IOException e) {
            System.out.println("Lỗi I/O với Client: " + e.getMessage());
        } finally {
            closeConnections();
        }
    }
    
    private void handleMessage(GameMessage msg) {
        
        switch (msg.getType()) {
            case GameMessage.MSG_MOVE:
                if (myTurn && partner != null) {
                    String data = (String) msg.getData();
                    String[] parts = data.split(",");
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);

                    if (board[x][y].equals("")) {
                        board[x][y] = this.mark;
                        String updateData = x + "," + y + "," + this.mark;
                        GameMessage updateMsg = new GameMessage(GameMessage.MSG_UPDATE_BOARD, updateData);
                        this.sendMessage(updateMsg);
                        partner.sendMessage(updateMsg);
                        
                        if (checkWin(x, y)) {
                            this.sendMessage(new GameMessage(GameMessage.MSG_WIN, "Bạn thắng!"));
                            partner.sendMessage(new GameMessage(GameMessage.MSG_LOSE, "Bạn thua!"));
                            this.myTurn = false;
                            partner.setMyTurn(false); 
                        } else {
                            this.myTurn = false;
                            partner.setMyTurn(true);
                        }
                    } else {
                        this.sendMessage(new GameMessage(GameMessage.MSG_CHAT, "Nước đi không hợp lệ!"));
                    }
                } else {
                    this.sendMessage(new GameMessage(GameMessage.MSG_CHAT, "Chưa đến lượt của bạn!"));
                }
                break;
            
            case GameMessage.MSG_CHAT:
                if (partner != null) { partner.sendMessage(msg); }
                break;

            case GameMessage.MSG_PLAY_AGAIN_REQUEST:
                this.agreedToPlayAgain = true;
                
                if (partner != null && partner.agreedToPlayAgain) {
                    // Cả hai đã đồng ý! Reset game
                    // (Logic này vẫn giữ nguyên)
                    
                    if (this.mark.equals("X")) {
                        this.setMark("O"); this.setMyTurn(false);
                        partner.setMark("X"); partner.setMyTurn(true);
                    } else {
                        this.setMark("X"); this.setMyTurn(true);
                        partner.setMark("O"); partner.setMyTurn(false);
                    }
                    
                    this.agreedToPlayAgain = false;
                    partner.agreedToPlayAgain = false;
                    
                    GameMessage resetMsg = new GameMessage(GameMessage.MSG_GAME_RESET, null);
                    this.sendMessage(resetMsg);
                    partner.sendMessage(resetMsg);
                    
                    // Logic reset bàn cờ logic (static) phải được gọi TỪ ServerApp
                    // khi 2 người chơi MỚI được ghép
                    // À không, ở đây 2 người cũ chơi lại -> phải reset
                    clearStaticBoard(); // <--- THÊM DÒNG NÀY ĐỂ RESET LOGIC
                    
                    if (this.myTurn) {
                        this.sendMessage(new GameMessage(GameMessage.MSG_CHAT, "Chơi lại! Bạn là X. Bạn đi trước."));
                        partner.sendMessage(new GameMessage(GameMessage.MSG_CHAT, "Chơi lại! Bạn là O. Chờ đối thủ đi."));
                    } else {
                        this.sendMessage(new GameMessage(GameMessage.MSG_CHAT, "Chơi lại! Bạn là O. Chờ đối thủ đi."));
                        partner.sendMessage(new GameMessage(GameMessage.MSG_CHAT, "Chơi lại! Bạn là X. Bạn đi trước."));
                    }
                    
                } else if (partner != null) {
                    // Mình đã đồng ý, chờ đối thủ
                    this.sendMessage(new GameMessage(GameMessage.MSG_CHAT, "Bạn đã đồng ý chơi lại. Đang chờ đối thủ..."));
                    partner.sendMessage(new GameMessage(GameMessage.MSG_CHAT, "Đối thủ muốn chơi lại! Bạn có đồng ý không?"));
                
                } else if (partner == null) {
                    // Lỗi: Đối thủ đã thoát rồi, không thể nhấn chơi lại
                    // (Logic này sẽ không bao giờ xảy ra nếu làm đúng)
                }
                break;
        }
    } 

    // Hàm checkWin (giữ nguyên, không thay đổi)
    private boolean checkWin(int x, int y) {
        if (board[x][y].equals("")) return false;
        String mark = this.mark;
        int count = 0;
        for (int j = 0; j < Constants.BOARD_SIZE; j++) {
            if (board[x][j].equals(mark)) { count++; if (count >= 5) return true; } else { count = 0; }
        }
        count = 0;
        for (int i = 0; i < Constants.BOARD_SIZE; i++) {
            if (board[i][y].equals(mark)) { count++; if (count >= 5) return true; } else { count = 0; }
        }
        count = 0;
        int iStart = x, jStart = y;
        while (iStart > 0 && jStart > 0) { iStart--; jStart--; }
        while (iStart < Constants.BOARD_SIZE && jStart < Constants.BOARD_SIZE) {
            if (board[iStart][jStart].equals(mark)) { count++; if (count >= 5) return true; } else { count = 0; }
            iStart++; jStart++;
        }
        count = 0;
        iStart = x; jStart = y;
        while (iStart > 0 && jStart < Constants.BOARD_SIZE - 1) { iStart--; jStart++; }
        while (iStart < Constants.BOARD_SIZE && jStart >= 0) {
            if (board[iStart][jStart].equals(mark)) { count++; if (count >= 5) return true; } else { count = 0; }
            iStart++; jStart--;
        }
        return false;
    } 
    
    public void sendMessage(GameMessage msg) {
        if (outStream == null) return;
        try {
            outStream.writeObject(msg);
            outStream.flush();
        } catch (IOException e) { e.printStackTrace(); }
    }
    
    private void closeConnections() {
        try {
            if (inStream != null) inStream.close();
            if (outStream != null) outStream.close();
            if (socket != null) socket.close();
        } catch (IOException e) { e.printStackTrace(); }
    }
}