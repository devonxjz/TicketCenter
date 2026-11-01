# Cẩm nang cấu trúc & phong cách viết code theo Murach

> Đúc kết từ **Murach's Java Servlets and JSP, 3rd Edition (Joel Murach & Michael Urban, 2014)**,
> chủ yếu từ các chương 2 (MVC), 5 (Servlet), 6 (JSP), 8–10 (EL / JSTL / Custom tag),
> 12 (JDBC), 13 (JPA), 19–20 (Listener / Filter), 22–23 (website Music Store).
>
> Trọng tâm của tài liệu: **phong cách "đơn giản – tuần tự – dễ đoán"** mà Murach dùng nhất quán
> trong toàn bộ cuốn sách, cùng lý do vì sao nó giúp code dễ đọc, dễ hiểu và dễ bảo trì.
>
> Quy ước: những chỗ đánh dấu **[Ngoài sách]** là phần mình bổ sung theo Java hiện đại, sách không viết.
> Mọi phần còn lại đều lấy từ sách.

---

## Mục lục

1. [Triết lý cốt lõi](#1-triết-lý-cốt-lõi)
2. [Cấu trúc dự án: MVC và ba tầng](#2-cấu-trúc-dự-án-mvc-và-ba-tầng)
3. [Phong cách logic đơn giản, tuần tự (phần chính)](#3-phong-cách-logic-đơn-giản-tuần-tự)
4. [Quy tắc theo từng tầng](#4-quy-tắc-theo-từng-tầng)
5. [Quy ước đặt tên](#5-quy-ước-đặt-tên)
6. [Validation, xử lý lỗi, bảo mật](#6-validation-xử-lý-lỗi-bảo-mật)
7. [Trình bày code](#7-trình-bày-code)
8. [Bảng anti-pattern (những điều sách cấm)](#8-bảng-anti-pattern)
9. [Template mẫu để copy](#9-template-mẫu-để-copy)
10. [Checklist trước khi commit](#10-checklist-trước-khi-commit)

---

## 1. Triết lý cốt lõi

Tóm cả cuốn sách thành 10 nguyên tắc:

| # | Nguyên tắc | Ý nghĩa thực tế |
|---|-----------|-----------------|
| 1 | **Tách tầng (separation of concerns)** | Model, View, Controller càng độc lập càng tốt. Sửa giao diện thì không phải đụng controller hay model. |
| 2 | **Mỗi file/lớp làm đúng một việc** | Servlet điều phối, JavaBean chứa dữ liệu, lớp `XxxDB` truy cập DB, JSP hiển thị. |
| 3 | **Mọi lớp cùng loại có cùng "hình dạng"** | Hiểu một `ProductDB` là hiểu hết `UserDB`, `InvoiceDB`... ("they all follow the same pattern"). |
| 4 | **Luồng xử lý đọc từ trên xuống dưới** | Đọc input → xử lý → lưu → đặt dữ liệu cho view → forward. Không nhảy lung tung. |
| 5 | **Một lối ra duy nhất** | Mỗi handler chỉ có **một** câu lệnh `forward` ở cuối, điều khiển bằng biến `url`. |
| 6 | **Giá trị mặc định đặt ngay từ đầu** | `String url = "/index.jsp";` và `if (action == null) action = "join";` |
| 7 | **Gom code lặp vào một chỗ** | `DBUtil`, `CookieUtil`, file `include` header/footer, custom tag. Sửa một nơi là áp dụng cho tất cả. |
| 8 | **Không đặt Java trong JSP** | Dùng EL + JSTL. Có thể tắt hẳn scripting trong `web.xml`. |
| 9 | **An toàn mặc định** | Luôn dùng prepared statement / tham số hoá, escape mọi output, validate ở server. |
| 10 | **Luôn dọn tài nguyên** | Đóng `ResultSet`, `PreparedStatement`, trả connection, đóng `EntityManager` trong `finally`. |

---

## 2. Cấu trúc dự án: MVC và ba tầng

### 2.1 Vì sao là MVC (Model 2), không phải Model 1

- **Model 1**: JSP làm hết, từ nhận request, xử lý đến hiển thị. Sách gọi đây là *"generally considered a bad practice"*.
- **Model 2 (MVC)**: tách ra ba phần:
  - **Model**: business object (JavaBean) như `User`, `Product`, `Invoice`.
  - **View**: HTML / JSP, chỉ để hiển thị.
  - **Controller**: Servlet, điều phối luồng xử lý.
- Thêm một tầng **Data access** (`UserDB`, `ProductDB`): JavaBean **không tự lưu chính nó**, việc đó thuộc về lớp DB. Như vậy business logic tách khỏi thao tác dữ liệu.

> *"When you use the MVC pattern, you should try to keep the model, view, and controller as independent
> of each other as possible... complete independence is the goal."* (Chương 2)

### 2.2 Luồng một request

```
Browser ──HTTP request──▶ Controller (Servlet)
                              │ 1. đọc tham số
                              │ 2. tạo/cập nhật Model (JavaBean)
                              │ 3. gọi Data access (XxxDB) để lưu/đọc
                              │ 4. request.setAttribute(...)
                              ▼
                          View (JSP + EL/JSTL) ──HTTP response──▶ Browser
```

### 2.3 Cây thư mục chuẩn (theo website Music Store, chương 22)

```
musicStore/                  ← root: HTML, JSP
├── admin/  cart/  catalog/  email/  customer_service/   ← JSP nhóm theo chức năng
├── includes/                ← header.jsp, footer.jsp, column_left_*.jsp
├── images/                  ← ảnh
├── styles/                  ← CSS (main.css)
├── META-INF/context.xml     ← cấu hình connection pool / DataSource
└── WEB-INF/
    ├── web.xml              ← deployment descriptor
    ├── lib/                 ← JAR thư viện
    └── classes/music/
        ├── business/        ← JavaBean: User, Product, Invoice, LineItem, Cart, Download
        ├── controllers/     ← Servlet: CatalogController, OrderController, UserController, AdminController
        ├── data/            ← ProductDB, UserDB, InvoiceDB, ..., ConnectionPool, DBUtil
        └── util/            ← CookieUtil, MusicStoreContextListener, ...
```

**Quy tắc rút ra:**

| Package | Chứa gì | Không được chứa |
|--------|---------|-----------------|
| `business` | JavaBean: field private, getter/setter, constructor rỗng, `Serializable`, method tiện ích (format tiền, tính tổng) | SQL, `HttpServletRequest`, HTML |
| `controllers` | Servlet: nhận request, gọi business/data, chọn view | SQL thô, HTML dài, instance variable |
| `data` | Lớp `XxxDB` với **static method**, không có instance variable hay constructor | Logic giao diện, `request`/`response` |
| `util` | Helper dùng chung: cookie, listener, filter | Logic nghiệp vụ đặc thù |
| JSP | HTML + EL + JSTL | Scriptlet `<% %>`, declaration `<%! %>` |

---

## 3. Phong cách logic đơn giản, tuần tự

> Đây là "chất" Murach: code **phẳng**, **đọc một lượt từ trên xuống là hiểu**, mọi method cùng loại
> **giống nhau về hình dạng**. Không có gì hay ho, và đó chính là mục đích.

### 3.1 Khuôn 5 bước của mọi controller

Mọi `doPost` / `doGet` trong sách đều theo đúng thứ tự này:

```
① Khởi tạo giá trị mặc định    (url, action)
② Xác định việc cần làm        (đọc action hoặc URI)
③ Rẽ nhánh phẳng if / else if  (mỗi nhánh: đọc tham số → xử lý → đặt url)
④ Đặt dữ liệu cho view         (request.setAttribute)
⑤ Một lối ra duy nhất          (forward(url))
```

Ví dụ gốc (chương 2 + 5, đã sửa lỗi OCR):

```java
@Override
protected void doPost(HttpServletRequest request,
                      HttpServletResponse response)
                      throws ServletException, IOException {

    String url = "/index.jsp";                       // ① mặc định

    // get current action
    String action = request.getParameter("action");  // ②
    if (action == null) {
        action = "join";  // default action
    }

    // perform action and set URL to appropriate page
    if (action.equals("join")) {                     // ③
        url = "/index.jsp";    // the "join" page
    }
    else if (action.equals("add")) {
        // get parameters from the request
        String firstName = request.getParameter("firstName");
        String lastName  = request.getParameter("lastName");
        String email     = request.getParameter("email");

        // store data in User object
        User user = new User(firstName, lastName, email);

        // validate the parameters
        String message;
        if (firstName == null || lastName == null || email == null ||
            firstName.isEmpty() || lastName.isEmpty() || email.isEmpty()) {
            message = "Please fill out all three text boxes.";
            url = "/index.jsp";
        }
        else {
            message = "";
            url = "/thanks.jsp";
            UserDB.insert(user);
        }
        request.setAttribute("user", user);          // ④
        request.setAttribute("message", message);
    }

    // forward request and response objects to specified URL
    getServletContext()                              // ⑤
        .getRequestDispatcher(url)
        .forward(request, response);
}
```

**Vì sao khuôn này dễ bảo trì:**

- **Biết ngay mình đang ở bước nào.** Ai đọc controller nào cũng thấy cùng một bố cục.
- **Một lối ra duy nhất.** Không có `forward` rải rác trong từng nhánh, nên không có lỗi "forward hai lần" hay "quên forward". Muốn biết trang nào được hiển thị thì chỉ cần dò xem biến `url` được gán ở đâu.
- **Giá trị mặc định đặt trước.** Nhánh nào quên gán `url` thì vẫn có trang hợp lệ. `action == null` được xử lý ngay đầu, nên các nhánh sau không phải kiểm tra null nữa.
- **Mỗi nhánh tự chứa đủ.** Đọc nhánh `"add"` không cần biết nhánh `"join"` làm gì.

### 3.2 Khi controller lớn lên: tách mỗi nhánh thành một helper trả về URL

Ở Music Store (chương 22), controller có nhiều chức năng. Murach **không** dồn hết vào `doGet` mà:

1. `doGet` / `doPost` chỉ còn là **bảng điều phối** (dispatcher) phẳng.
2. Mỗi nhánh gọi một **private method trả về `String url`**.
3. Lối ra vẫn chỉ có một.

```java
@Override
public void doGet(HttpServletRequest request,
                  HttpServletResponse response)
                  throws ServletException, IOException {

    String requestURI = request.getRequestURI();
    String url;
    if (requestURI.endsWith("/listen")) {
        url = listen(request, response);
    } else {
        url = showProduct(request, response);
    }
    getServletContext()
        .getRequestDispatcher(url)
        .forward(request, response);
}

@Override
public void doPost(HttpServletRequest request,
                   HttpServletResponse response)
                   throws ServletException, IOException {

    String requestURI = request.getRequestURI();
    String url = "/catalog";
    if (requestURI.endsWith("/register")) {
        url = registerUser(request, response);
    }
    getServletContext()
        .getRequestDispatcher(url)
        .forward(request, response);
}
```

**Điểm tinh tế:**

- `doGet` lo các thao tác **chỉ đọc** (xem sản phẩm, nghe thử), `doPost` lo thao tác **ghi** (đăng ký user). Sách lưu ý việc `doGet` gọi thẳng `doPost` cho mọi thứ là *"a potential security risk"*, nên khi ứng dụng lớn hãy tách GET và POST theo đúng ngữ nghĩa.
- Tên helper là **động từ mô tả hành động**: `showProduct`, `listen`, `registerUser`. Đọc dispatcher giống như đọc mục lục.
- Helper **trả về URL** chứ không tự forward, nên quyền quyết định lối ra vẫn nằm ở một chỗ.

### 3.3 Bên trong helper: kiểm tra và thoát sớm, rồi đi thẳng

Method `listen` (chương 22) cho thấy cách xử lý các điều kiện tiên quyết:

```java
private String listen(HttpServletRequest request,
                      HttpServletResponse response) {
    HttpSession session = request.getSession();
    User user = (User) session.getAttribute("user");

    // if the User object doesn't exist, check for the email cookie
    if (user == null) {
        Cookie[] cookies = request.getCookies();
        String emailAddress =
            CookieUtil.getCookieValue(cookies, "emailCookie");

        // if the email cookie doesn't exist, go to the registration page
        if (emailAddress == null || emailAddress.equals("")) {
            return "/catalog/register.jsp";
        }
        user = UserDB.selectUser(emailAddress);
        // if a user for that email isn't in the database,
        // go to the registration page
        if (user == null) {
            return "/catalog/register.jsp";
        }
        session.setAttribute("user", user);
    }

    // happy path
    Product product = (Product) session.getAttribute("product");
    Download download = new Download();
    download.setUser(user);
    download.setProductCode(product.getCode());
    DownloadDB.insert(download);

    return "/catalog/" + product.getCode() + "/sound.jsp";
}
```

**Phân tích phong cách:**

1. **Lấy dữ liệu trước, quyết định sau.** Dòng đầu luôn là "lấy cái mình cần".
2. **Mỗi điều kiện thất bại thì `return` ngay** (guard clause). Không có `else` lồng nhiều tầng.
3. **Comment nói "nếu… thì đi đâu"**, viết bằng ngôn ngữ nghiệp vụ, không diễn giải cú pháp.
4. **Phần cuối là happy path**, chạy thẳng không có nhánh.
5. **Việc chi tiết đẩy xuống helper** (`CookieUtil.getCookieValue`, `UserDB.selectUser`), nên method chính đọc như văn xuôi.

> Helper trả về URL được phép có nhiều `return` (thoát sớm). Còn ở `doGet`/`doPost` thì giữ **một lối ra**.
> Hai quy tắc không mâu thuẫn: tầng điều phối có một cửa ra, tầng chi tiết thì thoát sớm cho gọn.

### 3.4 Rẽ nhánh phẳng thay vì lồng sâu

Method `registerUser` cho thấy cách viết "hai trường hợp rõ ràng, rồi hội tụ":

```java
private String registerUser(HttpServletRequest request,
                            HttpServletResponse response) {
    HttpSession session = request.getSession();

    String firstName = request.getParameter("firstName");
    String lastName  = request.getParameter("lastName");
    String email     = request.getParameter("email");

    User user;
    if (UserDB.emailExists(email)) {
        user = UserDB.selectUser(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        UserDB.update(user);
    } else {
        user = new User();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        UserDB.insert(user);
    }
    // both branches converge here
    session.setAttribute("user", user);

    Cookie emailCookie = new Cookie("emailCookie", email);
    emailCookie.setMaxAge(60 * 60 * 24 * 365 * 2);
    emailCookie.setPath("/");
    response.addCookie(emailCookie);

    Product product = (Product) session.getAttribute("product");
    String url = "/catalog/" + product.getCode() + "/sound.jsp";
    return url;
}
```

**Bài học:**

- **Khai báo biến trước nhánh** (`User user;`), mỗi nhánh gán giá trị, sau đó **hội tụ** lại một dòng chảy chung.
- **Tách khối bằng dòng trống**: đọc tham số / quyết định insert hay update / cookie / tính URL. Mỗi khối là một "đoạn văn".
- **Biểu thức tự giải thích**: `60 * 60 * 24 * 365 * 2` (2 năm) dễ hiểu hơn `63072000`.
- Lặp 3 dòng `setXxx` ở hai nhánh **được chấp nhận** vì nó giúp mỗi nhánh đọc độc lập được. Murach ưu tiên rõ ràng hơn DRY ở mức vài dòng. (**[Ngoài sách]** Nếu số dòng lặp lớn dần, tách thành `fillUser(user, request)`.)

### 3.5 Mọi method cùng loại phải có cùng "hình dạng"

Đây là kỹ thuật dễ đọc **mạnh nhất** trong sách. Tất cả method của lớp DB (JDBC) đều có đúng bố cục sau:

```
1. Lấy tài nguyên       pool, connection
2. Khai báo null        ps, rs
3. Viết câu SQL         String query = ...
4. try:    prepare → set tham số → execute → map kết quả → return
5. catch:  log lỗi → return giá trị "thất bại" (0 / null / false)
6. finally: đóng rs → đóng ps → trả connection
```

```java
public static User selectUser(String email) {
    ConnectionPool pool = ConnectionPool.getInstance();      // 1
    Connection connection = pool.getConnection();
    PreparedStatement ps = null;                             // 2
    ResultSet rs = null;

    String query = "SELECT * FROM User "                     // 3
                 + "WHERE Email = ?";
    try {                                                    // 4
        ps = connection.prepareStatement(query);
        ps.setString(1, email);
        rs = ps.executeQuery();
        User user = null;
        if (rs.next()) {
            user = new User();
            user.setFirstName(rs.getString("FirstName"));
            user.setLastName(rs.getString("LastName"));
            user.setEmail(rs.getString("Email"));
        }
        return user;
    } catch (SQLException e) {                               // 5
        System.out.println(e);
        return null;
    } finally {                                              // 6
        DBUtil.closeResultSet(rs);
        DBUtil.closePreparedStatement(ps);
        pool.freeConnection(connection);
    }
}
```

**Vì sao hiệu quả:**

- Người đọc **nhận ra mẫu** chỉ sau một method, nên các method sau chỉ cần nhìn **phần khác biệt** (câu SQL và đoạn map dữ liệu).
- Lỗi thiếu đóng tài nguyên **lộ ra ngay** vì `finally` luôn nằm cùng một vị trí.
- Sách ghi rõ: *"if you understand this code, you shouldn't have much trouble understanding how the other classes in the data access layer work since they all follow the same pattern."*

**[Ngoài sách]** Từ Java 7 trở đi có thể dùng *try-with-resources* để rút gọn bước 6. Tinh thần vẫn vậy: mọi method cùng một hình dạng.

### 3.6 Method nhỏ gọi method nhỏ

Phiên bản JPA của `emailExists`:

```java
public static boolean emailExists(String email) {
    User u = selectUser(email);
    return u != null;
}
```

- Không viết lại truy vấn, **tái dùng** method đã có.
- Hai dòng, đọc là hiểu, không cần comment.

### 3.7 Đẩy chi tiết lặp đi lặp lại xuống utility

Đóng một `ResultSet` an toàn cần: kiểm tra null, gọi `close()`, bắt `SQLException`. Lặp ở mọi method thì rất rối, nên Murach gom vào `DBUtil`:

```java
public class DBUtil {
    public static void closeResultSet(ResultSet rs) {
        try {
            if (rs != null) {
                rs.close();
            }
        } catch (SQLException e) {
            System.out.println(e);
        }
    }
    // closeStatement, closePreparedStatement viết y hệt
}
```

Kết quả: khối `finally` của mọi method DB chỉ còn **3 dòng đọc như câu văn**.

Cùng tinh thần đó:
- `CookieUtil.getCookieValue(cookies, name)` thay cho vòng lặp tìm cookie.
- `DBUtil.getEmFactory()` (JPA) gói việc tạo `EntityManagerFactory`.
- `ConnectionPool.getInstance()` (singleton) gói việc lookup JNDI.

> **Quy tắc:** nếu đoạn code "kỹ thuật" (không phải nghiệp vụ) xuất hiện từ 2 lần trở lên, hãy gom vào lớp `XxxUtil`
> với **static method có tên là động từ** (`closeResultSet`, `getCookieValue`).

### 3.8 Comment kiểu "đánh dấu bước"

Comment trong sách **ngắn, viết thường, nói ý định của khối code**, không diễn giải từng dòng:

```java
// get current action
// perform action and set URL to appropriate page
// get parameters from the request
// store data in User object and save User object in database
// set User object in request object and set URL
// forward request and response objects to specified URL
```

- Đọc **riêng các comment** cũng thấy được thuật toán, giống mục lục của method.
- Comment cuối dòng chỉ dùng cho **giá trị đặc biệt**: `action = "join"; // default action`, `url = "/thanks.jsp"; // the "thanks" page`.
- Trong XML/JSP cũng vậy: `<!-- servlet definitions (alphabetical by complete class name) -->`, `<!-- start the middle column -->`.

### 3.9 Đặt tên khớp xuyên suốt các tầng

Một dữ liệu đi qua các tầng nên giữ **cùng một tên**:

```
<input name="firstName">                     ← HTML form
request.getParameter("firstName")            ← Controller
String firstName                             ← biến cục bộ
user.setFirstName(firstName)                 ← JavaBean property
${user.firstName}                            ← EL trong JSP
rs.getString("FirstName")                    ← cột DB
```

Grep `firstName` là thấy toàn bộ đường đi của dữ liệu. Không đổi tên giữa chừng (`fname`, `first`, `name1`).

### 3.10 Tóm tắt phong cách tuần tự

| Làm | Tránh |
|-----|-------|
| Đặt mặc định ở đầu method | Kiểm tra null rải rác khắp nơi |
| `if / else if` phẳng theo action | `if` lồng 3–4 tầng |
| Guard clause `return` sớm trong helper | Nhánh `else` khổng lồ chứa happy path |
| Một `forward` duy nhất ở cuối controller | `forward` trong từng nhánh |
| Helper trả về giá trị (URL, object) | Helper tự làm side effect lối ra |
| Khối code tách bằng dòng trống + comment ý định | Một khối code 40 dòng liền mạch |
| Mọi method cùng loại cùng hình dạng | Mỗi method viết một kiểu |
| Biến khai báo gần nơi dùng, tên đầy đủ | `tmp`, `x`, `obj1` |
| Gom code kỹ thuật lặp vào `Util` | Copy-paste try/catch đóng tài nguyên |

---

## 4. Quy tắc theo từng tầng

### 4.1 Model: JavaBean (`business` package)

Ba quy tắc bắt buộc:

1. **Constructor không tham số.** Khởi tạo String thành `""` để tránh `null` (*"usually what you want"*).
2. **Getter/setter cho mọi property** theo chuẩn `getXxx` / `setXxx`; với boolean dùng `isXxx` / `setXxx`.
3. **`implements Serializable`.**

```java
package murach.business;

import java.io.Serializable;

public class User implements Serializable {

    private String firstName;
    private String lastName;
    private String email;

    public User() {
        firstName = "";
        lastName = "";
        email = "";
    }

    public User(String firstName, String lastName, String email) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
    }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    // ... tương tự
}
```

**Method tiện ích trong bean** là chỗ đặt logic nhỏ gắn với dữ liệu, để JSP chỉ việc gọi:

```java
public String getPriceCurrencyFormat() {
    NumberFormat currency = NumberFormat.getCurrencyInstance();
    return currency.format(price);
}

public String getArtistName() {
    String artistName =
        description.substring(0, description.indexOf(" - "));
    return artistName;
}
```

- Format tiền, tính tổng (`getInvoiceTotal`), tách chuỗi đều nằm ở **bean**, không nằm ở JSP hay controller.
- Đặt tên `getXxx` để JSP gọi được bằng `${product.priceCurrencyFormat}`.
- Gán vào **biến có tên** rồi mới `return` (`String artistName = ...; return artistName;`), giúp đọc và debug dễ hơn.
- Bean có thể chứa bean khác (`Invoice` chứa `User` và `List<LineItem>`).

### 4.2 Data access (`data` package)

- Mỗi bảng/business object có **một lớp `XxxDB`**.
- **Toàn static method**, không có instance variable hay constructor.
- Tên method theo CRUD: `insert(obj)`, `update(obj)`, `delete(obj)`, `selectXxx(key)`, `selectXxxs()`, `xxxExists(key)`.
- **Ghi**: nhận cả business object (`insert(User user)`). **Đọc**: nhận một khoá định danh (`selectUser(String email)`), có thể overload (`selectProduct(String code)` / `selectProduct(long id)`).
- Lấy cột **theo tên, không theo chỉ số**: `rs.getString("FirstName")` thay vì `rs.getString(1)`. Sách: *"column names lead to code that's easier to read and understand"*.
- **Luôn dùng `PreparedStatement`**, không bao giờ cộng chuỗi SQL.
- Connection lấy từ **pool** (`ConnectionPool` singleton), cấu hình trong `context.xml`.

**JPA (chương 13)** vẫn theo cùng khuôn, chỉ ngắn hơn:

```java
public static void insert(User user) {
    EntityManager em = DBUtil.getEmFactory().createEntityManager();
    EntityTransaction trans = em.getTransaction();
    trans.begin();
    try {
        em.persist(user);
        trans.commit();
    } catch (Exception e) {
        System.out.println(e);
        trans.rollback();
    } finally {
        em.close();
    }
}
```

- `EntityManagerFactory` là thread-safe nên dùng **static, một instance**.
- `EntityManager` **không** thread-safe nên **tạo mới trong mỗi method**, đóng trong `finally`.
- Ghi dữ liệu thì luôn nằm trong transaction: `begin → thao tác → commit`, lỗi thì `rollback`.
- Truy vấn dùng **tham số có tên** (`:email` + `setParameter`), không cộng chuỗi.

### 4.3 Controller: Servlet (`controllers` package)

- Đặt tên `XxxController` (Music Store) hoặc `XxxServlet` (ví dụ nhỏ).
- **Override `doGet` / `doPost`, không override `service`.**
- **Không có instance variable** vì một servlet chỉ có một instance cho mọi thread, nên instance variable không thread-safe. Sách: *"you should never code instance variables for a servlet"*.
- Một controller phụ trách **một nhóm URL** (`/catalog/*`, `/order/*`, `/user/*`, `/admin/*`).
- Theo khuôn 5 bước ở mục 3.1; tách helper trả về URL như mục 3.2.
- Không viết HTML trong servlet. Servlet `forward` sang JSP.
- `init()` dùng cho khởi tạo một lần. Không đặt việc quan trọng trong `destroy()` vì server crash thì nó không chạy.

### 4.4 View: JSP

- **Không scriptlet, không declaration.** Sách khuyên có thể **tắt scripting cho toàn ứng dụng ngay từ đầu**:

```xml
<jsp-config>
    <jsp-property-group>
        <url-pattern>*.jsp</url-pattern>
        <scripting-invalid>true</scripting-invalid>
    </jsp-property-group>
</jsp-config>
```

- Dùng **EL** để đọc dữ liệu (`${user.firstName}`) và **JSTL** cho điều kiện/vòng lặp (`<c:if>`, `<c:forEach>`, `<c:choose>`).
- **Escape output** bằng `<c:out value="${...}"/>` để chống XSS, đặc biệt với dữ liệu người dùng nhập, cookie và header.
- **Include header/footer/sidebar** từ thư mục `includes/`. Sửa header một lần là áp dụng cho mọi trang.
- Dùng `<c:url>` cho link để đường dẫn đúng context.
- Đặt tên trang theo chức năng: `index.jsp`, `thanks.jsp`, `register.jsp`, `sound.jsp`, `error_404.jsp`, `error_java.jsp`.
- Khi markup lặp nhiều chỗ mà JSTL không đủ thì dùng **custom tag** để giảm trùng lặp.
- Định dạng giao diện để trong **CSS ngoài** (`styles/main.css`), HTML chỉ lo nội dung và cấu trúc.

Ví dụ trang dùng include (Music Store):

```jsp
<jsp:include page="/includes/header.jsp" />
<jsp:include page="/includes/column_left_home.jsp" />

<!-- start the middle column -->
<section>
    <h1>Welcome to Fresh Corn Records!</h1>
    <p>...</p>
</section>
<!-- end the middle column -->

<jsp:include page="/includes/column_right_news.jsp" />
<jsp:include page="/includes/footer.jsp" />
```

Trang chính chỉ còn **phần nội dung riêng**; khung trang nằm ở include.

### 4.5 Util, Listener, Filter

- **Listener** (`ServletContextListener`): khởi tạo dữ liệu toàn cục **một lần** khi app khởi động (ví dụ `currentYear` dùng ở footer, email chăm sóc khách hàng từ `context-param`). Đây là cách thread-safe để có biến toàn cục, thay cho instance variable trong servlet.
- **Filter**: dành cho *cross-cutting concerns*, tức những việc cắt ngang nhiều phần (log, nén, kiểm tra quyền, encoding). Đừng lặp các việc này trong từng servlet.
- **Cấu hình** (email, đường dẫn, tham số) để trong `web.xml` (`context-param` / `init-param`), không hard-code trong Java.

---

## 5. Quy ước đặt tên

| Thành phần | Quy ước | Ví dụ trong sách |
|-----------|---------|------------------|
| Package | chữ thường, theo tầng | `music.business`, `music.controllers`, `music.data`, `music.util` |
| Business class | danh từ số ít | `User`, `Product`, `Invoice`, `LineItem`, `Cart` |
| Data access class | `<Entity>DB` | `UserDB`, `ProductDB`, `InvoiceDB` |
| Controller | `<Area>Controller` / `<Feature>Servlet` | `CatalogController`, `EmailListServlet` |
| Utility | `<Topic>Util` | `DBUtil`, `CookieUtil`, `SQLUtil` |
| Listener | `<App>ContextListener` | `MusicStoreContextListener` |
| Method DB | động từ CRUD | `insert`, `update`, `delete`, `selectUser`, `selectProducts`, `emailExists` |
| Helper controller | động từ + đối tượng | `showProduct`, `registerUser`, `listen` |
| Getter tiện ích | `get<Thing><Format>` | `getPriceCurrencyFormat`, `getInvoiceTotal`, `getAddressHTMLFormat` |
| Boolean | `is<Xxx>` / `<xxx>Exists` | `isEmailUpdated`, `emailExists` |
| Biến/property | camelCase, đầy đủ nghĩa | `firstName`, `requestURI`, `emailCookie`, `productCode` |
| Tham số form | trùng tên property | `name="firstName"` |
| Cột DB | PascalCase | `FirstName`, `ProductCode`, `InvoiceDate` |
| JSP | chữ thường, theo chức năng | `index.jsp`, `thanks.jsp`, `register.jsp` |
| URL | chữ thường, theo nhóm | `/catalog/product/*`, `/order/showCart`, `/user/deleteCookies` |
| Debug message | `TênServlet tênBiến: giá trị` | `EmailListServlet email: jsmith@gmail.com` |

---

## 6. Validation, xử lý lỗi, bảo mật

### 6.1 Validation

- **Validate ở cả client lẫn server**. Server là bắt buộc vì JavaScript có thể bị tắt, và một số kiểm tra (email đã tồn tại chưa) chỉ server làm được.
- Khi dữ liệu sai, **forward về đúng trang nhập liệu kèm `message`** và **giữ lại giá trị hợp lệ** đã nhập (`value="${user.email}"`), để người dùng không phải gõ lại.
- Chuyển kiểu (String sang int) thì đặt trong `try/catch`; khi lỗi, set `message` và chọn `url` trang nhập.

### 6.2 Xử lý lỗi

- Trong lớp DB: `catch` thì **log** rồi **trả về giá trị trung tính** (`0`, `null`, `false`). Tầng trên chỉ cần kiểm tra giá trị trả về.
- `finally` **luôn** dọn tài nguyên.
- Cấu hình **trang lỗi tuỳ chỉnh** trong `web.xml` cho mã HTTP (404) và cho exception (`java.lang.Throwable`).
- Debug message nên kèm **tên servlet và tên biến** để dễ lần theo log.

### 6.3 Bảo mật

| Rủi ro | Cách sách khuyên |
|-------|------------------|
| SQL injection | `PreparedStatement` / JPQL có tham số, *"best practice whenever possible"* |
| XSS | `<c:out>` escape mọi output |
| Thao tác ghi bị gửi lại / lộ tham số | Dùng **POST** cho thao tác thay đổi dữ liệu, GET cho thao tác đọc |
| Session hijacking | Theo dõi session bằng **cookie**, không dùng URL rewriting (`<tracking-mode>COOKIE</tracking-mode>`) |
| Truy cập trang admin | `security-constraint` + role trong `web.xml` |
| Mật khẩu | Không lưu clear-text; hash (chương 17) |

---

## 7. Trình bày code

- **Thụt lề, khoảng trắng và dòng trống** để tách khối logic (chương 4 nhắc điều này cho cả CSS/HTML; trong code Java của sách cũng vậy).
- Tham số dài thì **xuống dòng, căn thẳng**:

```java
protected void doPost(HttpServletRequest request,
                      HttpServletResponse response)
                      throws ServletException, IOException {
```

- Chuỗi gọi method **mỗi lời gọi một dòng**:

```java
getServletContext()
    .getRequestDispatcher(url)
    .forward(request, response);
```

- SQL dài thì **nối theo mệnh đề**, mỗi mệnh đề một dòng:

```java
String query = "INSERT INTO User (Email, FirstName, LastName) "
             + "VALUES (?, ?, ?)";
```

- Getter/setter, field: **mỗi cái một khối**, thứ tự field → constructor → getter/setter → method tiện ích.
- Trong `web.xml`: nhóm theo loại, có comment đầu nhóm, **sắp xếp theo alphabet** (*"servlet definitions (alphabetical by complete class name)"*).
- Trong CSS: luôn có dấu `;` ở khai báo cuối block, dù không bắt buộc.

---

## 8. Bảng anti-pattern

| Không làm | Vì sao (theo sách) | Thay bằng |
|-----------|--------------------|-----------|
| Model 1: JSP xử lý mọi thứ | *"generally considered a bad practice"* | MVC: Servlet + JavaBean + JSP |
| Java (scriptlet) trong JSP | *"unwieldy mix of Java and HTML"* | EL + JSTL, tắt scripting |
| Declaration `<%! %>` trong JSP | *"not a good practice"* | Logic để ở servlet/bean |
| Override `service()` | *"bad practice"* | Override `doGet` / `doPost` |
| Instance variable trong servlet | Không thread-safe, mất dữ liệu | Biến cục bộ, session, listener / context |
| Cộng chuỗi SQL với dữ liệu người dùng | SQL injection | `PreparedStatement`, tham số JPQL |
| In output không escape | XSS | `<c:out>` |
| Không đóng tài nguyên DB | Rò rỉ connection | `finally` + `DBUtil` |
| Dùng chung `EntityManager` giữa các method | Không thread-safe, hỏng dữ liệu | Tạo mới trong mỗi method |
| `rs.getString(1)` | Khó đọc | `rs.getString("FirstName")` |
| Copy header/footer vào mọi trang | Khó sửa đồng loạt | `<jsp:include>` / `<c:import>` |
| Định dạng bằng thuộc tính HTML / table layout | Trộn trình bày với nội dung | CSS ngoài |
| URL rewriting cho session | Lộ session ID | Cookie |
| Logic quan trọng trong `destroy()` | Không chạy nếu server crash | Lưu ngay khi xử lý |
| Chỉ validate ở client | Có thể bỏ qua | Validate thêm ở server |

---

## 9. Template mẫu để copy

### 9.1 Controller

```java
package app.controllers;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import app.business.*;
import app.data.*;

public class XxxController extends HttpServlet {

    // NO instance variables

    @Override
    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
                      throws ServletException, IOException {

        String requestURI = request.getRequestURI();
        String url = "/xxx/index.jsp";                 // default view

        // dispatch read-only actions
        if (requestURI.endsWith("/show")) {
            url = showXxx(request, response);
        } else if (requestURI.endsWith("/list")) {
            url = listXxx(request, response);
        }

        // forward request and response objects to specified URL
        getServletContext()
            .getRequestDispatcher(url)
            .forward(request, response);
    }

    @Override
    public void doPost(HttpServletRequest request,
                       HttpServletResponse response)
                       throws ServletException, IOException {

        String requestURI = request.getRequestURI();
        String url = "/xxx/index.jsp";                 // default view

        // dispatch actions that modify data
        if (requestURI.endsWith("/add")) {
            url = addXxx(request, response);
        } else if (requestURI.endsWith("/update")) {
            url = updateXxx(request, response);
        }

        // forward request and response objects to specified URL
        getServletContext()
            .getRequestDispatcher(url)
            .forward(request, response);
    }

    private String addXxx(HttpServletRequest request,
                          HttpServletResponse response) {
        // get parameters from the request
        String name  = request.getParameter("name");
        String email = request.getParameter("email");

        // store data in business object
        Xxx xxx = new Xxx();
        xxx.setName(name);
        xxx.setEmail(email);
        request.setAttribute("xxx", xxx);

        // validate the parameters
        if (name == null || name.isEmpty() ||
            email == null || email.isEmpty()) {
            request.setAttribute("message", "Please fill out all fields.");
            return "/xxx/index.jsp";
        }
        if (XxxDB.emailExists(email)) {
            request.setAttribute("message", "This email already exists.");
            return "/xxx/index.jsp";
        }

        // save to the database
        XxxDB.insert(xxx);
        request.setAttribute("message", "");
        return "/xxx/thanks.jsp";
    }

    // showXxx, listXxx, updateXxx ... cùng hình dạng
}
```

### 9.2 Lớp DB (JDBC)

```java
package app.data;

import java.sql.*;
import java.util.*;
import app.business.Xxx;

public class XxxDB {

    public static int insert(Xxx xxx) {
        ConnectionPool pool = ConnectionPool.getInstance();
        Connection connection = pool.getConnection();
        PreparedStatement ps = null;

        String query = "INSERT INTO Xxx (Name, Email) "
                     + "VALUES (?, ?)";
        try {
            ps = connection.prepareStatement(query);
            ps.setString(1, xxx.getName());
            ps.setString(2, xxx.getEmail());
            return ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e);
            return 0;
        } finally {
            DBUtil.closePreparedStatement(ps);
            pool.freeConnection(connection);
        }
    }

    public static Xxx selectXxx(String email) {
        ConnectionPool pool = ConnectionPool.getInstance();
        Connection connection = pool.getConnection();
        PreparedStatement ps = null;
        ResultSet rs = null;

        String query = "SELECT * FROM Xxx "
                     + "WHERE Email = ?";
        try {
            ps = connection.prepareStatement(query);
            ps.setString(1, email);
            rs = ps.executeQuery();
            Xxx xxx = null;
            if (rs.next()) {
                xxx = new Xxx();
                xxx.setName(rs.getString("Name"));
                xxx.setEmail(rs.getString("Email"));
            }
            return xxx;
        } catch (SQLException e) {
            System.out.println(e);
            return null;
        } finally {
            DBUtil.closeResultSet(rs);
            DBUtil.closePreparedStatement(ps);
            pool.freeConnection(connection);
        }
    }

    public static boolean emailExists(String email) {
        Xxx xxx = selectXxx(email);
        return xxx != null;
    }
}
```

### 9.3 Lớp DB (JPA)

```java
public class XxxDB {

    public static void insert(Xxx xxx) {
        EntityManager em = DBUtil.getEmFactory().createEntityManager();
        EntityTransaction trans = em.getTransaction();
        trans.begin();
        try {
            em.persist(xxx);
            trans.commit();
        } catch (Exception e) {
            System.out.println(e);
            trans.rollback();
        } finally {
            em.close();
        }
    }

    public static Xxx selectXxx(String email) {
        EntityManager em = DBUtil.getEmFactory().createEntityManager();
        String qString = "SELECT x FROM Xxx x "
                       + "WHERE x.email = :email";
        TypedQuery<Xxx> q = em.createQuery(qString, Xxx.class);
        q.setParameter("email", email);
        try {
            return q.getSingleResult();
        } catch (NoResultException e) {
            return null;
        } finally {
            em.close();
        }
    }
}
```

### 9.4 JSP

```jsp
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<jsp:include page="/includes/header.jsp" />

<h1>Join our email list</h1>

<c:if test="${not empty message}">
    <p><i><c:out value="${message}" /></i></p>
</c:if>

<form action="<c:url value='/xxx/add' />" method="post">
    <label>Name:</label>
    <input type="text" name="name" value="<c:out value='${xxx.name}' />" required><br>
    <label>Email:</label>
    <input type="email" name="email" value="<c:out value='${xxx.email}' />" required><br>
    <label>&nbsp;</label>
    <input type="submit" value="Join Now">
</form>

<jsp:include page="/includes/footer.jsp" />
```

---

## 10. Checklist trước khi commit

**Cấu trúc**
- [ ] Lớp nằm đúng package (`business` / `controllers` / `data` / `util`)?
- [ ] JavaBean có constructor rỗng, getter/setter, `Serializable`?
- [ ] Lớp DB chỉ có static method, không có instance variable?
- [ ] Không có SQL trong controller, không có HTML trong servlet, không có Java trong JSP?

**Luồng logic tuần tự**
- [ ] Controller theo khuôn: mặc định → xác định action → rẽ nhánh phẳng → setAttribute → **một** forward?
- [ ] Giá trị mặc định (`url`, `action`) đặt ở đầu method?
- [ ] Không có `if` lồng quá 2 tầng? Có dùng guard clause / helper để làm phẳng chưa?
- [ ] Mỗi nhánh lớn đã tách thành helper có tên là động từ, trả về URL?
- [ ] Method mới có cùng "hình dạng" với các method cùng loại?
- [ ] Đọc riêng các comment có hiểu được các bước không?

**Đặt tên**
- [ ] Tên tham số form = tên property bean = tên biến?
- [ ] Đọc cột DB theo tên, không theo chỉ số?
- [ ] Không có tên viết tắt mơ hồ?

**An toàn và tài nguyên**
- [ ] Mọi SQL/JPQL đều dùng tham số?
- [ ] Mọi output trong JSP đã escape bằng `<c:out>`?
- [ ] Validate ở server, trả lại `message` và giữ dữ liệu đã nhập?
- [ ] Mọi tài nguyên DB đóng trong `finally`? `EntityManager` tạo mới và đóng trong từng method?
- [ ] Thao tác ghi dùng POST?
- [ ] Servlet không có instance variable?

**Tái sử dụng**
- [ ] Đoạn kỹ thuật lặp lại đã gom vào `XxxUtil`?
- [ ] Header/footer/sidebar dùng include?
- [ ] Cấu hình nằm trong `web.xml` / `context.xml`, không hard-code?

---

> **Một câu để nhớ:** *Code của Murach "nhàm chán" một cách có chủ đích. Mọi controller trông giống nhau,
> mọi lớp DB trông giống nhau, mọi method đọc từ trên xuống. Khi mọi thứ đều đoán trước được,
> người bảo trì chỉ cần chú ý vào phần thật sự khác biệt.*
