import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class XRPCSwingDemo extends JFrame {
    private final JTextField ipField;
    private final JTextField portField;
    private XRPCConnection xrpcConnection;
    private String lastIp = "";
    private int lastPort = -1;

    public XRPCSwingDemo() {
        setTitle("Xbox 360 RTM Java DEMO");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);

        JPanel connPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        connPanel.add(new JLabel("Xbox IP:"));
        ipField = new JTextField("10.0.0.17", 13);
        connPanel.add(ipField);
        connPanel.add(new JLabel("Port:"));
        portField = new JTextField("730", 5);
        connPanel.add(portField);

        JTabbedPane tabs = new JTabbedPane();

        JPanel infoPanel = new JPanel(new BorderLayout());
        JTextArea infoArea = new JTextArea(18, 60);
        infoArea.setEditable(false);
        infoPanel.add(connPanel, BorderLayout.NORTH);
        infoPanel.add(new JScrollPane(infoArea), BorderLayout.CENTER);
        JButton refreshInfo = new JButton("Refresh Info");
        refreshInfo.addActionListener(e -> runInThread(() -> fetchConsoleInfo(infoArea)));
        infoPanel.add(refreshInfo, BorderLayout.SOUTH);
        tabs.addTab("Console Info", infoPanel);

        JPanel notifyPanel = new JPanel();
        notifyPanel.setLayout(new BoxLayout(notifyPanel, BoxLayout.Y_AXIS));
        JPanel notifyInput = new JPanel(new FlowLayout(FlowLayout.LEFT));
        notifyInput.add(new JLabel("Message:"));
        JTextField notifyField = new JTextField("Made by Xeon", 22);
        notifyInput.add(notifyField);
        notifyInput.add(new JLabel("Logo:"));
        JComboBox<XNotifyLogo> logoBox = new JComboBox<>(XNotifyLogo.values());
        notifyInput.add(logoBox);
        JButton notifyBtn = new JButton("Send Notification");
        notifyInput.add(notifyBtn);
        notifyPanel.add(notifyInput);
        JTextArea notifyOutput = new JTextArea(6, 60);
        notifyOutput.setEditable(false);
        notifyPanel.add(new JScrollPane(notifyOutput));
        notifyBtn.addActionListener(e -> runInThread(() ->
            sendXNotify(notifyField.getText(), (XNotifyLogo) logoBox.getSelectedItem(), notifyOutput)
        ));
        tabs.addTab("XNotify", notifyPanel);

        JPanel memPanel = new JPanel();
        memPanel.setLayout(new BoxLayout(memPanel, BoxLayout.Y_AXIS));
        JPanel memInput = new JPanel(new FlowLayout(FlowLayout.LEFT));
        memInput.add(new JLabel("Address (hex): 0x"));
        JTextField memAddr = new JTextField(8);
        memInput.add(memAddr);
        memInput.add(new JLabel("Length:"));
        JTextField memLen = new JTextField("16", 4);
        memInput.add(memLen);
        memInput.add(new JLabel("Type:"));
        JComboBox<String> memType = new JComboBox<>(new String[]{"Hex", "ASCII", "Int32", "Float"});
        memInput.add(memType);
        JButton memRead = new JButton("Read");
        memInput.add(memRead);
        memInput.add(new JLabel("Set Value (hex):"));
        JTextField memVal = new JTextField(16);
        memInput.add(memVal);
        JButton memSet = new JButton("Set");
        memInput.add(memSet);
        memPanel.add(memInput);
        JTextArea memOutput = new JTextArea(10, 60);
        memOutput.setEditable(false);
        memPanel.add(new JScrollPane(memOutput));

        memRead.addActionListener(e -> runInThread(() -> {
            try {
                String addrStr = memAddr.getText().replaceAll("[^0-9a-fA-F]", "");
                String lenStr  = memLen.getText().replaceAll("[^0-9]", "");
                if (addrStr.isEmpty() || lenStr.isEmpty()) {
                    memOutput.append("Invalid address or length.\n");
                    return;
                }
                long addr = Long.parseLong(addrStr, 16);
                int len  = Integer.parseInt(lenStr);
                String cmd = "getmem addr=0x" + Long.toHexString(addr) + " length=" + len;
                String resp = getConnection().send(cmd);
                String data = extractDataField(resp);
                if (data == null) {
                    memOutput.append("Error or empty data: " + resp + "\n");
                    return;
                }
                byte[] bytes = hexStringToByteArray(data);
                String type = (String) memType.getSelectedItem();
                memOutput.append("Memory at 0x" + addrStr + " (" + type + "):\n");
                switch (type) {
                    case "Hex":
                        memOutput.append(bytesToHexDisplay(bytes) + "\n");
                        break;
                    case "ASCII":
                        memOutput.append(new String(bytes).replaceAll("[^\\x20-\\x7E]", ".") + "\n");
                        break;
                    case "Int32":
                        for (int i = 0; i + 3 < bytes.length; i += 4)
                            memOutput.append(String.format("0x%08X ", toInt32(bytes, i)));
                        memOutput.append("\n");
                        break;
                    case "Float":
                        for (int i = 0; i + 3 < bytes.length; i += 4)
                            memOutput.append(String.format("%.6f ", toFloat(bytes, i)));
                        memOutput.append("\n");
                        break;
                }
            } catch (Exception ex) {
                memOutput.append("Error: " + ex.getMessage() + "\n");
            }
        }));

        memSet.addActionListener(e -> runInThread(() -> {
            try {
                String addrStr = memAddr.getText().replaceAll("[^0-9a-fA-F]", "");
                String valStr  = memVal.getText().replaceAll("[^0-9a-fA-F]", "");
                if (addrStr.isEmpty() || valStr.isEmpty() || valStr.length() % 2 != 0) {
                    memOutput.append("Invalid address or value format.\n");
                    return;
                }
                long addr = Long.parseLong(addrStr, 16);
                String cmd = "setmem addr=0x" + Long.toHexString(addr) + " data=" + valStr;
                String resp = getConnection().send(cmd);
                memOutput.append("SetMemory response: " + resp + "\n");
            } catch (Exception ex) {
                memOutput.append("Error: " + ex.getMessage() + "\n");
            }
        }));
        tabs.addTab("Memory", memPanel);

        JPanel powerPanel = new JPanel();
        powerPanel.setLayout(new BoxLayout(powerPanel, BoxLayout.Y_AXIS));
        JPanel powerBtns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton softRebootBtn = new JButton("Reload Title");
        JButton shutdownBtn   = new JButton("Shutdown Console");
        powerBtns.add(softRebootBtn);
        powerBtns.add(shutdownBtn);
        powerPanel.add(powerBtns);
        JTextArea powerOutput = new JTextArea(6, 60);
        powerOutput.setEditable(false);
        powerPanel.add(new JScrollPane(powerOutput));

        softRebootBtn.addActionListener(e -> runInThread(() -> {
            try {
                String resp = getConnection().send("magicboot title=0");
                powerOutput.append("Soft reboot response: " + resp + "\n");
            } catch (Exception ex) {
                powerOutput.append("Error: " + ex.getMessage() + "\n");
            }
        }));
        shutdownBtn.addActionListener(e -> runInThread(() -> {
            try {
                String resp = getConnection().send(
                  "consolefeatures ver=2 type=11 params=\"A\\0\\A\\0\\\""
                );
                powerOutput.append("Shutdown response: " + resp + "\n");
            } catch (Exception ex) {
                powerOutput.append("Error: " + ex.getMessage() + "\n");
            }
        }));
        tabs.addTab("Power", powerPanel);

        add(tabs);
        setLocationRelativeTo(null);
        setVisible(true);
    }

	private void fetchConsoleInfo(JTextArea area) {
		String ip   = getIp();
		int    port = getPort();

		area.setText("");

		try (XRPCConnection conn = new XRPCConnection(ip, port)) {
			String[] labels = {
				"Kernel Version: ",
				"Console Type:    ",
				"CPU Key:         ",
				"Current Title ID:",
				"CPU Temp:        ",
				"GPU Temp:        "
			};
			String[] cmds = {
				"consolefeatures ver=2 type=13 params=\"A\\0\\A\\0\\\"",
				"consolefeatures ver=2 type=17 params=\"A\\0\\A\\0\\\"",
				"consolefeatures ver=2 type=10 params=\"A\\0\\A\\0\\\"",
				"consolefeatures ver=2 type=16 params=\"A\\0\\A\\0\\\"",
				"consolefeatures ver=2 type=15 params=\"A\\0\\A\\1\\0\\\"",
				"consolefeatures ver=2 type=15 params=\"A\\0\\A\\1\\1\\\""
			};

			for (int i = 0; i < labels.length; i++) {
				String resp = conn.send(cmds[i]);
				String val  = extractValue(resp);

				if (labels[i].trim().endsWith("Temp:")) {
					try {
						int t = Integer.parseInt(val, 16);
						area.append(labels[i] + t + " C\n");
					} catch (NumberFormatException e) {
						area.append(labels[i] + val + " C\n");
					}
				} else {
					area.append(labels[i] + val + "\n");
				}
			}
		} catch (IOException ex) {
			area.append("Error fetching console info: " + ex.getMessage() + "\n");
		}
	}

    private void sendXNotify(String message, XNotifyLogo logo, JTextArea out) {
        try {
            String hexMsg = toHex(message, "Cp1252").toUpperCase();
            int len      = message.length();
            int logoId   = logo.ordinal();
            String params = "A\\0\\A\\2\\2/" + len
                          + "\\" + hexMsg
                          + "\\1\\" + logoId + "\\\"";
            String cmd   = "consolefeatures ver=2 type=12 params=\"" + params + "\"";
            String resp  = getConnection().send(cmd);
            out.append("XNotify response: " + resp + "\n");
        } catch (Exception ex) {
            out.append("Error: " + ex.getMessage() + "\n");
        }
    }

    private synchronized XRPCConnection getConnection() throws IOException {
        String ip = getIp();
        int port  = getPort();
        if (xrpcConnection == null
         || !xrpcConnection.isConnected()
         || !ip.equals(lastIp)
         || port != lastPort) {
            if (xrpcConnection != null) xrpcConnection.close();
            xrpcConnection = new XRPCConnection(ip, port);
            lastIp   = ip;
            lastPort = port;
        }
        return xrpcConnection;
    }

    private String getIp() {
        return ipField.getText().trim();
    }

    private int getPort() {
        try {
            return Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            return 730;
        }
    }

    private void runInThread(Runnable r) {
        new Thread(r).start();
    }

    private static String extractValue(String line) {
        int i = line.indexOf(" ");
        return (i >= 0)
             ? line.substring(i+1).replace("value=", "").trim()
             : line;
    }

    private static String extractDataField(String resp) {
        int i = resp.indexOf("data=");
        if (i < 0) return null;
        int e = resp.indexOf(" ", i+5);
        return resp.substring(i+5, e<0?resp.length():e).trim();
    }

    private static String bytesToHexDisplay(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            sb.append(String.format("%02X ", b[i]));
            if ((i+1) % 16 == 0) sb.append("\n");
        }
        return sb.toString();
    }

    private static int toInt32(byte[] b, int o) {
        return ((b[o+3]&0xFF)<<24)
             | ((b[o+2]&0xFF)<<16)
             | ((b[o+1]&0xFF)<<8)
             |  (b[o]&0xFF);
    }

    private static float toFloat(byte[] b, int o) {
        return Float.intBitsToFloat(toInt32(b,o));
    }

    private static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] d = new byte[len/2];
        for (int i = 0; i < len; i += 2) {
            d[i/2] = (byte)(
              Character.digit(s.charAt(i),   16) << 4 |
              Character.digit(s.charAt(i+1), 16)
            );
        }
        return d;
    }

    private static String parseDirListing(String resp) {
        StringBuilder sb = new StringBuilder();
        for (String line : resp.split("\n")) {
            if (line.contains("id=")) sb.append(line.trim()).append("\n");
        }
        return sb.length()==0 ? resp : sb.toString();
    }

    public static String toHex(String arg, String encoding) throws UnsupportedEncodingException {
        byte[] bytes = arg.getBytes(encoding);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    public enum XNotifyLogo {
        XBOX_LOGO, NEW_MESSAGE_LOGO, FRIEND_REQUEST_LOGO, NEW_MESSAGE,
        FLASHING_XBOX_LOGO, GAMERTAG_SENT_YOU_A_MESSAGE, GAMERTAG_SINGED_OUT,
        GAMERTAG_SIGNEDIN, GAMERTAG_SIGNED_INTO_XBOX_LIVE, GAMERTAG_SIGNED_IN_OFFLINE,
        GAMERTAG_WANTS_TO_CHAT, DISCONNECTED_FROM_XBOX_LIVE, DOWNLOAD,
        FLASHING_MUSIC_SYMBOL, FLASHING_HAPPY_FACE, FLASHING_FROWNING_FACE,
        FLASHING_DOUBLE_SIDED_HAMMER, GAMERTAG_WANTS_TO_CHAT_2,
        PLEASE_REINSERT_MEMORY_UNIT, PLEASE_RECONNECT_CONTROLLERM,
        GAMERTAG_HAS_JOINED_CHAT, GAMERTAG_HAS_LEFT_CHAT, GAME_INVITE_SENT,
        FLASH_LOGO, PAGE_SENT_TO, FOUR_2, FOUR_3, ACHIEVEMENT_UNLOCKED,
        FOUR_9, GAMERTAG_WANTS_TO_TALK_IN_VIDEO_KINECT, VIDEO_CHAT_INVITE_SENT,
        READY_TO_PLAY, CANT_DOWNLOAD_X, DOWNLOAD_STOPPED_FOR_X,
        FLASHING_XBOX_CONSOLE, X_SENT_YOU_A_GAME_MESSAGE, DEVICE_FULL,
        FOUR_7, FLASHING_CHAT_ICON, ACHIEVEMENTS_UNLOCKED, X_HAS_SENT_YOU_A_NUDGE,
        MESSENGER_DISCONNECTED, BLANK, CANT_SIGN_IN_MESSENGER,
        MISSED_MESSENGER_CONVERSATION, FAMILY_TIMER_X_TIME_REMAINING,
        DISCONNECTED_XBOX_LIVE_11_MINUTES_REMAINING, KINECT_HEALTH_EFFECTS,
        FOUR_5, GAMERTAG_WANTS_YOU_TO_JOIN_AN_XBOX_LIVE_PARTY,
        PARTY_INVITE_SENT, GAME_INVITE_SENT_TO_XBOX_LIVE_PARTY,
        KICKED_FROM_XBOX_LIVE_PARTY, NULLED, DISCONNECTED_XBOX_LIVE_PARTY,
        DOWNLOADED, CANT_CONNECT_XBL_PARTY, GAMERTAG_HAS_JOINED_XBL_PARTY,
        GAMERTAG_HAS_LEFT_XBL_PARTY, GAMER_PICTURE_UNLOCKED,
        AVATAR_AWARD_UNLOCKED, JOINED_XBL_PARTY,
        PLEASE_REINSERT_USB_STORAGE_DEVICE, PLAYER_MUTED, PLAYER_UNMUTED,
        FLASHING_CHAT_SYMBOL, UPDATING
    }

	static class XRPCConnection implements Closeable {
		private final String ip;
		private final int port;
		private Socket             socket;
		private BufferedReader     in;
		private BufferedWriter     out;

		public XRPCConnection(String ip, int port) throws IOException {
			this.ip   = ip;
			this.port = port;
			connect();
		}

		private void connect() throws IOException {
			socket = new Socket(ip, port);
			socket.setSoTimeout(5000);
			in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

			try { in.readLine(); } catch (IOException ignored) {}
		}

		public synchronized String send(String cmd) throws IOException {
			if (socket == null || socket.isClosed() || !socket.isConnected()) {
				connect();
			}

			if (!cmd.endsWith("\r\n")) {
				cmd = cmd.replaceAll("\n$", "") + "\r\n";
			}

			out.write(cmd);
			out.flush();

			String first;
			try {
				first = in.readLine();
			} catch (SocketTimeoutException e) {
				close();
				throw e;
			}
			if (first == null) {
				close();
				throw new EOFException("Connection closed by XBDM");
			}

			StringBuilder sb = new StringBuilder(first);
			String lower = first.toLowerCase();

			if (lower.contains("response follows") || lower.contains("send binary data")) {
				String line;
				while ((line = in.readLine()) != null) {
					if (line.equals(".")) {
						break;
					}
					sb.append("\n").append(line);
				}
			}

			return sb.toString();
		}

		@Override
		public synchronized void close() throws IOException {
			if (in     != null) in.close();
			if (out    != null) out.close();
			if (socket != null && !socket.isClosed()) socket.close();
			socket = null;
		}

		public synchronized boolean isConnected() {
			return socket != null && socket.isConnected() && !socket.isClosed();
		}
	}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(XRPCSwingDemo::new);
    }
}
