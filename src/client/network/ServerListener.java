package client.network;

import client.Log;
import client.view.GameBoard;
import shared.GameMessage;

import javax.swing.SwingUtilities;
import java.io.EOFException;
import java.io.ObjectInputStream;
import java.util.logging.Level;

public class ServerListener implements Runnable {

    private final ObjectInputStream inStream;
    private final GameBoard gameBoard;

    public ServerListener(ObjectInputStream inStream, GameBoard gameBoard) {
        this.inStream = inStream;
        this.gameBoard = gameBoard;
    }

    @Override
    public void run() {
        try {
            while (true) {
                GameMessage msg = (GameMessage) inStream.readObject();
                Log.L.fine("Recv: " + msg);

                SwingUtilities.invokeLater(() -> gameBoard.handleServerMessage(msg));
            }
        } catch (EOFException e) {
            Log.L.warning("Server đã đóng kết nối (EOF).");
        } catch (Exception e) {
            Log.L.log(Level.SEVERE, "Lỗi lắng nghe Server", e);
        }
    }
}
