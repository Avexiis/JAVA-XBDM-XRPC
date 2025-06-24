import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.Socket;

public class XRPCSwingDemo extends JFrame {
    private JTextField ipField;
    private JTextField portField;
    private JTextArea outputArea;
    private JButton cpuKeyButton;
    private JButton notifyButton;
    private JTextField notifyField;
    private JComboBox<XNotifyLogo> logoBox;

    private XRPCConnection xrpcConnection;
    private String lastIp = "";
    private int lastPort = -1;

    public XRPCSwingDemo() {
        setTitle("Xbox 360 JRPC2/XRPC Demo");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel();
        topPanel.add(new JLabel("Xbox IP:"));
        ipField = new JTextField("10.0.0.17", 13);
        topPanel.add(ipField);

        topPanel.add(new JLabel("Port:"));
        portField = new JTextField("730", 5);
        topPanel.add(portField);

        cpuKeyButton = new JButton("Get CPU Key");
        notifyButton = new JButton("Send XNotify");
        notifyField = new JTextField("Hello from Java!", 18);

        topPanel.add(cpuKeyButton);

        topPanel.add(new JLabel("XNotify:"));
        topPanel.add(notifyField);

        topPanel.add(new JLabel("Logo:"));
        logoBox = new JComboBox<>(XNotifyLogo.values());
        topPanel.add(logoBox);

        topPanel.add(notifyButton);

        add(topPanel, BorderLayout.NORTH);

        outputArea = new JTextArea(12, 50);
        outputArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(outputArea);
        add(scrollPane, BorderLayout.CENTER);

        cpuKeyButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                sendGetCpuKey();
            }
        });

        notifyButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                sendConsoleFeaturesNotify();
            }
        });

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void sendGetCpuKey() {
        String ip = ipField.getText().trim();
        int port = getPort();
        runInThread(() -> {
            try {
                String cmd = "consolefeatures ver=2 type=10 params=\"A\\0\\A\\0\\\"\n";
                String response = getConnection(ip, port).sendRawCommand(cmd);
                if (response != null && (response.startsWith("200-") || response.startsWith("201-"))) {
                    String cpuKey = response.substring(response.indexOf(' ') + 1).trim();
                    printOutput("CPU Key Response:\n" + cpuKey);
                } else {
                    printOutput("CPU Key Error/Unexpected Response:\n" + response);
                }
            } catch (IOException ex) {
                printOutput("Error: " + ex.getMessage());
                disconnect();
            }
        });
    }

    private void sendConsoleFeaturesNotify() {
        String ip = ipField.getText().trim();
        int port = getPort();
        String message = notifyField.getText().trim();
        int logo = logoBox.getSelectedIndex();

        runInThread(() -> {
            try {
                String hexMessage = toHex(message, "Cp1252").toUpperCase();
                int len = message.length();
                String params = "A\\0\\A\\2\\2/" + len + "\\" + hexMessage + "\\1\\" + logo + "\\";
                String cmd = "consolefeatures ver=2 type=12 params=\"" + params + "\"\r\n";
                String response = getConnection(ip, port).sendRawCommand(cmd);
                printOutput("ConsoleFeatures Notify Response:\n" + response);
            } catch (IOException ex) {
                printOutput("Error: " + ex.getMessage());
                disconnect();
            }
        });
    }

    public static String toHex(String arg, String encoding) throws UnsupportedEncodingException {
        byte[] bytes = arg.getBytes(encoding);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private int getPort() {
        try {
            return Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException ex) {
            printOutput("Invalid port. Using 730.");
            return 730;
        }
    }

    private synchronized XRPCConnection getConnection(String ip, int port) throws IOException {
        if (xrpcConnection == null || !xrpcConnection.isConnected() ||
                !ip.equals(lastIp) || port != lastPort) {
            if (xrpcConnection != null) xrpcConnection.close();
            xrpcConnection = new XRPCConnection(ip, port);
            lastIp = ip;
            lastPort = port;
        }
        return xrpcConnection;
    }

    private synchronized void disconnect() {
        if (xrpcConnection != null) {
            try { xrpcConnection.close(); } catch (Exception ignored) {}
            xrpcConnection = null;
            lastIp = "";
            lastPort = -1;
        }
    }

    private void printOutput(String msg) {
        SwingUtilities.invokeLater(() -> {
            outputArea.append(msg + "\n");
            outputArea.setCaretPosition(outputArea.getDocument().getLength());
        });
    }

    private void runInThread(Runnable r) {
        new Thread(r).start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new XRPCSwingDemo());
    }

    public enum XNotifyLogo {
        XBOX_LOGO, NEW_MESSAGE_LOGO, FRIEND_REQUEST_LOGO, NEW_MESSAGE, FLASHING_XBOX_LOGO, GAMERTAG_SENT_YOU_A_MESSAGE,
        GAMERTAG_SINGED_OUT, GAMERTAG_SIGNEDIN, GAMERTAG_SIGNED_INTO_XBOX_LIVE, GAMERTAG_SIGNED_IN_OFFLINE,
        GAMERTAG_WANTS_TO_CHAT, DISCONNECTED_FROM_XBOX_LIVE, DOWNLOAD, FLASHING_MUSIC_SYMBOL, FLASHING_HAPPY_FACE,
        FLASHING_FROWNING_FACE, FLASHING_DOUBLE_SIDED_HAMMER, GAMERTAG_WANTS_TO_CHAT_2, PLEASE_REINSERT_MEMORY_UNIT,
        PLEASE_RECONNECT_CONTROLLERM, GAMERTAG_HAS_JOINED_CHAT, GAMERTAG_HAS_LEFT_CHAT, GAME_INVITE_SENT, FLASH_LOGO,
        PAGE_SENT_TO, FOUR_2, FOUR_3, ACHIEVEMENT_UNLOCKED, FOUR_9, GAMERTAG_WANTS_TO_TALK_IN_VIDEO_KINECT,
        VIDEO_CHAT_INVITE_SENT, READY_TO_PLAY, CANT_DOWNLOAD_X, DOWNLOAD_STOPPED_FOR_X, FLASHING_XBOX_CONSOLE,
        X_SENT_YOU_A_GAME_MESSAGE, DEVICE_FULL, FOUR_7, FLASHING_CHAT_ICON, ACHIEVEMENTS_UNLOCKED, X_HAS_SENT_YOU_A_NUDGE,
        MESSENGER_DISCONNECTED, BLANK, CANT_SIGN_IN_MESSENGER, MISSED_MESSENGER_CONVERSATION, FAMILY_TIMER_X_TIME_REMAINING,
        DISCONNECTED_XBOX_LIVE_11_MINUTES_REMAINING, KINECT_HEALTH_EFFECTS, FOUR_5, GAMERTAG_WANTS_YOU_TO_JOIN_AN_XBOX_LIVE_PARTY,
        PARTY_INVITE_SENT, GAME_INVITE_SENT_TO_XBOX_LIVE_PARTY, KICKED_FROM_XBOX_LIVE_PARTY, NULLED, DISCONNECTED_XBOX_LIVE_PARTY,
        DOWNLOADED, CANT_CONNECT_XBL_PARTY, GAMERTAG_HAS_JOINED_XBL_PARTY, GAMERTAG_HAS_LEFT_XBL_PARTY, GAMER_PICTURE_UNLOCKED,
        AVATAR_AWARD_UNLOCKED, JOINED_XBL_PARTY, PLEASE_REINSERT_USB_STORAGE_DEVICE, PLAYER_MUTED, PLAYER_UNMUTED,
        FLASHING_CHAT_SYMBOL, UPDATING
    }

    static class XRPCConnection implements Closeable {
        private String ip;
        private int port;
        private Socket socket;
        private BufferedReader in;
        private BufferedWriter out;

        public XRPCConnection(String ip, int port) throws IOException {
            this.ip = ip;
            this.port = port;
            connect();
        }

        public String getIp() {
            return ip;
        }

        private void connect() throws IOException {
            socket = new Socket(ip, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            if (port == 730 && in.ready()) {
                in.readLine();
            }
        }

        public synchronized String sendCommand(String command) throws IOException {
            if (socket == null || socket.isClosed() || !socket.isConnected()) {
                close();
                connect();
            }
            out.write(command + "\n");
            out.flush();
            return in.readLine();
        }

        public synchronized String sendRawCommand(String command) throws IOException {
            if (socket == null || socket.isClosed() || !socket.isConnected()) {
                close();
                connect();
            }
            out.write(command);
            out.flush();
            return in.readLine();
        }

        public boolean isConnected() {
            return socket != null && socket.isConnected() && !socket.isClosed();
        }

        @Override
        public void close() throws IOException {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        }
    }
}
