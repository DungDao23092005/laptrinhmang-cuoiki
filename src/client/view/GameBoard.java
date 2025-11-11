package client.view;

import shared.Constants;
import shared.GameMessage;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.ObjectOutputStream;

public class GameBoard extends JFrame {

    private ObjectOutputStream outStream; 
    private JTextArea logArea; 
    private JButton[][] boardButtons; 

    public GameBoard(ObjectOutputStream outStream) {
        this.outStream = outStream;
        
        setTitle("Game Cờ Caro");
        setSize(800, 900); 
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel mainBoardPanel = new JPanel();
        mainBoardPanel.setLayout(new GridLayout(Constants.BOARD_SIZE, Constants.BOARD_SIZE));

        boardButtons = new JButton[Constants.BOARD_SIZE][Constants.BOARD_SIZE];

        for (int i = 0; i < Constants.BOARD_SIZE; i++) {
            for (int j = 0; j < Constants.BOARD_SIZE; j++) {
                JButton button = new JButton();
                button.setPreferredSize(new Dimension(40, 40)); 
                button.setBackground(Color.WHITE);
                button.setMargin(new Insets(0, 0, 0, 0));
                
                final int x = i;
                final int y = j;
                
                button.addActionListener(e -> {
                    sendMove(x, y); 
                });

                mainBoardPanel.add(button);
                boardButtons[i][j] = button;
            }
        }

        logArea = new JTextArea(5, 30);
        logArea.setEditable(false);
        // >>>>> DÒNG NÀY ĐÃ ĐƯỢC SỬA <<<<<
        JScrollPane logScrollPane = new JScrollPane(logArea); 

        JButton testSendButton = new JButton("Gửi nước đi (Test) - Bỏ qua nút này");

        add(mainBoardPanel, BorderLayout.CENTER); 
        add(logScrollPane, BorderLayout.SOUTH); 
        add(testSendButton, BorderLayout.NORTH); 
    }
    
    private void sendMove(int x, int y) {
        try {
            String moveData = x + "," + y; 
            GameMessage msg = new GameMessage(GameMessage.MSG_MOVE, moveData);
            outStream.writeObject(msg);
            outStream.flush();
            logArea.append("Đã gửi nước đi: [" + x + "," + y + "]\n");
            
        } catch (Exception e) {
            e.printStackTrace();
            logArea.append("Lỗi khi gửi nước đi: " + e.getMessage() + "\n");
        }
    }

    // Hàm này được ServerListener gọi
    public void handleServerMessage(GameMessage msg) {
        
        switch (msg.getType()) {
            case GameMessage.MSG_CHAT:
                logArea.append(msg.getData() + "\n");
                break;
                
            case GameMessage.MSG_UPDATE_BOARD:
                String data = (String) msg.getData();
                String[] parts = data.split(",");
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                String mark = parts[2]; 
                updateBoard(x, y, mark);
                break;
                
            case GameMessage.MSG_WIN:
                logArea.append("BẠN THẮNG RỒI!\n");
                showPlayAgainDialog("Bạn thắng!");
                break;
                
            case GameMessage.MSG_LOSE:
                logArea.append("Bạn đã thua.\n");
                showPlayAgainDialog("Bạn đã thua!");
                break;
            
            case GameMessage.MSG_GAME_RESET:
                logArea.append("Trò chơi đã được reset. Chơi lại nào!\n");
                resetBoardUI();
                break;
            
            // --- LOGIC MỚI: BỊ SERVER ÉP THOÁT ---
            case GameMessage.MSG_PARTNER_QUIT:
                logArea.append("Đối thủ đã thoát. Trò chơi kết thúc.\n");
                JOptionPane.showMessageDialog(this, 
                    "Đối thủ đã thoát. Trò chơi kết thúc.", 
                    "Thông báo", 
                    JOptionPane.INFORMATION_MESSAGE);
                System.exit(0);
                break;
        }
    }
    
    public void updateBoard(int x, int y, String mark) {
        JButton button = boardButtons[x][y];
        button.setText(mark);
        button.setEnabled(false); 
        if (mark.equals("X")) {
            button.setForeground(Color.BLUE);
            button.setFont(new Font("Arial", Font.BOLD, 20));
        } else {
            button.setForeground(Color.RED);
            button.setFont(new Font("Arial", Font.BOLD, 20));
        }
    }
    
    public void resetBoardUI() {
        for (int i = 0; i < Constants.BOARD_SIZE; i++) {
            for (int j = 0; j < Constants.BOARD_SIZE; j++) {
                boardButtons[i][j].setText("");
                boardButtons[i][j].setEnabled(true);
                boardButtons[i][j].setBackground(Color.WHITE);
            }
        }
    }
    
    private void showPlayAgainDialog(String message) {
        int result = JOptionPane.showConfirmDialog(
            this, 
            message + "\nBạn có muốn chơi lại không?", 
            "Kết thúc ván", 
            JOptionPane.YES_NO_OPTION
        );
        
        if (result == JOptionPane.YES_OPTION) {
            try {
                outStream.writeObject(new GameMessage(GameMessage.MSG_PLAY_AGAIN_REQUEST, null));
                outStream.flush();
                logArea.append("Đã gửi yêu cầu chơi lại. Đang chờ đối thủ...\n");
            } catch (IOException e) {
                logArea.append("Lỗi khi gửi yêu cầu chơi lại.\n");
            }
        } else {
            // Người dùng nhấn "Không", chỉ cần đóng ứng dụng
            System.exit(0);
        }
    }
}