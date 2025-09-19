package client.tcp;
import java.util.List;
import java.util.ArrayList;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.*;


public class MovieClient extends JFrame {
    private static final String HOST = "localhost";
    private static final int PORT = 12345;
    private static final int WINDOW_WIDTH = 1100;
    private static final int WINDOW_HEIGHT = 720;
    private static final int ICON_SIZE = 32; // Kích thước icon ghế (pixel)

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    // Icon cho ghế đơn và ghế đôi
    private final Icon iconSeatSingle = resizeIcon(new ImageIcon(getClass().getResource("/client/tcp/icon/movie-seat.png")), ICON_SIZE, ICON_SIZE); // Ghế đơn (NORMAL, ví dụ: B1)
    private final Icon iconSeatCouple = resizeIcon(new ImageIcon(getClass().getResource("/client/tcp/icon/watch-movie.png")), ICON_SIZE, ICON_SIZE); // Ghế đôi (COUPLE, ví dụ: AF)

    private String currentUser = null;
    private final Map<String, JButton> seatButtons = new LinkedHashMap<>();
    private final Set<String> selectedSeats = new LinkedHashSet<>();
    private final Map<String, JCheckBox> snackChecks = new LinkedHashMap<>();
    private final Map<String, JSpinner> snackQuantities = new LinkedHashMap<>();

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel rootPanel = new JPanel(cardLayout);
    private final JTextField tfUsername = new JTextField(16);
    private final JPasswordField tfPassword = new JPasswordField(16);
    private final JComboBox<String> movieComboBox = new JComboBox<>();
    private final JLabel lbUser = new JLabel("User: -");
    private final JPanel seatGrid = new JPanel();
    private final JPanel snackPanel = new JPanel();
    private final DefaultListModel<String> orderModel = new DefaultListModel<>();
    private final JList<String> orderList = new JList<>(orderModel);
    private final JLabel statusBar = new JLabel("Ready.");

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MovieClient().setVisible(true));
    }

    public MovieClient() {
        setTitle("🎬 Movie Booking Client");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        setLocationRelativeTo(null);

        connectServer();
        buildLoginPanel();
        buildMainPanel();

        add(rootPanel, BorderLayout.CENTER);
        add(createStatusBar(), BorderLayout.SOUTH);
        cardLayout.show(rootPanel, "LOGIN");
    }

    // Hàm resize icon để đảm bảo kích thước phù hợp
    private Icon resizeIcon(ImageIcon icon, int width, int height) {
        if (icon == null) return null;
        Image img = icon.getImage();
        Image resizedImg = img.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        return new ImageIcon(resizedImg);
    }

    private void connectServer() {
        try {
            socket = new Socket(HOST, PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            updateStatus("Connected to server.");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Cannot connect to server: " + e.getMessage());
            System.exit(0);
        }
    }

    private void buildLoginPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(40, 40, 40, 40));
        panel.setBackground(new Color(240, 248, 255)); // Màu nền xanh nhạt

        JLabel titleLabel = new JLabel("Movie Booking System");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 28));
        titleLabel.setForeground(new Color(0, 128, 0)); // Màu xanh lá
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(10, 10, 10, 10);
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.CENTER;
        panel.add(titleLabel, c);

        c.gridwidth = 1;
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0; c.gridy = 1; panel.add(new JLabel("Username:"), c);
        c.gridx = 1; panel.add(tfUsername, c);
        c.gridx = 0; c.gridy = 2; panel.add(new JLabel("Password:"), c);
        c.gridx = 1; panel.add(tfPassword, c);

        JButton btLogin = new JButton("Login");
        btLogin.setBackground(new Color(0, 128, 0)); // Màu xanh lá
        btLogin.setForeground(Color.WHITE);
        JButton btReg = new JButton("Register");
        btReg.setBackground(new Color(0, 128, 0));
        btReg.setForeground(Color.WHITE);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        row.setBackground(panel.getBackground());
        row.add(btLogin); row.add(btReg);
        c.gridx = 1; c.gridy = 3; panel.add(row, c);

        btLogin.addActionListener(e -> login());
        btReg.addActionListener(e -> register());

        rootPanel.add(panel, "LOGIN");
    }

    private void buildMainPanel() {
        JPanel app = new JPanel(new BorderLayout());
        app.add(createTopBar(), BorderLayout.NORTH);

        JPanel seatsWrap = new JPanel(new BorderLayout());
        seatsWrap.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Thêm màn hình chiếu phim
        JLabel screen = new JLabel("SCREEN", SwingConstants.CENTER);
        screen.setFont(new Font("Arial", Font.BOLD, 24));
        screen.setForeground(Color.WHITE);
        screen.setBackground(Color.BLACK);
        screen.setOpaque(true);
        screen.setBorder(BorderFactory.createLineBorder(Color.GRAY, 2));
        screen.setPreferredSize(new Dimension(600, 80)); // Kích thước màn hình
        seatsWrap.add(screen, BorderLayout.NORTH);

        JLabel titleSeats = new JLabel("Sơ đồ ghế", SwingConstants.CENTER);
        titleSeats.setFont(titleSeats.getFont().deriveFont(Font.BOLD, 16));
        seatsWrap.add(titleSeats, BorderLayout.SOUTH);

        seatGrid.setBorder(new EmptyBorder(10, 10, 10, 10));
        seatsWrap.add(new JScrollPane(seatGrid), BorderLayout.CENTER);
        seatsWrap.add(createLegend(), BorderLayout.PAGE_END);

        snackPanel.setLayout(new BoxLayout(snackPanel, BoxLayout.Y_AXIS));
        snackPanel.setBorder(BorderFactory.createTitledBorder("Snacks & Drinks"));
        JScrollPane snackScroll = new JScrollPane(snackPanel);

        JPanel orderPane = new JPanel(new BorderLayout());
        orderPane.setBorder(new EmptyBorder(10, 10, 10, 10));
        JLabel titleOrders = new JLabel("Đơn của tôi");
        titleOrders.setFont(titleOrders.getFont().deriveFont(Font.BOLD));
        orderPane.add(titleOrders, BorderLayout.NORTH);
        orderList.setVisibleRowCount(14);
        orderPane.add(new JScrollPane(orderList), BorderLayout.CENTER);

        JPanel orderBtnPanel = new JPanel(new FlowLayout());
        JButton btRefresh = new JButton("Refresh");
        JButton btCancel = new JButton("Cancel");
        orderBtnPanel.add(btRefresh);
        orderBtnPanel.add(btCancel);
        orderPane.add(orderBtnPanel, BorderLayout.SOUTH);

        btRefresh.addActionListener(e -> loadOrders());
        btCancel.addActionListener(e -> cancelSelectedOrder());

        JSplitPane right = new JSplitPane(JSplitPane.VERTICAL_SPLIT, snackScroll, orderPane);
        right.setResizeWeight(0.5);

        JSplitPane main = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, seatsWrap, right);
        main.setResizeWeight(0.65);
        app.add(main, BorderLayout.CENTER);

        rootPanel.add(app, "APP");
    }

    private JPanel createTopBar() {
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        top.add(new JLabel("Phim:"));
        top.add(movieComboBox);

        JButton btRefreshSeats = new JButton("Refresh Seats");
        JButton btOrder = new JButton("Đặt vé");
        JButton btMyOrders = new JButton("Đơn chi tiết");
        JButton btLogout = new JButton("Logout");

        top.add(btRefreshSeats);
        top.add(btOrder);
        top.add(btMyOrders);
        top.add(btLogout);
        top.add(Box.createHorizontalStrut(18));
        lbUser.setFont(lbUser.getFont().deriveFont(Font.BOLD));
        top.add(lbUser);

        movieComboBox.addActionListener(e -> loadSeats());
        btRefreshSeats.addActionListener(e -> loadSeats());
        btOrder.addActionListener(e -> createOrder());
        btMyOrders.addActionListener(e -> openOrderHistory());
        btLogout.addActionListener(e -> logout());

        return top;
    }

    private JPanel createLegend() {
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        legend.add(makeChip("Available (Normal)", Color.LIGHT_GRAY));
        legend.add(makeChip("Available (Couple)", new Color(220, 170, 220)));
        legend.add(makeChip("Selected", Color.BLUE));
        legend.add(makeChip("Reserved", Color.ORANGE));
        legend.add(makeChip("Booked", Color.RED));
        return legend;
    }

    private Component makeChip(String text, Color bg) {
        JLabel l = new JLabel(text);
        l.setOpaque(true);
        l.setBackground(bg);
        l.setBorder(new EmptyBorder(4, 10, 4, 10));
        return l;
    }

    private JPanel createStatusBar() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(4, 10, 4, 10));
        statusBar.setFont(statusBar.getFont().deriveFont(Font.PLAIN, 12f));
        p.add(statusBar, BorderLayout.WEST);
        return p;
    }

    // ---------------- AUTH ----------------
    private void register() {
        String u = tfUsername.getText().trim();
        String p = new String(tfPassword.getPassword()).trim();
        if (u.isEmpty() || p.isEmpty()) {
            message("Nhập đầy đủ thông tin");
            return;
        }
        out.println("REGISTER " + u + " " + p);
        String resp = readLine();
        message(resp);
    }

    private void login() {
        String u = tfUsername.getText().trim();
        String p = new String(tfPassword.getPassword()).trim();
        if (u.isEmpty() || p.isEmpty()) {
            message("Nhập đầy đủ thông tin");
            return;
        }
        out.println("LOGIN " + u + " " + p);
        String resp = readLine();
        if (resp != null && resp.startsWith("OK")) {
            currentUser = u;
            lbUser.setText("User: " + u);
            loadMovies();
            loadSnacks();
            loadOrders();
            cardLayout.show(rootPanel, "APP");
            updateStatus("Xin chào, " + u);
        } else {
            message("Đăng nhập thất bại: " + resp);
        }
    }

    private void logout() {
        currentUser = null;
        tfUsername.setText("");
        tfPassword.setText("");
        orderModel.clear();
        lbUser.setText("User: -");
        cardLayout.show(rootPanel, "LOGIN");
        updateStatus("Đã đăng xuất");
    }

    // ---------------- LOAD DATA ----------------
    private void loadMovies() {
        out.println("LIST_MOVIES");
        String resp = readLine();
        if (resp == null || !resp.startsWith("MOVIES")) return;
        movieComboBox.removeAllItems();
        for (String m : resp.substring(7).split(",")) movieComboBox.addItem(m.trim());
        if (movieComboBox.getItemCount() > 0) movieComboBox.setSelectedIndex(0);
        loadSeats();
    }

    private void loadSnacks() {
        out.println("SNACK_LIST");
        String resp = readLine();
        if (resp == null || !resp.startsWith("SNACKS")) return;
        snackPanel.removeAll();
        snackChecks.clear();
        snackQuantities.clear();
        String data = resp.substring(7).trim();
        if (!data.isEmpty()) {
            for (String item : data.split(",")) {
                String[] kv = item.split(":");
                if (kv.length < 2) continue;
                String name = kv[0].trim();
                int price = Integer.parseInt(kv[1].trim());
                JCheckBox cb = new JCheckBox(name + " (" + price + " VND)");
                JSpinner sp = new JSpinner(new SpinnerNumberModel(1, 1, 20, 1));
                snackChecks.put(name, cb);
                snackQuantities.put(name, sp);
                JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
                row.add(cb);
                row.add(new JLabel("Qty:"));
                row.add(sp);
                snackPanel.add(row);
            }
        }
        snackPanel.revalidate();
        snackPanel.repaint();
    }

    private void loadSeats() {
        selectedSeats.clear();
        seatButtons.clear();
        seatGrid.removeAll();

        String mv = (String) movieComboBox.getSelectedItem();
        if (mv == null) return;
        out.println("LIST_SEATS " + mv);
        String resp = readLine();
        if (resp == null || !resp.startsWith("SEATS")) return;

        // Tách ghế đơn và ghế đôi
        String[] tokens = resp.substring(6).split(",");
        List<String> normalSeats = new ArrayList<>();
        List<String> coupleSeats = new ArrayList<>();

        for (String t : tokens) {
            if (t.isBlank()) continue;
            String[] p = t.split(":");
            String type = p.length > 2 ? p[2].trim().toUpperCase() : "NORMAL";
            if ("COUPLE".equals(type)) {
                coupleSeats.add(t);
            } else {
                normalSeats.add(t);
            }
        }

        // Tính số hàng và cột, thêm lối đi (khoảng trống giữa)
        int cols = 11; // 5 trái + 1 lối đi + 5 phải
        int normalRows = (int) Math.ceil(normalSeats.size() / 10.0); // 10 ghế/hàng (5 trái + 5 phải)
        int coupleRows = (int) Math.ceil(coupleSeats.size() / 10.0);
        int totalRows = Math.max(1, normalRows + coupleRows);
        seatGrid.setLayout(new GridLayout(totalRows, cols, 8, 8)); // Tăng khoảng cách

        // Thêm ghế đơn (các hàng trên)
        int normalIndex = 0;
        for (int row = 0; row < normalRows; row++) {
            for (int col = 0; col < cols; col++) {
                if (col == 5) { // Lối đi giữa
                    seatGrid.add(new JPanel());
                    continue;
                }
                if (normalIndex < normalSeats.size()) {
                    String t = normalSeats.get(normalIndex++);
                    String[] p = t.split(":");
                    String seat = p[0].trim();
                    String status = p[1].trim().toUpperCase();
                    String type = p.length > 2 ? p[2].trim().toUpperCase() : "NORMAL";

                    // Ghế đơn: movie-seat.png
                    Icon icon = "COUPLE".equals(type) ? iconSeatCouple : iconSeatSingle;
                    JButton b = new JButton(seat, icon);
                    b.setHorizontalTextPosition(SwingConstants.CENTER);
                    b.setVerticalTextPosition(SwingConstants.BOTTOM);
                    b.setIconTextGap(2);
                    b.setFocusPainted(false);
                    b.setContentAreaFilled(false);
                    b.setPreferredSize(new Dimension(60, 60));

                    // Chuẩn hóa màu viền
                    if ("BOOKED".equals(status)) {
                        b.setBorder(BorderFactory.createLineBorder(Color.RED, 3));
                        b.setEnabled(false);
                    } else if ("RESERVED".equals(status)) {
                        b.setBorder(BorderFactory.createLineBorder(Color.ORANGE, 3));
                        b.setEnabled(false);
                    } else {
                        b.setBorder(BorderFactory.createLineBorder(
                            "COUPLE".equals(type) ? new Color(220, 170, 220) : Color.LIGHT_GRAY, 1));
                        b.addActionListener(e -> toggleSeat(seat));
                    }

                    seatButtons.put(seat, b);
                    seatGrid.add(b);
                } else {
                    seatGrid.add(new JPanel()); // Lấp kín ô trống
                }
            }
        }

        // Thêm ghế đôi (hàng cuối)
        int coupleIndex = 0;
        for (int row = 0; row < coupleRows; row++) {
            for (int col = 0; col < cols; col++) {
                if (col == 5) { // Lối đi giữa
                    seatGrid.add(new JPanel());
                    continue;
                }
                if (coupleIndex < coupleSeats.size()) {
                    String t = coupleSeats.get(coupleIndex++);
                    String[] p = t.split(":");
                    String seat = p[0].trim();
                    String status = p[1].trim().toUpperCase();
                    String type = p.length > 2 ? p[2].trim().toUpperCase() : "NORMAL";

                    // Ghế đôi: watch-movie.png
                    Icon icon = "COUPLE".equals(type) ? iconSeatCouple : iconSeatSingle;
                    JButton b = new JButton(seat, icon);
                    b.setHorizontalTextPosition(SwingConstants.CENTER);
                    b.setVerticalTextPosition(SwingConstants.BOTTOM);
                    b.setIconTextGap(2);
                    b.setFocusPainted(false);
                    b.setContentAreaFilled(false);
                    b.setPreferredSize(new Dimension(60, 60));

                    // Chuẩn hóa màu viền
                    if ("BOOKED".equals(status)) {
                        b.setBorder(BorderFactory.createLineBorder(Color.RED, 3));
                        b.setEnabled(false);
                    } else if ("RESERVED".equals(status)) {
                        b.setBorder(BorderFactory.createLineBorder(Color.ORANGE, 3));
                        b.setEnabled(false);
                    } else {
                        b.setBorder(BorderFactory.createLineBorder(
                            "COUPLE".equals(type) ? new Color(220, 170, 220) : Color.LIGHT_GRAY, 1));
                        b.addActionListener(e -> toggleSeat(seat));
                    }

                    seatButtons.put(seat, b);
                    seatGrid.add(b);
                } else {
                    seatGrid.add(new JPanel()); // Lấp kín ô trống
                }
            }
        }

        seatGrid.revalidate();
        seatGrid.repaint();
        updateStatus("Seat map loaded.");
    }

    private void loadOrders() {
        if (currentUser == null) return;
        out.println("LIST_ORDERS " + currentUser);
        String resp = readLine();
        if (resp == null || !resp.startsWith("ORDERS")) return;
        orderModel.clear();
        for (String o : resp.substring(7).split(",")) if (!o.trim().isEmpty()) orderModel.addElement(o.trim());
    }

    // ---------------- ORDER FLOW ----------------
    private void toggleSeat(String seat) {
        JButton b = seatButtons.get(seat);
        if (selectedSeats.contains(seat)) {
            selectedSeats.remove(seat);
            // Khôi phục viền theo loại ghế
            String type = seat.contains("COUPLE") ? "COUPLE" : "NORMAL";
            b.setBorder(BorderFactory.createLineBorder(
                "COUPLE".equals(type) ? new Color(220, 170, 220) : Color.LIGHT_GRAY, 1));
        } else {
            selectedSeats.add(seat);
            b.setBorder(BorderFactory.createLineBorder(Color.BLUE, 3));
        }
        updateStatus("Selected: " + String.join(",", selectedSeats));
    }

    private void createOrder() {
        if (currentUser == null) {
            message("Chưa đăng nhập");
            return;
        }
        if (selectedSeats.isEmpty()) {
            message("Chọn ít nhất 1 ghế");
            return;
        }
        String mv = (String) movieComboBox.getSelectedItem();
        if (mv == null) {
            message("Chưa chọn phim");
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (String name : snackChecks.keySet()) {
            if (snackChecks.get(name).isSelected()) {
                int qty = (Integer) snackQuantities.get(name).getValue();
                if (sb.length() > 0) sb.append(",");
                sb.append(name).append(":").append(qty);
            }
        }
        out.println("CREATE_ORDER " + currentUser + " " + mv + " " + String.join(",", selectedSeats) + ";" + sb);
        String resp = readLine();
        if (resp == null) return;
        if (resp.startsWith("ORDER_CREATED")) {
            String[] p = resp.split(" ");
            showPaymentDialog(p[1], p[2], p[3]);
        } else {
            message("Order error: " + resp);
        }
    }

    private void cancelSelectedOrder() {
        String sel = orderList.getSelectedValue();
        if (sel == null || !sel.contains("UNPAID")) {
            message("Chọn đơn UNPAID để hủy");
            return;
        }
        int orderId = Integer.parseInt(sel.substring(1, sel.indexOf(" ")).trim());
        if (JOptionPane.showConfirmDialog(this, "Hủy đơn #" + orderId + " ?", "Xác nhận", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            out.println("CANCEL_ORDER " + orderId);
            String resp = readLine();
            if (resp != null && resp.startsWith("OK")) {
                message("Đã hủy đơn #" + orderId);
                loadSeats();
                loadOrders();
            } else {
                message("Hủy thất bại: " + resp);
            }
        }
    }

    private void showPaymentDialog(String orderId, String amount, String code) {
        JDialog d = new JDialog(this, "Thanh toán", true);
        d.setSize(460, 320);
        d.setLayout(new BorderLayout(10, 10));
        JTextArea ta = new JTextArea("=== QR Payment (simulated) ===\n\nPAYMENT CODE:\n" + code + "\n\nAmount: " + amount + " VND\n\nScan app ngân hàng (mô phỏng).");
        ta.setEditable(false);
        d.add(new JScrollPane(ta), BorderLayout.CENTER);
        JButton bt = new JButton("Xác nhận thanh toán");
        bt.addActionListener(e -> {
            out.println("PAY " + orderId + " " + code);
            String resp = readLine();
            if (resp != null && resp.startsWith("PAID")) {
                JOptionPane.showMessageDialog(this, "Thanh toán thành công!");
                d.dispose();
                loadSeats();
                loadOrders();
                selectedSeats.clear();
            } else {
                message("Payment failed: " + resp);
            }
        });
        d.add(bt, BorderLayout.SOUTH);
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    private void openOrderHistory() {
        if (currentUser == null) {
            message("Bạn chưa đăng nhập");
            return;
        }
        out.println("LIST_ORDER_DETAILS " + currentUser);
        String resp = readLine();
        if (resp == null || !resp.startsWith("ORDER_DETAILS")) {
            message("Không lấy được lịch sử");
            return;
        }
        String[] rows = resp.substring(14).split(";");
        String[] cols = {"Order#", "Phim", "Suất chiếu", "Ghế", "Snacks", "Tổng", "Thanh toán", "Thời gian"};
        DefaultTableModel m = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        for (String r : rows) {
            if (r.isBlank()) continue;
            String[] f = r.split("\\|", -1);
            if (f.length < 8) continue;
            m.addRow(new Object[]{f[0], f[1], f[2], f[3], f[4], f[5], "1".equals(f[6]) ? "PAID" : "UNPAID", f[7]});
        }
        JTable t = new JTable(m);
        JDialog d = new JDialog(this, "Lịch sử giao dịch", true);
        d.setSize(960, 520);
        d.add(new JScrollPane(t), BorderLayout.CENTER);
        JButton close = new JButton("Đóng");
        close.addActionListener(e -> d.dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(close);
        d.add(south, BorderLayout.SOUTH);
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    // ---------------- UTIL ----------------
    private String readLine() {
        try {
            String s = in.readLine();
            if (s == null) {
                message("Mất kết nối server");
            }
            return s;
        } catch (IOException e) {
            message("IO error: " + e.getMessage());
            return null;
        }
    }

    private void message(String msg) {
        JOptionPane.showMessageDialog(this, msg);
        updateStatus(msg);
    }

    private void updateStatus(String msg) {
        statusBar.setText(msg);
    }
}