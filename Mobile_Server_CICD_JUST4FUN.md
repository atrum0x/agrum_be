# Agrum Backend: Mobile Server & CI/CD Deployment Guide

This guide details how to completely configure a spare Android device (e.g., Xperia 1) as a self-updating, battery-backed server running the full Spring Boot, PostgreSQL, and Redis stack. It includes automated GitHub deployments and public internet exposure via Serveo.

## Phase 1: Prerequisites

1. **On your PC:** Install ADB (Android Debug Bridge).
2. **On the Android Device:**
   * Enable **Developer Options** -> **USB Debugging**.
   * Install **Termux** from F-Droid (do NOT use the Google Play Store version).
   * Install **Termux:Boot** from F-Droid (required for auto-starting the server on device restart).

---

## Phase 2: Termux Environment Setup

Connect the phone to your PC via USB and open a terminal on your PC to set up the phone remotely.

### 1. Grant Storage Permissions

```bash
adb shell pm grant com.termux android.permission.WRITE_EXTERNAL_STORAGE
adb shell pm grant com.termux android.permission.READ_EXTERNAL_STORAGE
```

### 2. Start Termux and Setup SSH

Use ADB to launch Termux, set a password, and start the SSH daemon so you can type comfortably from your PC terminal.

```bash
adb shell "am start -n com.termux/.app.TermuxActivity"
adb shell "input text 'passwd' && input keyevent 66"
# (Type your new password on the phone screen)
adb shell "input text 'sshd' && input keyevent 66"
```

### 3. Connect via SSH from PC

Forward the SSH port over USB and log in:

```bash
adb forward tcp:8022 tcp:8022
ssh -p 8022 root@localhost
```

### 4. Install Required Packages

Run this inside your Termux SSH session:

```bash
pkg update -y
pkg install openjdk-21 postgresql redis openssh git maven dos2unix nano termux-api wget unzip ttyd -y
termux-setup-storage
```
```bash
# File Browser (Ports & Logs UI)
cd ~
wget [https://github.com/filebrowser/filebrowser/releases/download/v2.30.0/linux-arm64-filebrowser.tar.gz](https://github.com/filebrowser/filebrowser/releases/download/v2.30.0/linux-arm64-filebrowser.tar.gz)
tar -xvf linux-arm64-filebrowser.tar.gz filebrowser
chmod +x filebrowser
rm linux-arm64-filebrowser.tar.gz

# Pgweb (Database GUI)
wget [https://github.com/sosedoff/pgweb/releases/download/v0.14.1/pgweb_linux_arm64.zip](https://github.com/sosedoff/pgweb/releases/download/v0.14.1/pgweb_linux_arm64.zip)
unzip pgweb_linux_arm64.zip
mv pgweb_linux_arm64 pgweb
chmod +x pgweb
rm pgweb_linux_arm64.zip
```
---

## Phase 3: Database & Cache Initialization

Stay in your SSH session to initialize and configure PostgreSQL and Redis.

### 1. Start Services

```bash
initdb -D $PREFIX/var/lib/postgresql
pg_ctl -D $PREFIX/var/lib/postgresql -l$PREFIX/var/lib/postgresql/server.log start

# Start Redis with a bypass for the Android ARM64 kernel bug
redis-server --ignore-warnings ARM64-COW-BUG --daemonize yes
```

### 2. Configure PostgreSQL User and Database

```bash
# Create the superuser
createuser -s postgres

# Set the password for the postgres user
psql -d postgres -c "ALTER USER postgres WITH PASSWORD 'postgres';"

# Create the application database
createdb agrum_dev

# Grant ownership
psql -d postgres -c "ALTER DATABASE agrum_dev OWNER TO postgres;"
```

---

## Phase 4: GitHub Integration (CI/CD Prep)

To allow the phone to pull directly from your repository, generate an SSH key.

1. **Generate the key (leave passphrase empty):**

   ```bash
   ssh-keygen -t ed25519 -C "termux-server"
   ```

2. **Display and copy the public key:**

   ```bash
   cat ~/.ssh/id_ed25519.pub
   ```

3. **Add it to GitHub:** Go to GitHub -> **Settings** -> **SSH and GPG keys** -> **New SSH key** and paste the output.

4. **Test Connection & Clone:**

   ```bash
   ssh -T git@github.com
   cd ~
   git clone git@github.com:YOUR_GITHUB_USERNAME/agrum_be.git
   ```

---

## Phase 5: Environment Variables (.env)

Create your `.env` file on the phone:

```bash
nano ~/.env
```

Paste your configuration (ensure `CORS_ALLOWED_ORIGINS` is present):

```properties
DB_NAME=agrum_dev
DB_USER=postgres
DB_URL=jdbc:postgresql://localhost:5432/agrum_dev
DB_USERNAME=postgres
DB_PASSWORD=postgres
REDIS_HOST=localhost
REDIS_PORT=6379
SUPERUSER_USERNAME=superuser
SUPERUSER_EMAIL=superuser@system.local
SUPERUSER_PASSWORD=superuser
JWT_SECRET=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
JWT_EXP=86400000
CORS_ALLOWED_ORIGINS=*
```

Save and exit (`Ctrl+O`, `Enter`, `Ctrl+X`).

**CRITICAL:** Sanitize the file to prevent hidden Windows line-ending crashes:

```bash
dos2unix ~/.env
```

---

## Phase 6: The Automated Deployment Script

Create a script that pulls the latest code, builds it, and restarts the server automatically.

```bash
cat << 'EOF' > ~/deploy.sh
#!/data/data/com.termux/files/usr/bin/bash

APP_DIR="$HOME/agrum_be"
LOG_FILE="$HOME/app.log"

echo "🛑 Stopping existing server..."
pkill java || true

echo "📥 Pulling latest code from main..."
cd $APP_DIR
git fetch --all
git reset --hard origin/main

echo "🔨 Building the Spring Boot application..."
mvn clean package -DskipTests

echo "🚀 Starting the new server..."
dos2unix ~/.env
export $(cat ~/.env | xargs)

# Run the newly built JAR
nohup java -jar $APP_DIR/target/agrum-0.0.1-SNAPSHOT.jar > $LOG_FILE 2>&1 &

echo "✅ Deployment complete! Tailing logs... (Press Ctrl+C to exit)"
sleep 3
tail -f $LOG_FILE
EOF

chmod +x ~/deploy.sh
```

**To deploy updates in the future, just SSH in and run `./deploy.sh`.**

---

## Phase 7: Automating Everything on Boot (Including Public URL)

Configure Termux:Boot so that if the phone restarts, the database, cache, backend, and public internet tunnel all spin up automatically.

### 1. Create the Boot Directory

```bash
mkdir -p ~/.termux/boot
```

### 2. Create the Startup Script

```bash
cat << 'EOF' > ~/.termux/boot/start-server.sh
#!/data/data/com.termux/files/usr/bin/sh

# 1. Prevent Android CPU sleep
termux-wake-lock

# 2. Start SSH daemon (for remote management)
sshd

# 3. Start PostgreSQL
pg_ctl -D $PREFIX/var/lib/postgresql -l$PREFIX/var/lib/postgresql/server.log start

# 4. Start Redis with ARM64 kernel bug bypass
redis-server --ignore-warnings ARM64-COW-BUG --daemonize yes

# 5. Load environment variables and start Spring Boot
cd ~/agrum_be
dos2unix ~/.env
export $(cat ~/.env | xargs)
nohup java -jar target/agrum-0.0.1-SNAPSHOT.jar > ~/app.log 2>&1 &

# 6. Start Lightweight GUIs
nohup ~/filebrowser -a 0.0.0.0 -p 8082 -r ~ > ~/filebrowser.log 2>&1 &
nohup ~/pgweb --url postgres://postgres:postgres@localhost:5432/agrum_dev --bind=0.0.0.0 --listen=8081 > ~/pgweb.log 2>&1 &
nohup ttyd -p 8083 -W -c admin:admin bash --login > ~/ttyd.log 2>&1 &

# 7. Start the Serveo public tunnel (Exposes port 8080 to the web)
# Automatically restarts if the connection drops
while true; do
    ssh -o StrictHostKeyChecking=no -R 80:localhost:8080 serveo.net
    sleep 10
done > ~/tunnel.log 2>&1 &
EOF
```

### 3. Make the Script Executable

```bash
chmod +x ~/.termux/boot/start-server.sh
```

### 4. Get Public URls And Local GUI links
```bash
cat << 'EOF' > ~/dashboard.sh
#!/data/data/com.termux/files/usr/bin/sh
IP=$(ip route show | awk '/wlan0/ {print $9}')
if [ -z "$IP" ]; then
    IP="10.220.13.44" # Fallback to your known IP
fi

echo "========================================="
echo "       AGRUM LOCAL SERVER DASHBOARD      "
echo "========================================="
echo "📁 Files & Logs : http://$IP:8082  (user: admin / admin)"
echo "🗄️ Database GUI : http://$IP:8081"
echo "🚀 Web Terminal : http://$IP:8083"
echo "🌍 Public API   : Check ~/tunnel.log for Serveo URL"
echo "========================================="
EOF
```
```
chmod +x ~/dashboard.sh
```

> **Final Step:** Open the **Termux:Boot** app once manually on your phone screen to initialize its permissions. Restart the phone to verify the entire stack, including your public Serveo URL, boots completely hands-free! Check `~/tunnel.log` for your live public URL.


