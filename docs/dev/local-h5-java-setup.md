# Local H5 + Java Setup

This migration uses local MySQL and Redis services. Do not use Docker for this project unless the project direction changes.

## Local Services

Expected local tools:

- JDK 21
- Maven
- Node + npm
- MySQL, preferably MySQL 8.4 LTS; the current machine has Homebrew `mysql@8.0`
- Redis

On this machine, Homebrew tools are under `/opt/homebrew/bin`, and JDK 21 is available at:

```text
/Users/heidashuai/Library/Java/JavaVirtualMachines/temurin-21.0.7/Contents/Home
```

## Prepare MySQL and Redis

If MySQL and Redis are already installed, start them:

```bash
/opt/homebrew/bin/brew services start mysql@8.0
/opt/homebrew/bin/brew services start redis
```

To create the development database and app user:

```bash
MYSQL_ADMIN_USER=root MYSQL_ADMIN_PASSWORD='your-root-password' \
MYTHIC_DB_NAME=mythicrealm \
MYTHIC_DB_USERNAME=mythic \
MYTHIC_DB_PASSWORD=mythic \
scripts/dev/setup-local-services.sh
```

If the local root user has no password, omit `MYSQL_ADMIN_PASSWORD`.

## Project-Local MySQL 8.4 Option

If the global Homebrew MySQL requires an unknown admin password, run a project-local MySQL instance instead. This keeps data under `.local/mysql84-run` and listens on `127.0.0.1:3307`.

Initialize once:

```bash
mkdir -p .local/mysql84-run .local/logs
/opt/homebrew/opt/mysql@8.4/bin/mysqld \
  --initialize-insecure \
  --basedir=/opt/homebrew/opt/mysql@8.4 \
  --datadir="$PWD/.local/mysql84-run" \
  --log-error="$PWD/.local/logs/mysql84-run-init.log"
```

Start it:

```bash
/opt/homebrew/opt/mysql@8.4/bin/mysqld \
  --basedir=/opt/homebrew/opt/mysql@8.4 \
  --datadir="$PWD/.local/mysql84-run" \
  --port=3307 \
  --bind-address=127.0.0.1 \
  --socket="$PWD/.local/mysql84-run/mysql.sock" \
  --pid-file="$PWD/.local/mysql84-run/mysql.pid" \
  --mysqlx=0 \
  --log-error="$PWD/.local/logs/mysql84-run.log"
```

Create the app database:

```bash
/opt/homebrew/opt/mysql@8.4/bin/mysql -uroot --protocol=tcp --host=127.0.0.1 --port=3307 \
  -e "CREATE DATABASE IF NOT EXISTS mythicrealm CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; \
      CREATE USER IF NOT EXISTS 'mythic'@'localhost' IDENTIFIED BY 'mythic'; \
      CREATE USER IF NOT EXISTS 'mythic'@'127.0.0.1' IDENTIFIED BY 'mythic'; \
      GRANT ALL PRIVILEGES ON mythicrealm.* TO 'mythic'@'localhost'; \
      GRANT ALL PRIVILEGES ON mythicrealm.* TO 'mythic'@'127.0.0.1'; \
      FLUSH PRIVILEGES;"
```

## Run Backend

```bash
cd backend
JAVA_HOME=/Users/heidashuai/Library/Java/JavaVirtualMachines/temurin-21.0.7/Contents/Home \
PATH=/opt/homebrew/bin:$PATH \
MYTHIC_DB_USERNAME=mythic \
MYTHIC_DB_PASSWORD=mythic \
/opt/homebrew/bin/mvn spring-boot:run
```

When using the project-local MySQL instance, set:

```bash
MYTHIC_DB_URL='jdbc:mysql://127.0.0.1:3307/mythicrealm?useUnicode=true&characterEncoding=utf8&connectionTimeZone=SERVER'
```

The backend starts on:

```text
http://127.0.0.1:8080
```

Useful check:

```bash
curl http://127.0.0.1:8080/actuator/health
```

## Run H5 Client

```bash
cd web
/opt/homebrew/bin/npm install
/opt/homebrew/bin/npm run dev
```

The H5 client starts on:

```text
http://127.0.0.1:5173
```

Vite proxies `/api` to `http://127.0.0.1:8080`.
