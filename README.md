<h2 align="center">
    <a href="https://dainam.edu.vn/vi/khoa-cong-nghe-thong-tin">
    🎓 Faculty of Information Technology (DaiNam University)
    </a>
</h2>
<h2 align="center">
     ỨNG DỤNG ĐẶT VÉ XEM PHIM
</h2>
<div align="center">
    <p align="center">
        <img alt="AIoTLab Logo" width="170" src="https://github.com/user-attachments/assets/711a2cd8-7eb4-4dae-9d90-12c0a0a208a2" />
        <img alt="AIoTLab Logo" width="180" src="https://github.com/user-attachments/assets/dc2ef2b8-9a70-4cfa-9b4b-f6c2f25f1660" />
        <img alt="DaiNam University Logo" width="200" src="https://github.com/user-attachments/assets/77fe0fd1-2e55-4032-be3c-b1a705a1b574" />
    </p>

[![AIoTLab](https://img.shields.io/badge/AIoTLab-green?style=for-the-badge)](https://www.facebook.com/DNUAIoTLab)
[![Faculty of Information Technology](https://img.shields.io/badge/Faculty%20of%20Information%20Technology-blue?style=for-the-badge)](https://dainam.edu.vn/vi/khoa-cong-nghe-thong-tin)
[![DaiNam University](https://img.shields.io/badge/DaiNam%20University-orange?style=for-the-badge)](https://dainam.edu.vn)
</div>

## 1. Giới thiệu hệ thống
Ứng dụng **Đặt Vé Xem Phim** cung cấp nền tảng đặt vé trực tuyến cho rạp chiếu phim:
- Chọn phim và suất chiếu.
- Chọn nhiều ghế cùng lúc (thường, đôi).
- Chọn combo thức ăn & nước uống.
- Thanh toán trực tuyến hoặc giữ chỗ (Reserved) để thanh toán sau.
- Quản lý và hiển thị **lịch sử giao dịch chi tiết**: phim, suất chiếu, ghế, đồ ăn, tổng tiền, trạng thái thanh toán.

Ứng dụng được phát triển bằng **Java Swing** cho giao diện đồ họa hiện đại, sử dụng **MySQL** để quản lý dữ liệu và **giao thức TCP Socket** để truyền thông tin giữa client và server.

### Kiến trúc
- **Client**: Giao diện đặt vé, hiển thị sơ đồ ghế, chọn combo, thanh toán, xem lịch sử.
- **Server**: Xử lý yêu cầu, lưu/đọc dữ liệu từ MySQL, quản lý trạng thái ghế, đơn hàng.

Đặc điểm nổi bật:
- Giao diện trực quan, hỗ trợ FlatLaf Look & Feel (giao diện phẳng, hiện đại).
- Hỗ trợ thanh toán sau và hủy đơn chưa thanh toán.
- Cho phép mở rộng để quản lý phim, suất chiếu, reset ghế đã đặt.

## 2. Ngôn ngữ & Công nghệ
[![Java](https://img.shields.io/badge/Java-007396?style=for-the-badge&logo=java&logoColor=white)](https://www.java.com/)
[![Java Swing](https://img.shields.io/badge/Java%20Swing-007396?style=for-the-badge&logo=java&logoColor=white)](https://docs.oracle.com/javase/tutorial/uiswing/)
[![MySQL](https://img.shields.io/badge/MySQL-4479A1?style=for-the-badge&logo=mysql&logoColor=white)](https://www.mysql.com/)
[![TCP Socket](https://img.shields.io/badge/TCP%20Socket-007396?style=for-the-badge&logo=socketdotio&logoColor=white)](https://docs.oracle.com/javase/tutorial/networking/sockets/)
[![FlatLaf](https://img.shields.io/badge/FlatLaf-3.6.1-green?style=for-the-badge)](https://www.formdev.com/flatlaf/)

## 3. Một số màn hình giao diện
<p align="center">
   <img src="images/login.png" alt="Đăng nhập" width="500"/>
</p>
<p align="center">
   <em>Hình 1: Giao diện đăng nhập/đăng ký</em>
</p>

<p align="center">
   <img src="images/seatmap.png" alt="Sơ đồ ghế" width="500"/>
</p>
<p align="center">
   <em>Hình 2: Chọn ghế và đồ ăn thức uống</em>
</p>

<p align="center">
   <img src="images/history.png" alt="Lịch sử" width="500"/>
</p>
<p align="center">
   <em>Hình 3: Lịch sử giao dịch chi tiết</em>
</p>

*(Bạn có thể chụp màn hình thực tế của ứng dụng để thay thế các ảnh trên)*

## 4. Cài đặt & Sử dụng
**Yêu cầu môi trường:**
- Java Development Kit (JDK) 8 trở lên.
- MySQL Server.
- IDE: Eclipse/IntelliJ hoặc chạy trực tiếp qua terminal.

**Cách triển khai:**
1. Import project vào IDE.
2. Chạy file SQL `movie_booking.sql` để khởi tạo cơ sở dữ liệu.
3. Chạy `MovieServer` (server TCP).
4. Chạy nhiều instance `MovieClient` để đặt vé từ nhiều máy hoặc nhiều cửa sổ.
5. Đăng ký tài khoản và bắt đầu đặt vé.

## 5. Thành viên & Thông tin
- **Sinh viên thực hiện**: Lê Đức Mạnh
- **Lớp**: CNTT 16-01
- **Email**: leducmanh19102004@gmail.com

© 2025 AIoTLab – Faculty of Information Technology, DaiNam University
