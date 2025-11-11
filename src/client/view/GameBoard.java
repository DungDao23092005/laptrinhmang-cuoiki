package client.view;

import client.Log;                       
import shared.Constants;
import shared.GameMessage;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.logging.Level;

public class GameBoard extends JFrame {

    private final ObjectOutputStream outStream;
    private JTextArea logArea;
    private JButton[][] boardButtons;

    // Heartbeat (tuỳ chọn)
    private javax.swing.Timer hbTimer;

    public GameBoard(ObjectOutputStream outStream) {
        this.outStream = outStream;

        setTitle("Game Cờ Caro");
        setSize(800, 900);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Bàn cờ
        JPanel mainBoardPanel = new JPanel(new GridLayout(Constants.BOARD_SIZE, Constants.BOARD_SIZE));
        boardButtons = new JButton[Constants.BOARD_SIZE][Constants.BOARD_SIZE];

        for (int i = 0; i < Constants.BOARD_SIZE; i++) {
            for (int j = 0; j < Constants.BOARD_SIZE; j++) {
                final int x = i, y = j;
                JButton button = new JButton();
                button.setPreferredSize(new Dimension(40, 40));
                button.setBackground(Color.WHITE);
                button.setMargin(new Insets(0, 0, 0, 0));
                button.setFocusPainted(false);
                button.addActionListener(e -> sendMove(x, y));
                mainBoardPanel.add(button);
                boardButtons[i][j] = button;
            }
        }

        // Log
        logArea = new JTextArea(5, 30);
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        add(mainBoardPanel, BorderLayout.CENTER);
        add(logScrollPane, BorderLayout.SOUTH);
        // ⛔ ĐÃ XOÁ nút "Gửi nước đi (Test) - Bỏ qua nút này"

        // (Tuỳ chọn) Heartbeat PING 5s/lần
        hbTimer = new javax.swing.Timer(5000, e -> {
            try {
                outStream.writeObject(new GameMessage(GameMessage.MSG_PING, null));
                outStream.flush();
                Log.L.finest("SEND PING");
            } catch (IOException ex) {
                Log.L.log(Level.FINE, "Heartbeat send failed", ex);
            }
        });
        hbTimer.start();

        // Dừng heartbeat khi đóng cửa sổ để tránh timer chạy ngầm
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                if (hbTimer != null) hbTimer.stop();
            }
        });
    }

    private void appendLog(String s) {
        logArea.append(s);
        if (!s.endsWith("\n")) logArea.append("\n");
        // auto-scroll
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void sendMove(int x, int y) {
        try {
            String moveData = x + "," + y;
            outStream.writeObject(new GameMessage(GameMessage.MSG_MOVE, moveData));
            outStream.flush();
            Log.L.fine("SEND MOVE: " + moveData);
            appendLog("Đã gửi nước đi: [" + x + "," + y + "]");
        } catch (Exception e) {
            Log.L.log(Level.WARNING, "Lỗi khi gửi nước đi", e);
            appendLog("Lỗi khi gửi nước đi: " + e.getMessage());
        }
    }

    // Hàm này được ServerListener gọi (qua SwingUtilities.invokeLater)
    public void handleServerMessage(GameMessage msg) {
        switch (msg.getType()) {
            case GameMessage.MSG_CHAT:
                appendLog(String.valueOf(msg.getData()));
                break;

            case GameMessage.MSG_UPDATE_BOARD: {
                String data = (String) msg.getData();
                String[] parts = data.split(",");
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                String mark = parts[2];
                Log.L.fine("UPDATE_BOARD <- " + data);
                updateBoard(x, y, mark);
                break;
            }

            case GameMessage.MSG_WIN:
                appendLog("BẠN THẮNG RỒI!");
                showPlayAgainDialog("Bạn thắng!");
                break;

            case GameMessage.MSG_LOSE:
                appendLog("Bạn đã thua.");
                showPlayAgainDialog("Bạn đã thua!");
                break;

            case GameMessage.MSG_GAME_RESET:
                appendLog("Trò chơi đã được reset. Chơi lại nào!");
                resetBoardUI();
                break;

            case GameMessage.MSG_FIND_NEW_OPPONENT:
                Log.L.info("Server yêu cầu tìm đối thủ mới.");
                appendLog("Đối thủ không muốn chơi lại. Đang tìm đối thủ mới...");
                resetBoardUI();
                setTitle("Game Cờ Caro - Đang chờ đối thủ mới...");
                break;

            case GameMessage.MSG_PARTNER_QUIT:
                Log.L.warning("Đối thủ đã thoát.");
                appendLog("Đối thủ đã thoát. Trò chơi kết thúc.");
                JOptionPane.showMessageDialog(
                        this,
                        "Đối thủ đã thoát. Trò chơi kết thúc.",
                        "Thông báo",
                        JOptionPane.INFORMATION_MESSAGE
                );
                if (hbTimer != null) hbTimer.stop();
                System.exit(0);
                break;

            default:
                // no-op
        }
    }

    public void updateBoard(int x, int y, String mark) {
        JButton button = boardButtons[x][y];
        button.setText(mark);
        button.setEnabled(false);
        button.setFont(new Font("Arial", Font.BOLD, 20));
        if ("X".equals(mark)) {
            button.setForeground(Color.BLUE);
        } else {
            button.setForeground(Color.RED);
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
                appendLog("Đã gửi yêu cầu chơi lại. Đang chờ đối thủ...");
            } catch (IOException e) {
                Log.L.log(Level.WARNING, "Lỗi khi gửi yêu cầu chơi lại", e);
                appendLog("Lỗi khi gửi yêu cầu chơi lại.");
            }
        } else {
            try {
                // Báo server: từ chối để đối thủ được re-queue
                outStream.writeObject(new GameMessage(GameMessage.MSG_PLAY_AGAIN_DECLINE, null));
                outStream.flush();
                Log.L.info("Gửi DECLINE và thoát ứng dụng.");
            } catch (IOException e) {
                Log.L.log(Level.FINE, "Gửi DECLINE thất bại khi thoát", e);
            }
            if (hbTimer != null) hbTimer.stop();
            System.exit(0);
        }
    }
}
