package server.tcp;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class DBManager {
    private static final String URL = "jdbc:mysql://localhost:3306/movie_booking?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String USER = "appuser";
    private static final String PASS = "123456";

    private final Connection conn;

    public DBManager() throws SQLException {
        conn = DriverManager.getConnection(URL, USER, PASS);
        System.out.println("✅ Kết nối MySQL thành công!");
    }

    // ------------ Auth ------------
    public boolean registerUser(String username, String password) throws SQLException {
        try (PreparedStatement check = conn.prepareStatement("SELECT id FROM users WHERE username=?")) {
            check.setString(1, username);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) return false;
            }
        }
        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO users(username,password,balance) VALUES(?,?,?)")) {
            ins.setString(1, username);
            ins.setString(2, password);
            ins.setDouble(3, 100);
            return ins.executeUpdate() > 0;
        }
    }

    public int login(String username, String password) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM users WHERE username=? AND password=?")) {
            ps.setString(1, username);
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    // ------------ Movies ------------
    public List<String> getMovies() throws SQLException {
        List<String> list = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id,name,show_time FROM movies ORDER BY id")) {
            while (rs.next()) {
                list.add(rs.getInt("id") + " - " + rs.getString("name") + " (" + rs.getString("show_time") + ")");
            }
        }
        return list;
    }

    // ------------ Seats ------------
    public List<String> getSeatsColonFormat(int movieId) throws SQLException {
        List<String> list = new ArrayList<>();
        String sql = "SELECT seat_code, status, type FROM seats WHERE movie_id=? ORDER BY id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, movieId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String seat = rs.getString("seat_code");
                    String status = rs.getString("status"); // AVAILABLE/RESERVED/BOOKED
                    String type = rs.getString("type");     // NORMAL/COUPLE
                    list.add(seat + ":" + status + ":" + type);
                }
            }
        }
        return list;
    }

    // ------------ Snacks ------------
    public List<String> getSnackList() throws SQLException {
        List<String> res = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name, price FROM snacks ORDER BY name")) {
            while (rs.next()) {
                res.add(rs.getString(1) + ":" + rs.getInt(2));
            }
        }
        return res;
    }

    // ------------ Orders ------------
    public static class CreateOrderResult {
        public final int orderId;
        public final String paymentCode;
        public final double amount;
        public CreateOrderResult(int id, String code, double amt) { orderId = id; paymentCode = code; amount = amt; }
    }

    public CreateOrderResult createOrder(String username, int movieId, List<String> seatCodes,
                                         Map<String,Integer> snacks) throws Exception {
        conn.setAutoCommit(false);
        try {
            int userId = getUserId(username);
            if (userId == -1) throw new RuntimeException("User not found");

            // Lock & check seats
            String q = "SELECT seat_code,status,price FROM seats WHERE movie_id=? AND seat_code IN (" +
                       seatCodes.stream().map(s -> "?").collect(Collectors.joining(",")) + ") FOR UPDATE";
            try (PreparedStatement ps = conn.prepareStatement(q)) {
                ps.setInt(1, movieId);
                int idx = 2;
                for (String s : seatCodes) ps.setString(idx++, s);
                try (ResultSet rs = ps.executeQuery()) {
                    Map<String, Double> priceMap = new HashMap<>();
                    while (rs.next()) {
                        String status = rs.getString("status");
                        if (!"AVAILABLE".equals(status)) {
                            throw new RuntimeException("Seat " + rs.getString("seat_code") + " not available");
                        }
                        priceMap.put(rs.getString("seat_code"), rs.getDouble("price"));
                    }
                    if (priceMap.size() != seatCodes.size()) {
                        throw new RuntimeException("Some seats not found");
                    }

                    double seatSum = priceMap.values().stream().mapToDouble(d -> d).sum();
                    double snackSum = 0;
                    if (!snacks.isEmpty()) {
                        String sq = "SELECT name, price FROM snacks WHERE name IN (" +
                                snacks.keySet().stream().map(s -> "?").collect(Collectors.joining(",")) + ")";
                        try (PreparedStatement sp = conn.prepareStatement(sq)) {
                            int sIdx = 1;
                            for (String s : snacks.keySet()) sp.setString(sIdx++, s);
                            try (ResultSet srs = sp.executeQuery()) {
                                Map<String,Integer> priceSnack = new HashMap<>();
                                while (srs.next()) priceSnack.put(srs.getString(1), srs.getInt(2));
                                for (Map.Entry<String,Integer> e : snacks.entrySet()) {
                                    Integer p = priceSnack.get(e.getKey());
                                    if (p == null) throw new RuntimeException("Snack not found: " + e.getKey());
                                    snackSum += p * e.getValue();
                                }
                            }
                        }
                    }
                    double amount = seatSum + snackSum;

                    // Insert order
                    String payCode = genPaymentCode();
                    int orderId;
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO orders(user_id,movie_id,amount,payment_code,paid) VALUES(?,?,?,?,0)",
                            Statement.RETURN_GENERATED_KEYS)) {
                        ins.setInt(1, userId);
                        ins.setInt(2, movieId);
                        ins.setDouble(3, amount);
                        ins.setString(4, payCode);
                        ins.executeUpdate();
                        try (ResultSet gk = ins.getGeneratedKeys()) {
                            gk.next();
                            orderId = gk.getInt(1);
                        }
                    }

                    // Insert order_seats
                    try (PreparedStatement os = conn.prepareStatement(
                            "INSERT INTO order_seats(order_id, seat_code, price) VALUES(?,?,?)")) {
                        for (String s : seatCodes) {
                            os.setInt(1, orderId);
                            os.setString(2, s);
                            os.setDouble(3, priceMap.get(s));
                            os.addBatch();
                        }
                        os.executeBatch();
                    }

                    // Insert order_snacks
                    if (!snacks.isEmpty()) {
                        try (PreparedStatement oss = conn.prepareStatement(
                                "INSERT INTO order_snacks(order_id, snack_name, qty, unit_price) " +
                                "SELECT ?, name, ?, price FROM snacks WHERE name=?")) {
                            for (Map.Entry<String,Integer> e : snacks.entrySet()) {
                                oss.setInt(1, orderId);
                                oss.setInt(2, e.getValue());
                                oss.setString(3, e.getKey());
                                oss.addBatch();
                            }
                            oss.executeBatch();
                        }
                    }

                    // Reserve seats
                    try (PreparedStatement up = conn.prepareStatement(
                            "UPDATE seats SET status='RESERVED', reserved_by=? WHERE movie_id=? AND seat_code=? AND status='AVAILABLE'")) {
                        for (String s : seatCodes) {
                            up.setInt(1, userId);
                            up.setInt(2, movieId);
                            up.setString(3, s);
                            up.addBatch();
                        }
                        int[] upd = up.executeBatch();
                        for (int u : upd) if (u == 0) throw new RuntimeException("Seat changed concurrently");
                    }

                    conn.commit();
                    return new CreateOrderResult(orderId, payCode, amount);
                }
            }
        } catch (Exception ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    public boolean cancelOrder(int orderId) throws SQLException {
        conn.setAutoCommit(false);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT movie_id FROM orders WHERE id=? AND paid=0 FOR UPDATE")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) { conn.rollback(); return false; }
                int movieId = rs.getInt(1);

                // Release seats
                try (PreparedStatement up = conn.prepareStatement(
                        "UPDATE seats s JOIN order_seats os ON s.seat_code=os.seat_code " +
                        "SET s.status='AVAILABLE', s.reserved_by=NULL " +
                        "WHERE os.order_id=? AND s.movie_id=?")) {
                    up.setInt(1, orderId);
                    up.setInt(2, movieId);
                    up.executeUpdate();
                }

                // Delete details + order
                try (PreparedStatement d1 = conn.prepareStatement("DELETE FROM order_seats WHERE order_id=?");
                     PreparedStatement d2 = conn.prepareStatement("DELETE FROM order_snacks WHERE order_id=?");
                     PreparedStatement d3 = conn.prepareStatement("DELETE FROM orders WHERE id=?")) {
                    d1.setInt(1, orderId); d1.executeUpdate();
                    d2.setInt(1, orderId); d2.executeUpdate();
                    d3.setInt(1, orderId); d3.executeUpdate();
                }
            }
            conn.commit();
            return true;
        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    public String payOrder(int orderId, String paymentCode) throws Exception {
        conn.setAutoCommit(false);
        try {
            int userId, movieId;
            double amount;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT user_id,movie_id,amount,paid,payment_code FROM orders WHERE id=? FOR UPDATE")) {
                ps.setInt(1, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new RuntimeException("Order not found");
                    if (rs.getInt("paid") == 1) throw new RuntimeException("Already paid");
                    if (!paymentCode.equals(rs.getString("payment_code"))) throw new RuntimeException("Invalid code");
                    userId = rs.getInt("user_id");
                    movieId = rs.getInt("movie_id");
                    amount = rs.getDouble("amount");
                }
            }

            // Mark paid
            try (PreparedStatement up = conn.prepareStatement("UPDATE orders SET paid=1 WHERE id=?")) {
                up.setInt(1, orderId);
                up.executeUpdate();
            }

            // Book seats
            try (PreparedStatement up = conn.prepareStatement(
                    "UPDATE seats s JOIN order_seats os ON os.seat_code=s.seat_code " +
                    "SET s.status='BOOKED', s.booked_by=?, s.reserved_by=NULL " +
                    "WHERE os.order_id=? AND s.movie_id=?")) {
                up.setInt(1, userId);
                up.setInt(2, orderId);
                up.setInt(3, movieId);
                up.executeUpdate();
            }

            // Ticket
            StringBuilder sb = new StringBuilder();
            sb.append("Order #").append(orderId).append("\n");
            String movieName = getMovieName(movieId);
            sb.append("Movie: ").append(movieName).append("\n");
            sb.append("Seats: ");
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT seat_code FROM order_seats WHERE order_id=? ORDER BY seat_code")) {
                ps.setInt(1, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<String> seats = new ArrayList<>();
                    while (rs.next()) seats.add(rs.getString(1));
                    sb.append(String.join(",", seats)).append("\n");
                }
            }
            sb.append("Amount: ").append((long)amount).append(" VND\n");
            sb.append("Status: PAID\n");
            conn.commit();
            return sb.toString();
        } catch (Exception ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    public List<String> listOrders(String username) throws SQLException {
        List<String> res = new ArrayList<>();
        int userId = getUserId(username);
        if (userId == -1) return res;
        String sql = "SELECT o.id, o.amount, o.paid, m.name FROM orders o JOIN movies m ON m.id=o.movie_id " +
                     "WHERE o.user_id=? ORDER BY o.id DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String paid = rs.getInt("paid")==1 ? "PAID" : "UNPAID";
                    res.add("#"+rs.getInt(1)+" - "+rs.getString("name")+" - "+((long)rs.getDouble("amount"))+" VND - "+paid);
                }
            }
        }
        return res;
    }

    // ---------- NEW: Order details for "Đơn của tôi" ----------
    public static class OrderDetail {
        public int id;
        public String movieName;
        public String showTime;
        public String seatsCsv;
        public String snacksCsv;
        public long amount;
        public int paid;
        public Timestamp createdAt;
    }

    public List<OrderDetail> listOrderDetails(String username) throws SQLException {
        List<OrderDetail> list = new ArrayList<>();
        int userId = getUserId(username);
        if (userId == -1) return list;

        String sql =
            "SELECT o.id, m.name AS movie_name, m.show_time, " +
            "       IFNULL(GROUP_CONCAT(DISTINCT os.seat_code ORDER BY os.seat_code SEPARATOR ','), '') AS seats, " +
            "       IFNULL(GROUP_CONCAT(DISTINCT CONCAT(osn.snack_name,'x',osn.qty) SEPARATOR ', '), '') AS snacks, " +
            "       o.amount, o.paid, o.created_at " +
            "FROM orders o " +
            "JOIN movies m ON m.id=o.movie_id " +
            "LEFT JOIN order_seats os ON os.order_id=o.id " +
            "LEFT JOIN order_snacks osn ON osn.order_id=o.id " +
            "WHERE o.user_id=? " +
            "GROUP BY o.id, m.name, m.show_time, o.amount, o.paid, o.created_at " +
            "ORDER BY o.id DESC";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OrderDetail od = new OrderDetail();
                    od.id = rs.getInt("id");
                    od.movieName = rs.getString("movie_name");
                    od.showTime = rs.getString("show_time");
                    od.seatsCsv = rs.getString("seats");
                    od.snacksCsv = rs.getString("snacks");
                    od.amount = (long)rs.getDouble("amount");
                    od.paid = rs.getInt("paid");
                    od.createdAt = rs.getTimestamp("created_at");
                    list.add(od);
                }
            }
        }
        return list;
    }

    // ---------- Helpers ----------
    private int getUserId(String username) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM users WHERE username=?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : -1; }
        }
    }

    private String getMovieName(int movieId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT name FROM movies WHERE id=?")) {
            ps.setInt(1, movieId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : ("ID " + movieId); }
        }
    }

    private static String genPaymentCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    public static List<String> splitSeats(String seatCsv) {
        List<String> list = new ArrayList<>();
        for (String s : seatCsv.split(",")) {
            s = s.trim();
            if (!s.isEmpty()) list.add(s);
        }
        return list;
    }

    public static Map<String,Integer> parseSnacks(String snackSpec) {
        Map<String,Integer> map = new LinkedHashMap<>();
        if (snackSpec == null || snackSpec.isBlank()) return map;
        for (String tok : snackSpec.split(",")) {
            tok = tok.trim();
            if (tok.isEmpty()) continue;
            String[] kv = tok.split(":");
            String name = kv[0].trim();
            int qty = (kv.length>1) ? Integer.parseInt(kv[1].trim()) : 1;
            map.put(name, qty);
        }
        return map;
    }

    public static int parseMovieId(String token) {
        try {
            String t = token.trim();
            int dash = t.indexOf('-');
            if (dash > 0) t = t.substring(0, dash).trim();
            return Integer.parseInt(t);
        } catch (Exception e) {
            return -1;
        }
    }
}
