package server.tcp;

import java.io.*;
import java.net.*;
import java.util.*;

public class MovieServer {
    private static final int PORT = 12345;
    private static DBManager db;

    public static void main(String[] args) {
        try {
            db = new DBManager();
        } catch (Exception e) {
            System.out.println("❌ Không kết nối được MySQL: " + e.getMessage());
            return;
        }

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("🎬 MovieServer running on port " + PORT);
            while (true) {
                Socket s = serverSocket.accept();
                new Thread(() -> handleClient(s)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("REQ: " + line);
                String[] tokens = line.trim().split(" ", 2);
                String cmd = tokens[0].toUpperCase();
                String args = tokens.length > 1 ? tokens[1] : "";

                try {
                    switch (cmd) {
                        case "REGISTER": {
                            String[] p = args.split(" ", 2);
                            if (p.length < 2) { out.println("ERROR BadParams"); break; }
                            boolean ok = db.registerUser(p[0], p[1]);
                            out.println(ok ? "OK Registered" : "ERROR UserExists");
                            break;
                        }
                        case "LOGIN": {
                            String[] p = args.split(" ", 2);
                            if (p.length < 2) { out.println("ERROR BadParams"); break; }
                            int uid = db.login(p[0], p[1]);
                            out.println(uid != -1 ? ("OK Login " + uid) : "ERROR AuthFailed");
                            break;
                        }
                        case "LIST_MOVIES": {
                            out.println("MOVIES " + String.join(",", db.getMovies()));
                            break;
                        }
                        case "LIST_SEATS": {
                            int movieId = DBManager.parseMovieId(args);
                            if (movieId <= 0) { out.println("ERROR InvalidMovieId"); break; }
                            out.println("SEATS " + String.join(",", db.getSeatsColonFormat(movieId)));
                            break;
                        }
                        case "SNACK_LIST": {
                            out.println("SNACKS " + String.join(",", db.getSnackList()));
                            break;
                        }
                        case "CREATE_ORDER": {
                            // CREATE_ORDER <username> <movieId - movieName (time)> <seat1,seat2;Snack:Qty>
                            int firstSpace = args.indexOf(' ');
                            if (firstSpace < 0) { out.println("ERROR BadParams"); break; }
                            String username = args.substring(0, firstSpace).trim();

                            String remain = args.substring(firstSpace + 1).trim();
                            int movieId = DBManager.parseMovieId(remain);
                            if (movieId <= 0) { out.println("ERROR InvalidMovieId"); break; }

                            int closeParen = remain.indexOf(')');
                            if (closeParen < 0 || closeParen + 1 >= remain.length()) {
                                out.println("ERROR BadMovieToken");
                                break;
                            }
                            String afterMovie = remain.substring(closeParen + 1).trim();

                            String[] parts = afterMovie.split(";", 2);
                            String seatCsv = parts[0].trim();
                            String snackSpec = parts.length > 1 ? parts[1].trim() : "";

                            List<String> seats = DBManager.splitSeats(seatCsv);
                            var snacks = DBManager.parseSnacks(snackSpec);
                            if (seats.isEmpty()) { out.println("ERROR NoSeats"); break; }

                            DBManager.CreateOrderResult r = db.createOrder(username, movieId, seats, snacks);
                            out.println("ORDER_CREATED " + r.orderId + " " + ((long) r.amount) + " " + r.paymentCode);
                            break;
                        }
                        case "PAY": {
                            String[] p = args.split(" ", 2);
                            if (p.length < 2) { out.println("ERROR BadParams"); break; }
                            int orderId = Integer.parseInt(p[0].trim());
                            String code = p[1].trim();
                            String ticket = db.payOrder(orderId, code);
                            out.println("PAID " + orderId + " " + ticket.replace("\n", "\\n"));
                            break;
                        }
                        case "LIST_ORDERS": {
                            String username = args.trim();
                            out.println("ORDERS " + String.join(",", db.listOrders(username)));
                            break;
                        }
                        case "LIST_ORDER_DETAILS": {
                            String username = args.trim();
                            var list = db.listOrderDetails(username);
                            // mỗi record: id|movie|showTime|seats|snacks|amount|paid|createdAt
                            List<String> rows = new ArrayList<>();
                            for (DBManager.OrderDetail od : list) {
                                rows.add(od.id + "|" +
                                         safe(od.movieName) + "|" +
                                         safe(od.showTime) + "|" +
                                         safe(od.seatsCsv) + "|" +
                                         safe(od.snacksCsv) + "|" +
                                         od.amount + "|" +
                                         od.paid + "|" +
                                         od.createdAt);
                            }
                            out.println("ORDER_DETAILS " + String.join(";", rows));
                            break;
                        }
                        case "CANCEL_ORDER": {
                            int orderId = Integer.parseInt(args.trim());
                            boolean ok = db.cancelOrder(orderId);
                            out.println(ok ? "OK OrderCanceled" : "ERROR CancelFailed");
                            break;
                        }
                        case "PAY_LATER": {
                            out.println("OK PayLater");
                            break;
                        }
                        default:
                            out.println("ERROR UnknownCommand");
                    }
                } catch (Exception ex) {
                    out.println("ERROR " + ex.getMessage());
                    ex.printStackTrace();
                }
            }

        } catch (Exception e) {
            System.out.println("Client error: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private static String safe(Object x) {
        if (x == null) return "";
        String s = x.toString();
        // tránh ký tự phân tách
        return s.replace("|","/").replace(";","/");
    }
}
