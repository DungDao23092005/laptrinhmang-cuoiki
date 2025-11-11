package shared;

import java.io.Serializable;

/**
 * Lớp này định nghĩa đối tượng tin nhắn
 * mà Client và Server sẽ trao đổi.
 * Phải implement Serializable.
 */
public class GameMessage implements Serializable {
    
    // Định nghĩa các loại tin nhắn
    public static final String MSG_PLAY_AGAIN_REQUEST = "PLAY_AGAIN";
    public static final String MSG_GAME_RESET = "GAME_RESET";
    public static final String MSG_PARTNER_QUIT = "PARTNER_QUIT";
    public static final String MSG_CONNECT = "CONNECT";
    public static final String MSG_MOVE = "MOVE";
    public static final String MSG_UPDATE_BOARD = "UPDATE_BOARD";
    public static final String MSG_WIN = "WIN";
    public static final String MSG_LOSE = "LOSE";
    public static final String MSG_CHAT = "CHAT";

    private String type; // Loại tin nhắn (dùng các hằng số ở trên)
    private Object data; // Dữ liệu đi kèm (ví dụ: tọa độ, nội dung chat)

    public GameMessage(String type, Object data) {
        this.type = type;
        this.data = data;
    }

    public String getType() {
        return type;
    }

    public Object getData() {
        return data;
    }
}