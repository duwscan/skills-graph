## Skills Graph – Laravel 12

Ứng dụng này là một dự án Laravel 12 dùng để xây dựng **skills graph** (mạng lưới kỹ năng), với các model như `Skill`, `SkillAlias`, `SkillRelationship`, `SkillCoOccurrence`, v.v. Dự án sử dụng Laravel AI SDK và một số công cụ phát triển đi kèm (Boost, Pint, Pail, Sail).

### Yêu cầu hệ thống

- **PHP**: ^8.2
- **Composer**
- **Node.js + npm** (để build frontend, chạy Vite)
- **SQLite** (mặc định) hoặc một database khác nếu bạn tự cấu hình trong `.env`

### Cài đặt & setup nhanh

1. **Clone dự án**

```bash
git clone <repo-url> skills-graph
cd skills-graph
```

2. **Chạy lệnh setup tự động**

Lệnh này sẽ:
- cài đặt PHP dependencies,
- tạo file `.env` nếu chưa có (copy từ `.env.example`),
- generate `APP_KEY`,
- chạy migrations,
- cài đặt npm packages,
- build assets.

```bash
composer setup
```

Nếu bạn muốn chạy từng bước thủ công, có thể thực hiện:

```bash
composer install
cp .env.example .env        # nếu chưa có .env
php artisan key:generate
php artisan migrate --force
npm install
npm run build
```

Mặc định, file `.env.example` đang dùng `DB_CONNECTION=sqlite`. Bạn có thể đổi sang MySQL/PostgreSQL nếu muốn, sau đó cập nhật lại thông tin kết nối và chạy lại migrations.

### Chạy môi trường development

Có 2 cách:

- **Cách 1 – composer script (khuyến nghị)**  
Chạy đồng thời PHP server, queue, log viewer và Vite:

```bash
composer dev
```

- **Cách 2 – thủ công**

```bash
php artisan serve
php artisan queue:listen --tries=1 --timeout=0
php artisan pail --timeout=0
npm run dev
```

Sau đó truy cập ứng dụng tại `http://localhost:8000` (hoặc port mà `php artisan serve` hiển thị).

### Chạy test

```bash
composer test
```

Hoặc chạy trực tiếp:

```bash
php artisan test
```

### Cấu trúc chính

- **`app/Models`**: chứa các model như `Skill`, `SkillAlias`, `SkillRelationship`, `SkillCoOccurrence`, `LocaleConfig`, ...
- **`database/migrations`**: migrations tạo bảng tương ứng cho các model trên.
- **`database/factories`**: factories phục vụ cho việc seed dữ liệu và viết test.
- **`routes/web.php`**: định nghĩa route web, route `/` mặc định trả về view `welcome`.

### Ghi chú thêm

- Dự án đã cài sẵn **Laravel Boost**, **Pint**, **Pail**, **Sail**, **PHPUnit** để hỗ trợ phát triển.
- Khi thay đổi frontend mà không thấy cập nhật, hãy chắc chắn bạn đã chạy `npm run dev` hoặc `npm run build` (hoặc `composer dev`).
- Để cấu hình AI provider (Laravel AI SDK), xem thêm trong `.env` các biến `AI_CUSTOM_*` và tài liệu nội bộ của dự án.
