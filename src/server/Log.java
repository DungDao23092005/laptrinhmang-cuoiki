package server;

import java.io.IOException;
import java.nio.file.*;
import java.util.logging.*;

public final class Log {
    public static final Logger L = Logger.getLogger("CaroServer");

    static {
        try {
            L.setUseParentHandlers(false);
            Level level = Level.INFO;
            L.setLevel(level);

            Path dir = Paths.get("logs");
            if (!Files.exists(dir)) Files.createDirectories(dir);

            ConsoleHandler ch = new ConsoleHandler();
            ch.setLevel(level);
            ch.setEncoding("UTF-8");
            ch.setFormatter(new PrettyFormatter());
            L.addHandler(ch);

            FileHandler fh = new FileHandler("logs/server-%g.log", 1_000_000, 5, true);
            fh.setLevel(level);
            fh.setEncoding("UTF-8");
            fh.setFormatter(new PrettyFormatter());
            L.addHandler(fh);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Log() {}

    /** Formatter: strip toàn bộ dấu để log ra terminal luôn “không dấu” */
    static class PrettyFormatter extends Formatter {
        private static String noMark(String s) {
            if (s == null) return null;
            String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
            n = n.replaceAll("\\p{M}+", "");              // bỏ dấu tổ hợp
            n = n.replace('Đ','D').replace('đ','d');      // Đ/đ
            return n;
        }
        @Override public String format(LogRecord r) {
            String ts = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
                    .format(new java.util.Date(r.getMillis()));
            String thread = noMark(Thread.currentThread().getName());
            String src    = noMark(r.getLoggerName());
            String msg    = noMark(formatMessage(r));
            StringBuilder sb = new StringBuilder()
                .append(ts).append(" [").append(thread).append("] ")
                .append(r.getLevel().getName()).append(" ")
                .append(src).append(" - ").append(msg).append("\n");
            if (r.getThrown() != null) {
                java.io.StringWriter sw = new java.io.StringWriter();
                java.io.PrintWriter pw = new java.io.PrintWriter(sw);
                r.getThrown().printStackTrace(pw);
                sb.append(sw);
            }
            return sb.toString();
        }
    }
}
