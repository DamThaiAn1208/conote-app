Tên đề tài

XÂY DỰNG ỨNG DỤNG GHI CHÚ DESKTOP HỖ TRỢ TẠO, QUẢN LÝ VÀ CHIA SẺ GHI CHÚ

Mô tả

Trong học tập và công việc, nhu cầu ghi chú thông tin cá nhân, lưu lại ý tưởng, kế hoạch và nội dung quan trọng là rất phổ biến. Tuy nhiên, nhiều ứng dụng ghi chú hiện nay chỉ tập trung vào việc lưu trữ cho cá nhân mà chưa hỗ trợ tốt việc chia sẻ nội dung giữa người dùng với nhau trong cùng một hệ thống đơn giản, dễ sử dụng.

Từ nhu cầu đó, đề tài hướng đến việc xây dựng một ứng dụng ghi chú trên nền tảng desktop, cho phép người dùng tạo tài khoản, đăng nhập, tạo và quản lý các ghi chú cá nhân, đồng thời hỗ trợ chia sẻ ghi chú với người dùng khác. Người dùng có thể cấp quyền xem hoặc chỉnh sửa đối với từng ghi chú được chia sẻ, giúp tăng khả năng phối hợp học tập và làm việc mà vẫn đảm bảo tính riêng tư của dữ liệu cá nhân.

Hệ thống được xây dựng theo mô hình client-server và áp dụng kiến trúc N-layer nhằm đảm bảo phân tách rõ ràng giữa giao diện, xử lý nghiệp vụ, truy cập dữ liệu và lưu trữ. Ở phía client, ứng dụng sử dụng JavaFX để xây dựng giao diện desktop trực quan, thân thiện với người dùng. Ở phía server, hệ thống sử dụng Java để xử lý nghiệp vụ, xác thực người dùng, quản lý note và điều phối quá trình chia sẻ dữ liệu giữa các tài khoản.

Ứng dụng cho phép người dùng tạo mới, chỉnh sửa, xóa, tìm kiếm và phân loại ghi chú. Mỗi ghi chú có thể bao gồm tiêu đề, nội dung, thời gian tạo, thời gian cập nhật và trạng thái chia sẻ. Ngoài ra, hệ thống còn hỗ trợ đánh dấu yêu thích, ghim ghi chú quan trọng, và tìm kiếm nhanh theo từ khóa để giúp người dùng quản lý nội dung hiệu quả hơn.

Một điểm nổi bật của hệ thống là chức năng chia sẻ ghi chú. Người dùng có thể chọn một note bất kỳ và chia sẻ cho người dùng khác thông qua tài khoản trong hệ thống. Khi chia sẻ, chủ sở hữu có thể thiết lập quyền truy cập như chỉ xem hoặc cho phép chỉnh sửa. Điều này giúp ứng dụng không chỉ phục vụ nhu cầu quản lý ghi chú cá nhân mà còn hỗ trợ trao đổi thông tin và cộng tác ở mức cơ bản giữa các người dùng.

Về mặt công nghệ, hệ thống dự kiến sử dụng JavaFX cho giao diện desktop, Java cho xử lý phía server, Socket để giao tiếp giữa client và server, MariaDB hoặc MySQL để lưu trữ dữ liệu, BCrypt để mã hóa mật khẩu và Gson hoặc Jackson để trao đổi dữ liệu dưới dạng JSON. Việc lựa chọn các công nghệ này giúp ứng dụng phù hợp với mô hình desktop client-server, dễ triển khai và thuận lợi cho việc mở rộng sau này.

Tóm lại, đề tài hướng đến việc xây dựng một ứng dụng ghi chú desktop hiện đại, hỗ trợ quản lý note cá nhân và chia sẻ note giữa người dùng trong cùng hệ thống. Đây là một đề tài có tính ứng dụng thực tế, độ phức tạp vừa phải, phù hợp để triển khai thành đồ án phần mềm theo hướng rõ ràng, dễ phát triển và dễ đánh giá.