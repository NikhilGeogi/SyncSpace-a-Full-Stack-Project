# SyncSpace — 在新電腦上用 Docker 啟動

這份文件是給「拿到 `SyncSpace-docker.zip`、想在自己電腦上把專案跑起來」的人看的。

**你只需要安裝 Docker，不需要安裝 JDK、Maven 或 MySQL。** 這三樣都在容器裡，Docker 會自己處理。

---

## 一、安裝 Docker

### Windows 10 / 11

1. 下載並安裝 [Docker Desktop for Windows](https://www.docker.com/products/docker-desktop/)
2. 安裝過程若提示啟用 **WSL 2**，請照做（Docker Desktop 需要它）
3. 安裝完**重新開機**
4. 從開始選單啟動 Docker Desktop，等右下角的鯨魚圖示不再轉動、狀態顯示 **Engine running**

> ⚠️ Docker Desktop 必須是**開著的**，指令才會有反應。這是最常見的第一個坑。

### macOS

下載 [Docker Desktop for Mac](https://www.docker.com/products/docker-desktop/)，注意選對晶片版本（Apple Silicon / Intel），安裝後啟動。

### Linux

```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER   # 加完要登出再登入
```

### 確認裝好了

打開終端機（Windows 用 PowerShell），執行：

```bash
docker --version
```

```bash
docker compose version
```

兩行都要有版本號印出來。若 `docker compose version` 失敗但 `docker-compose --version` 成功，代表你的是舊版 Compose v1 —— 底下所有 `docker compose` 指令請改成 `docker-compose`。

---

## 二、解壓縮

把 `SyncSpace-docker.zip` 解開，放到一個**路徑不含空白與中文**的位置，例如：

- Windows：`C:\projects\SyncSpace`
- macOS / Linux：`~/projects/SyncSpace`

然後用終端機切換到那個資料夾：

```bash
cd C:\projects\SyncSpace
```

確認你在對的位置 —— 這個資料夾裡應該看得到 `docker-compose.yml` 和 `Dockerfile`。

---

## 三、建立 `.env` 設定檔

專案附的是 `.env.example` 範本，要複製成 `.env` 才會生效。

Windows PowerShell：

```powershell
Copy-Item .env.example .env
```

macOS / Linux：

```bash
cp .env.example .env
```

接著用記事本或任何編輯器打開 `.env`，把 `DB_PASSWORD` 改成你自己的密碼：

```
DB_PASSWORD=改成你自己的密碼
DB_NAME=test
APP_PORT=8080
DB_HOST_PORT=3307
```

> 沒建立 `.env` 或沒設 `DB_PASSWORD` 的話，Docker 會直接拒絕啟動並告訴你原因。這是刻意的，避免有人用空密碼把資料庫跑起來。

---

## 四、啟動

```bash
docker compose up -d --build
```

**第一次會比較慢**，這是正常的。Docker 要下載映像檔並編譯 Java 專案；應用程式啟動時會由 Flyway 建立或升級資料表。

1. 下載基礎映像檔（Maven、Java 執行環境、MySQL）
2. 編譯 Java 專案（下載 Spring Boot 的相依套件）
3. 啟動 MySQL 並執行 Flyway schema migrations

第二次之後因為有快取，只要幾秒鐘。

想看進度就開另一個終端機執行：

```bash
docker compose logs -f
```

---

## 五、確認成功

### 1. 兩個容器都健康

```bash
docker compose ps
```

`syncspace-db` 應該是 `Up (healthy)`，`syncspace-app` 應該是 `Up`。

### 2. 應用程式有啟動

```bash
docker compose logs app
```

要找到這一行：

```
Started FinalApplication in xx.xxx seconds
```

### 3. 資料表建好了

```bash
docker compose exec db mysql -uroot -p test -e "SHOW TABLES;"
```

輸入你在 `.env` 設的密碼後，應該列出 `blocks`、`flyway_schema_history`、`page_shares`、`pages`、`users` 等資料表。

（指令裡的 `test` 是資料庫名。若你在 `.env` 改過 `DB_NAME`，這裡跟著換成你設的名字。）

### 4. 網頁打得開

瀏覽器輸入：

```
http://localhost:8080/
```

註冊一個帳號，登入，建立一頁，新增幾個 block 試試看。中文字若顯示正常，代表 UTF-8 那條鏈整條都通了。

---

## 六、日常指令

| 指令 | 用途 |
| --- | --- |
| `docker compose up -d` | 啟動（已經 build 過就不用再加 `--build`） |
| `docker compose down` | 停止，**資料保留** |
| `docker compose down -v` | 停止，**連資料一起刪掉**（整組重來用這個） |
| `docker compose logs -f app` | 即時看應用程式日誌 |
| `docker compose ps` | 看容器狀態 |
| `docker compose restart app` | 只重啟應用程式 |
| `docker compose up -d --build` | 改過原始碼後重新編譯並套用 |

---

## 七、疑難排解

### `docker: command not found` / 指令沒反應

Docker Desktop 沒開。去開始選單啟動它，等圖示停止轉動再試。

### `port is already allocated` 或 `bind: address already in use`

某個埠被佔用了。打開 `.env` 改成沒人用的號碼，例如：

```
APP_PORT=8090
DB_HOST_PORT=3308
```

然後 `docker compose down` 再 `docker compose up -d`。改完網址也要跟著改成 `http://localhost:8090/`。

> 8080 常被其他開發伺服器佔用；3306 常被本機已安裝的 MySQL 佔用（所以預設才用 3307）。

### 應用程式一直重啟，日誌出現 `Communications link failure`

MySQL 還沒完全就緒。正常情況 compose 會等健康檢查通過才啟動應用程式，但機器很慢時仍可能發生。先看：

```bash
docker compose logs db
```

確認 MySQL 有 `ready for connections`，然後：

```bash
docker compose restart app
```

### 日誌出現 `Public Key Retrieval is not allowed`

代表 `application.properties` 的連線字串被改動過。它必須保留 `allowPublicKeyRetrieval=true&useSSL=false` 這兩個參數 —— MySQL 8 預設用 `caching_sha2_password`，少了它們在無 TLS 連線下拿不到公鑰。

### `SHOW TABLES` 是空的

先查看 Flyway 和應用程式日誌，確認 migration 的錯誤；不要刪除資料 volume：

```bash
docker compose logs app
```

### `--mount=type=cache` 相關錯誤

你的 Docker 版本太舊、沒有 BuildKit。兩個選擇：升級 Docker Desktop，或打開 `Dockerfile` 把這一行

```
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests clean package
```

改成

```
RUN mvn -B -DskipTests clean package
```

功能一樣，只是每次 build 都要重新下載相依套件、比較慢。

### 網頁中文變成亂碼

先確認 `Dockerfile` 裡的 `ENV LANG=C.UTF-8` 和 `-Dfile.encoding=UTF-8` 還在，然後 `docker compose up -d --build` 重建。

---

## 八、資料的備份與搬移

資料庫內容存在名為 `db-data` 的 Docker volume 裡，`docker compose down` 不會刪掉它，但**它不在專案資料夾內**，複製資料夾搬不走資料。

下面兩個指令裡的 `test` 都是資料庫名，若改過 `.env` 的 `DB_NAME` 請跟著替換。

### 匯出

```bash
docker compose exec db mysqldump -uroot -p test > backup.sql
```

### 匯入到另一台

先在新機器上完成上面一到四步、確認跑得起來，再執行：

```bash
docker compose exec -T db mysql -uroot -p test < backup.sql
```

---

## 九、想改回不用 Docker 的跑法？

原本的本機開發方式完全沒有被破壞。`application.properties` 裡的設定是 `${DB_HOST:localhost}` 這種「有預設值的佔位符」，不設任何環境變數時就等同於原本的 `localhost:3306/test` + `root` + 空密碼。

也就是說，在 NetBeans 裡直接按 Run 或執行 `mvnw spring-boot:run`，行為跟加入 Docker 之前一模一樣。詳見 [README.md](README.md) 的 Running the Project Locally 一節。

> macOS / Linux 的使用者請注意：從 zip 解壓出來的 `mvnw` 會失去執行權限，第一次用要先補回來（走 Docker 的話用不到這個檔案，可以略過）：
>
> ```bash
> chmod +x mvnw
> ```
