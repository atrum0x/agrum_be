# Hosting agrum on OVH (UAT + Production on one VPS)

How the backend is hosted, why it is set up this way, and what to do when something goes wrong.
Read the **Lockout rules** and **Things that will surprise you** sections before touching the server.

> Placeholders: `yourdomain` = the real domain, `<VPS-IP>` = the server's IP address.
>
> **Where things stand (2026-11-17):** UAT is live on a **UAT-only VPS** (2 vCPU, 3.7 GB RAM, Ubuntu 26.04), set up with section 5 up to 5.8. The diagram below is the plan for production on the bigger VPS-2. The domain is temporary; a new one will be bought, and then only the DNS records (5.6), the Coolify domains and `CORS_ALLOWED_ORIGINS` change.
> **Never put real passwords, keys or secrets in this file.** They live in the password manager (Bitwarden folder `agrum-hosting`).

---

## 1. The big picture

```
                         Internet
                            │  only ports 22 (SSH), 80, 443 are open (ufw)
                            ▼
┌──────────────────────── OVH VPS-2 (Ubuntu 24.04) ────────────────────────┐
│  Coolify (dashboard + Traefik proxy, gets HTTPS certificates itself)     │
│                                                                          │
│  Environment: production              Environment: uat                   │
│  ├─ app  (branch main)                ├─ app  (branch uat)               │
│  │   https://api.yourdomain           │   https://uat-api.yourdomain     │
│  ├─ PostgreSQL 16 (private)           ├─ PostgreSQL 16 (private)         │
│  └─ Redis/Valkey  (private)           └─ Redis/Valkey  (private)         │
└──────────────────────────────────────────────────────────────────────────┘
        │ nightly pg_dump (Coolify)                 
        ▼                                            
  S3 bucket in another region (off-site backups)

Web (Vercel):  app.yourdomain  → api.yourdomain
               uat-app.yourdomain → uat-api.yourdomain
Mobile app:    API_BASE_URL GitHub secret in agrum_mobile (currently → UAT)
```

| Item | Value |
|---|---|
| Provider | OVHcloud. Prod plan: VPS-2, 4 vCores, 8 GB RAM, 75 GB NVMe. **UAT now:** a separate VPS, 2 vCores, 3.7 GB RAM + 2 GB swap |
| Location | Singapore (or Mumbai), the closest to Sri Lanka |
| OS | Ubuntu 24.04 or newer (the UAT box runs 26.04; every step here works on both) |
| Control panel | `ca.ovh.com/manager` (the Asia/"world" zone, **not** Canada-only) |
| Backups | OVH Premium automatic backup (7 days, whole server) + Coolify Postgres backups to S3 (14 days) |
| Deploys | UAT: automatic on push to `uat`. Prod: manual "Deploy" button on `main` |

---

## 2. Ownership and billing

- The OVH account, domain and S3 bucket were opened **in the developer's name** to get started quickly.
- The **customer pays** the server in the end. This is done by moving the OVH **contacts**, not by putting the customer's credit card on the developer's account. A foreign card on your account means invoices in your name and possible fraud flags or suspension.

OVH has three contacts per service:

| Contact | Meaning | Now | After handover |
|---|---|---|---|
| Administrator | Owns the service | Developer | Customer (optional, at final handover) |
| Technical | Manages it, gets server emails | Developer | Developer |
| Billing | Gets invoices, pays renewals | Developer | **Customer** |

**How to switch billing to the customer:**
1. The customer signs up through **the same OVH site** used for this account (`ovhcloud.com/asia` or `/en-sg`).
   - After login, their control panel URL must be **`ca.ovh.com/manager`**, the same as ours. Accounts in different zones (EU `www.ovh.com`, US `us.ovhcloud.com`) **cannot** swap contacts.
2. They send their **NIC handle** (their OVH ID, shown in their account, e.g. `ab12345-ovh`).
3. Our control panel → My account → **Contacts management** → the VPS → change the **billing** contact to their NIC handle. Both sides confirm by email.
4. Repeat for the domain, the backup storage and any add-ons.
5. Until the switch, renewals hit **our** card. Agree a date with the customer.

Keep **auto-renew on**. An expired VPS is deleted after a grace period, with its backups.

---

## 3. Credentials inventory (names only; values are in Bitwarden)

| Bitwarden item | What it is | If lost |
|---|---|---|
| `agrum-vps ssh key` (secure note) | Private key + public key + passphrase | Get in through the KVM console, add a new key |
| `ovh account` | OVH login + 2FA | OVH account recovery (slow) |
| `vps ubuntu console password` | Password of user `ubuntu`, **only** for the KVM console | Rescue mode |
| `coolify admin` | Dashboard login + **2FA recovery codes** | Reset with an artisan command (section 9) |
| `prod db` / `uat db` | Postgres user + password per environment | Visible in Coolify |
| `prod jwt` / `uat jwt` | `JWT_SECRET` per environment | Generate a new one (logs everyone out) |
| `prod superuser` / `uat superuser` | `SUPERUSER_*` per environment | Change the env var and redeploy |
| `s3 backup bucket` | Access key + secret | Make new keys in the storage provider |
| `domain registrar` | Domain account login | Registrar account recovery |

**SSH keys are just files, not tied to a device.** Whoever has the private key file (and passphrase) can log in from any computer. Best practice is **one key per device** (laptop, PC, phone with Termux), each added as its own line in `~/.ssh/authorized_keys`. If a device is lost, delete that one line.

Restore a key on a new PC: create `C:\Users\<you>\.ssh\id_ed25519` (no `.txt`!), paste the private key text exactly, including the `-----BEGIN/END-----` lines and a final newline. Do the same for `id_ed25519.pub`.

---

## 4. 🔒 Lockout rules (read every time)

1. **Keep one SSH session open** while changing SSH or firewall settings, and test in a **second** terminal before closing the first.
2. **`sudo ufw allow 22/tcp` before `sudo ufw enable`.** Always.
3. **Run `sudo sshd -t` before `sudo systemctl restart ssh`.** No output means the config is valid.
4. **`PermitRootLogin prohibit-password`, never `no`.** Coolify manages the server by SSHing in as root with its own key. `no` breaks Coolify.
5. **The KVM console password must work.** Test it after any password change (OVH panel → VPS → KVM).
6. **Close port 8000 only after** `https://coolify.yourdomain` loads.
7. **Leave the OVH Network Firewall (in the control panel) off.** ufw is enough. Two firewalls double the chances of locking yourself out.
8. **Before risky changes:** press "Backup now" on the prod Postgres in Coolify (and take a snapshot if that add-on is bought).

### Emergency doors (in order)
1. **Second SSH key / second device.**
2. **KVM console:** OVH panel → VPS → KVM. Log in as `ubuntu` with the console password. Works even if SSH or the firewall is broken.
3. **Rescue mode:** OVH panel → VPS → Reboot in rescue mode. OVH emails temporary credentials. Mount the disk and fix files, e.g. `/etc/ssh/sshd_config.d/99-hardening.conf` or `ufw` (`/etc/ufw/ufw.conf` → `ENABLED=no`).
4. **Restore an OVH backup** (rolls back the **whole** server, UAT and prod together).

---

## 5. Setup log (how the server was built)

Steps in the order they were done. Use them to rebuild from scratch.

> [!IMPORTANT]
> **Know which terminal you are in.** Most mistakes come from typing a command in the wrong place.
>
> | The prompt looks like | You are on | Marked in this file as |
> |---|---|---|
> | `C:\Users\you>` or `PS C:\Users\you>` | **your PC** (Windows) | 💻 PC |
> | `ubuntu@vps-xxxx:~$` | **the server** (over SSH) | 🖥️ SERVER |
>
> Typing `exit` on the server takes you back to the PC.

### Editing files on the server (nano cheat sheet)

`nano` is the text editor on the server. Open a file with `nano <file>`, or with `sudo nano <file>` for system files under `/etc`.

| To do this | Press |
|---|---|
| Move around | Arrow keys (the mouse does **not** move the cursor) |
| **Paste** from Windows | **Right-click** in the terminal (or Shift+Insert). Ctrl+V does **not** work. |
| Copy text out of the server | Select it with the mouse; Windows Terminal copies on select or with Ctrl+Shift+C |
| Delete a whole line | Ctrl+K |
| **Save** | **Ctrl+O**, then **Enter** to confirm the file name |
| **Exit** | **Ctrl+X** (if it asks "Save modified buffer?", press **Y**, then **Enter**) |
| Quit without saving | Ctrl+X, then **N** |
| Search | Ctrl+W |

> [!TIP]
> Nothing appears on screen while you type a **password** or **passphrase** in the terminal. That's normal. Type it and press Enter.

---

### 5.0 Create an SSH key (💻 PC, once per device)

> [!NOTE]
> **What an SSH key is:** two files.
> - **Private key** `id_ed25519`: your secret. It **never** leaves the device. Never paste it anywhere except Bitwarden.
> - **Public key** `id_ed25519.pub`: one line starting with `ssh-ed25519`. It is safe to share, and it is **the one that goes on the server**.
>
> The server accepts your key only if your public key is in its `~/.ssh/authorized_keys` file. **It does not get there by itself.** Either you paste it into the OVH order form, or you add it by hand (5.2).

**Step 1. Do I already have one?**
```
💻 PC (cmd)
type %USERPROFILE%\.ssh\id_ed25519.pub
```
- It prints a line starting with `ssh-ed25519`: you have one. Go to step 3.
- It says "The system cannot find the file": go to step 2.

**Step 2. Create one**
```
💻 PC
ssh-keygen -t ed25519 -C "agrum-laptop"
```
- `Enter file in which to save the key`: just press **Enter**.
- `Overwrite (y/n)?`: answer **n**. It means you already have a key, and overwriting it locks you out of everything it was used for.
- `Enter passphrase`: type one (nothing shows on screen) and store it in Bitwarden.
- `-C "agrum-laptop"` is only a label, so you can tell keys apart in `authorized_keys`.

**Step 3. Copy the public key**
```
💻 PC
type %USERPROFILE%\.ssh\id_ed25519.pub          (cmd)
Get-Content $HOME\.ssh\id_ed25519.pub           (PowerShell)
```
Select the whole line (`ssh-ed25519 AAAA... agrum-laptop`) and copy it. It must stay **one line**.

**Step 4. Back it up.** In the Bitwarden item `agrum-vps ssh key`, store the text of `id_ed25519` **and** `id_ed25519.pub`, plus the passphrase. The files are in `C:\Users\<you>\.ssh\` (open them with Notepad).

Phone (Termux): run `pkg install openssh`, then `ssh-keygen -t ed25519 -C "agrum-phone"`, then `cat ~/.ssh/id_ed25519.pub`.

---

### 5.1 Order
- VPS-2, Singapore/Mumbai, Ubuntu 24.04 or newer.
- For **UAT alone**, the minimum is 2 vCPU, 4 GB RAM (3.7 GB usable) and 40 GB disk, **plus the swap file in 5.3**. Below 4 GB the Maven build during a deploy runs out of memory.
- Add-on: **Premium automatic backup** (daily, keeps 7 days). The snapshot add-on is optional.
- The VPS already includes a free daily backup that keeps only the last 24 hours.

> [!WARNING]
> **The SSH key field in the order is optional.** This server was ordered **without** a key. Its `~/.ssh/authorized_keys` started **empty**, and the first logins used the **password** from OVH's email. If `cat ~/.ssh/authorized_keys` shows nothing, the server does **not** know your key yet. Add it in 5.2.

---

### 5.2 First login + safety net

**Step 1. Log in** (with the password from OVH's email the first time)
```
💻 PC
ssh ubuntu@<VPS-IP>
```

**Step 2. Set the console password** (for the OVH KVM console, your emergency way in)
```bash
🖥️ SERVER
sudo passwd ubuntu
```
Save it in Bitwarden (`vps ubuntu console password`). **Test it now:** OVH panel → VPS → KVM → log in as `ubuntu`.

**Step 3. Does the server know my key?**
```bash
🖥️ SERVER
cat ~/.ssh/authorized_keys
```
- **Empty, or "No such file":** it doesn't. Do step 4.
- **Shows your `ssh-ed25519 ...` line** (the same as your `.pub` file): it does. Go to step 5.

**Step 4. Add your public key to the server**
```bash
🖥️ SERVER
mkdir -p ~/.ssh && chmod 700 ~/.ssh
nano ~/.ssh/authorized_keys
```
In nano:
1. **Right-click** to paste the `ssh-ed25519 ...` line from 5.0 step 3.
2. Press **Enter** so the line ends with a newline. Each key goes on **its own line**.
3. Press **Ctrl+O**, then **Enter** to save, then **Ctrl+X** to exit.

Then:
```bash
🖥️ SERVER
chmod 600 ~/.ssh/authorized_keys
cat ~/.ssh/authorized_keys          # check: one line per key, nothing broken across lines
```
> [!CAUTION]
> SSH silently ignores the file if the permissions are wrong (`~/.ssh` must be 700, the file 600), or if a key got split over two lines when you pasted it.

**Step 5. Test the key** in a **new** terminal. Keep the old session open.
```
💻 PC
ssh -o PasswordAuthentication=no ubuntu@<VPS-IP>
```
- ✅ It logs you in, asking at most for the key's **passphrase**: the key works.
- ❌ `Permission denied (publickey)`: the key isn't right. Redo step 4, and compare the line on the server with your `.pub` file character by character.

`-o PasswordAuthentication=no` refuses passwords, so this test can only pass with the key.

**Step 6. Second device key** (optional for now, but see the checklist below)
Create a key on the second device (5.0) and copy its `.pub` line. On the server, run `nano ~/.ssh/authorized_keys` and paste it on a **new line** under the first one. Save, exit, and test from that device.

> [!IMPORTANT]
> **✅ Checklist before 5.4.** 5.4 turns password login **off**. Skip an item and you can lock yourself out.
> - [ ] Step 5 passes: key login works from this PC.
> - [ ] The private key and passphrase are backed up in Bitwarden, or a second device's key is added and tested.
> - [ ] The KVM console password works (tested in the OVH panel).

---

### 5.3 Updates + firewall (🖥️ SERVER)
```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y ufw fail2ban unattended-upgrades
sudo dpkg-reconfigure -plow unattended-upgrades     # pick Yes
```
If `apt upgrade` opens a pink or purple screen asking about a config file or which services to restart, keep the defaults: press **Tab** to reach `<Ok>`, then **Enter**.

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp              # SSH. MUST come before "ufw enable"
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 8000/tcp            # temporary, for the first Coolify login
sudo ufw enable                    # answer y
sudo ufw status                    # should list 22, 80, 443 and 8000 as ALLOW
```
Test a new SSH session from the PC before you continue.

**Swap file (do this before the first deploy)**

> [!NOTE]
> **What swap is:** disk space the server uses as spare memory when RAM runs out. It's slow, but it stops a short memory spike from killing a process. The spike here is the **Maven build** on every deploy: it needs about 1–1.5 GB on top of Coolify, Postgres, Redis and the running app. On a 4 GB box, without swap, a deploy can fail with **"Killed"** or **exit code 137**.

```bash
🖥️ SERVER
sudo fallocate -l 2G /swapfile                       # make a 2 GB file
sudo chmod 600 /swapfile                             # only root may read it
sudo mkswap /swapfile                                # format it as swap
sudo swapon /swapfile                                # start using it now
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab   # and after every reboot
free -h                                              # the "Swap:" line must show 2.0Gi
```
Run the `echo ... /etc/fstab` line **only once**; running it twice adds a duplicate line. Check with `cat /etc/fstab`: `/swapfile` should appear one time.

What normal looks like on the 3.7 GB UAT box (Coolify dashboard → Servers → localhost → Metrics, or `free -h`):

| State | Memory used |
|---|---|
| Coolify + empty Postgres + Redis, no app | ~33% (≈1.2 GB) |
| UAT app running, idle | ~50–60% |
| During a deploy (Maven build) | can reach 70–85% for a few minutes; some swap in use is fine |

Worry if it stays above 85–90%, if `free -h` shows more than ~1 GB of swap used most of the time, or if deploys fail with "Killed" / 137. Then lower the app's memory limit (5.7) or move to a bigger VPS.

---

### 5.4 SSH hardening (🖥️ SERVER) ⚠️ the step that can lock you out

> [!WARNING]
> Do the **checklist at the end of 5.2** first. Keep your current SSH session **open** the whole time.

**Step 1. Create the hardening file**
```bash
sudo nano /etc/ssh/sshd_config.d/99-hardening.conf
```
Paste these three lines, then press **Ctrl+O**, **Enter**, **Ctrl+X**:
```
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin prohibit-password
```
> Use `prohibit-password`, **never** `no`: Coolify logs in as root with its own key.

**Step 2. Check the cloud-init file.** This is an Ubuntu 24.04+ trap: the file is read **first**, so its setting wins.
```bash
ls /etc/ssh/sshd_config.d/
sudo cat /etc/ssh/sshd_config.d/50-cloud-init.conf     # only if ls lists it
```
If it says `PasswordAuthentication yes`, open it with `sudo nano /etc/ssh/sshd_config.d/50-cloud-init.conf`, change `yes` to `no`, then save and exit.

**Step 3. Check the config, then restart SSH**
```bash
sudo sshd -t                                            # no output = OK. On any error, fix it first and do NOT restart.
sudo sshd -T | grep -i -E 'passwordauth|permitroot'     # expect: passwordauthentication no / permitrootlogin without-password
sudo systemctl restart ssh
```
(`without-password` is the old name for `prohibit-password`. They mean the same thing.)

**Step 4. Test in a NEW terminal** before you close the old one:
```
💻 PC
ssh ubuntu@<VPS-IP>                                          # must log in with the key
ssh -o PubkeyAuthentication=no ubuntu@<VPS-IP>               # must FAIL: "Permission denied (publickey)"
```
You want the second command to fail: that proves password login is off. If the first one fails, go back to the old session (still open) and undo the change: `sudo rm /etc/ssh/sshd_config.d/99-hardening.conf && sudo systemctl restart ssh`.

### 5.5 Coolify (🖥️ SERVER, then browser)

> [!NOTE]
> **What Coolify is:** a dashboard that runs on the VPS and does the hosting work for you. It builds the app from GitHub, runs Postgres and Redis in Docker, gets HTTPS certificates, and takes backups. After this step you rarely need SSH: almost everything is done in the browser.

**Step 1. Install**
```bash
🖥️ SERVER
curl -fsSL https://cdn.coollabs.io/coolify/install.sh | sudo bash
```
It takes a few minutes and ends with a message saying Coolify is ready at `http://<VPS-IP>:8000`.

**Step 2. Register the admin account. Do this immediately.**
Open `http://<VPS-IP>:8000` in your browser and register.

> [!CAUTION]
> **The first person to register becomes the admin.** Until you register, anyone who finds the IP could take over the server. Don't leave this page for later.

Save the email and password in Bitwarden (`coolify admin`).

**Step 3. Turn on 2FA**
Click your profile (top right or bottom left) → **Two-factor authentication** → scan the QR code with your authenticator app. **Save the recovery codes in Bitwarden.** Without them, losing your phone means losing the dashboard.

**Step 4. Turn on Docker cleanup**
It is **not** under the main Settings page. It is per server:
**Servers** (left menu) → **localhost** → **Docker Cleanup** (in the server's own left menu or tabs).

Recent Coolify versions turn it on by default. Check that:
- **Docker Cleanup Frequency** has a schedule (e.g. `0 0 * * *` = every night). Keep the default.
- **Docker Cleanup Threshold** is around **80%** (cleanup also runs when the disk passes this). Keep the default.
- Under **Advanced cleanup**, leave all three on **Keep**:
  - **Unused volumes → Keep unused volumes.** ⚠️ The databases live in volumes. A stopped database's volume counts as "unused".
  - **Unused networks → Keep unused networks.** Harmless either way.
  - **Application images → Keep retained images.** Coolify keeps a few old images so you can roll back to an earlier deploy. Removing them breaks rollback.

Press **Save** if you changed anything. Without cleanup, every deploy leaves an old image behind and the disk slowly fills up.

If your version shows none of this, the same job can be done by hand (section 9, "Disk full"), which is safe as long as you never add `--volumes`.

> [!TIP]
> If the onboarding wizard asks what server to use, pick **This Machine / localhost**. Coolify then connects to the VPS as root with its own SSH key. That's why 5.4 uses `PermitRootLogin prohibit-password` and not `no`.

> [!CAUTION]
> **Don't edit the server's connection settings.** Servers → localhost has a field **"IP address or domain"**. It is how Coolify SSHes into its own machine and must stay **`host.docker.internal`**. It is **not** where the dashboard's domain goes (that's 5.6 step 2). If a domain ends up there, put `host.docker.internal` back, **Save**, then **Revalidate connection**. Leave SSH user `root`, port `22`, and **Wildcard domain** empty.

---

### 5.6 DNS + HTTPS (registrar website, then Coolify)

**Step 1. Add DNS records** at your domain registrar (where you bought the domain) → DNS settings → add **A records**:

| Type | Name / Host | Value / Points to | Use |
|---|---|---|---|
| A | `coolify` | `<VPS-IP>` | Coolify dashboard |
| A | `api` | `<VPS-IP>` | Prod backend |
| A | `uat-api` | `<VPS-IP>` | UAT backend |

The **Name** is just the part before your domain: type `api`, not `api.yourdomain`. Changes take from a few minutes up to a few hours.

- **Using a domain that already runs another site?** That's fine: subdomains are independent. Only **add** records; don't change or delete the existing ones. Pick names that don't clash with the other site (e.g. prefix them with `agrum-`).
- **DNS on Cloudflare?** Set each of these records to **DNS only** (grey cloud), **not Proxied** (orange cloud). With the proxy on, Coolify can't get its HTTPS certificate and you can get redirect loops.
- **A `CAA` record in the list?** It must allow `letsencrypt.org`, or the certificates fail. No CAA record at all is fine.

Check from the PC:
```
💻 PC
nslookup uat-api.yourdomain
```
It must answer with `<VPS-IP>`. If it shows nothing or another IP, wait and try again.

**Step 2. Check the proxy is running**
**Servers** → **localhost** → **Proxy**. It must say **Running**. If it's stopped, press **Start Proxy** and pick **Traefik** if asked. The proxy is what serves HTTPS and sends each domain to the right app.

**Step 3. Give Coolify its own HTTPS address**
In the **main left menu** click **Settings** (the global one, **not** Servers → localhost) → **Instance's Domain** → `https://coolify.yourdomain` → **Save**.
Type it exactly: `https://` at the start, no trailing `/`. Coolify asks Let's Encrypt for a certificate by itself. Within a minute or two, `https://coolify.yourdomain` should load with a padlock.

> [!TIP]
> **A "503 Service Unavailable … The domain is not configured on a resource in Coolify" page** on `https://coolify.yourdomain` is good news: the DNS works and the proxy answers. Coolify just doesn't know the domain is its own yet. Fix: open `http://<VPS-IP>:8000`, set the Instance's Domain as above, wait 1–2 minutes. Still 503? Servers → localhost → Proxy → **Restart Proxy**.

**Step 4. Close port 8000.** Only after `https://coolify.yourdomain` works:
```bash
🖥️ SERVER
sudo ufw delete allow 8000/tcp
sudo ufw status            # 8000 must be gone; 22, 80, 443 stay
```
From now on, use only `https://coolify.yourdomain`.

> [!WARNING]
> Set the domain **before** connecting GitHub (5.7 step 1). GitHub sends its deploy webhooks to the address Coolify had when the connection was made. If it was `http://IP:8000`, they break as soon as port 8000 closes.

---

### 5.7 Coolify resources (browser)

> [!TIP]
> **Doing UAT only for now?** Create only the `uat` column below. Production gets the same steps later, in its own environment.

**Step 1. Connect GitHub (once)**
Coolify → **Sources** → **+ Add** → **GitHub App** → follow the steps. When GitHub asks which repositories to allow, pick **only** `agrum_be`. That lets Coolify clone the private repo and receive push webhooks for automatic deploys.

**Step 2. Project + environments**
**Projects** → **+ Add** → name it `agrum`. It starts with an environment called `production`. Add a second one called `uat` (a "+ New" or "Environments" button in the project).
Everything below is created **inside an environment**: open `uat` first, and later `production`.

**Step 3. Postgres**
In the environment → **+ New** → **Databases** → **PostgreSQL** → create. The default image is fine (UAT uses `postgres:18-alpine`).

Before pressing **Start**:
- **Password:** replace it with a random one (`openssl rand -base64 24`) and store it **only** in Bitwarden. Never a personal password, never pasted into chats or tickets.
- **Access: Private.** Leave **"Make it publicly available" OFF**. Docker bypasses ufw, so a public database is open to the whole internet (section 8).
- **Port mappings** and **Custom Docker options** must be **empty**. The greyed text in them (`3000:5432`, `--cap-add SYS_ADMIN ...`) is only an example. If it is real text you can select, delete it: a port mapping publishes the database past ufw.
- SSL **Disabled** is fine: the app talks to it only inside the server.

Then press **Start**.

> [!WARNING]
> **Postgres reads the password only the first time it starts.** Changing the Password field afterwards does **not** change it in the database, and the app then fails to log in. To change it later: open the database's **Terminal** in Coolify and run
> `psql -U postgres -c "ALTER USER postgres PASSWORD 'NEW_PASSWORD';"`,
> then put the same value in the Password field and in the app's `DB_PASSWORD`, and redeploy the app. While the database is still empty, deleting and re-creating it is just as easy.

Copy the **Postgres URL (internal)**. It looks like `postgres://<user>:<password>@<long-id>:5432/postgres`. You need `<user>`, `<password>`, `<long-id>` (the host) and the database name for 5.8.

**Step 4. Redis**
**+ New** → **Databases** → **Redis** → create and **Start**. Again **not public**, and no port mappings.
Its internal URL looks like `redis://default:<password>@<long-id>:6379/0`. You need the **host** (`<long-id>`) and the **password** for 5.8, as separate values (see the box in 5.8).

**Step 5. The app**
**+ New** → **Private Repository (with GitHub App)** → pick `agrum_be` →
- **Branch:** `uat` (prod: `main`). The branch must already exist on GitHub.
- **Build Pack:** **Dockerfile**
- **Ports Exposes:** `8080`
- **Domains:** `https://uat-api.yourdomain` (prod: `https://api.yourdomain`). The `https://` is what makes Coolify fetch a certificate.
- Do **not** fill in "Ports Mappings". That would publish the port and bypass ufw.

Don't press Deploy yet. Set the environment variables first (5.8).

> [!NOTE]
> **"DNS is not configured for www.<your api domain>"** is a harmless warning. Coolify checks the `www.` version of every domain out of habit; an API is never called with `www`. Make sure the Domains field holds only the non-www `https://...` address, and carry on.

**Step 6. Health check, memory limits, auto deploy**
- App → **Health check:** path `/api/public/health`, port `8080`, enabled.
- **Memory limits:** these are **not** on the resource's General page. Each resource (Postgres, Redis, the app) has its own left menu → **Resource Limits** → **Maximum Memory Limit**. Write it in Docker format: `512m`, `128m`, `1g` (lowercase, no space). Leave the CPU fields alone. **Save**, then **Restart** that resource so the limit applies.
- App → **Auto Deploy:** on for UAT, **off** for prod.

**UAT-only 4 GB box (what UAT uses now):**

| Resource | Maximum Memory Limit |
|---|---|
| Postgres | `512m` |
| Redis | `128m` |
| App | `1g` |

**Shared 8 GB VPS-2 (the production plan):**

| Resource | production | uat |
|---|---|---|
| PostgreSQL, **not public** | memory limit 1.5 GB | 512 MB |
| Redis, **not public** | 256 MB | 256 MB |
| App: GitHub `agrum_be`, build pack **Dockerfile**, port 8080 | branch `main`, `https://api.yourdomain`, 1.5 GB | branch `uat`, `https://uat-api.yourdomain`, 1 GB |
| Auto deploy | **off** (press Deploy) | on |
| Scheduled backups | daily `0 2 * * *` to S3, keep 14 | optional |

Memory budget: about 6 GB of the 8. The rest is for Coolify itself, the OS, and the Maven build that runs during each deploy.

---

### 5.8 App environment variables (browser, per environment, never shared)

App → **Environment Variables** → switch to the **Developer view** (or "Bulk edit") to paste them all at once → **Save**. Then press **Deploy** and watch the log.

Fill the `<...>` parts from 5.7 steps 3 and 4. Make new secrets for each environment and save them in Bitwarden (`uat db`, `uat jwt`, `uat superuser`, and the `prod` ones).

```
DB_URL=jdbc:postgresql://<postgres internal host from Coolify>:5432/<db name>
DB_USERNAME=...
DB_PASSWORD=...
REDIS_HOST=<redis internal host from Coolify>
REDIS_PORT=6379
SPRING_DATA_REDIS_PASSWORD=<redis password from Coolify>
JWT_SECRET=<openssl rand -base64 48>
JWT_EXP=86400000
SUPERUSER_USERNAME=...
SUPERUSER_EMAIL=...
SUPERUSER_PASSWORD=<long random>
CORS_ALLOWED_ORIGINS=https://app.yourdomain           # uat: https://uat-app.yourdomain
COOKIE_SECURE=true
COOKIE_SAME_SITE=None
JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75
SPRING_JPA_SHOW_SQL=false
SPRING_JPA_PROPERTIES_HIBERNATE_FORMAT_SQL=false
SPRINGDOC_API_DOCS_ENABLED=false                      # prod only; UAT can keep Swagger
SPRINGDOC_SWAGGER_UI_ENABLED=false                    # prod only
```

> [!IMPORTANT]
> **Coolify gives full URLs; the app wants the pieces.** Split them like this:
>
> **Postgres:** Coolify shows `postgres://USER:PASSWORD@HOST:5432/DBNAME`.
> ```
> DB_URL=jdbc:postgresql://HOST:5432/DBNAME     # drop "USER:PASSWORD@", start with jdbc:postgresql://
> DB_USERNAME=USER
> DB_PASSWORD=PASSWORD
> ```
> **Redis:** Coolify shows `redis://default:PASSWORD@HOST:6379/0`.
> ```
> REDIS_HOST=HOST                               # only the long id, NOT the whole redis://... URL
> REDIS_PORT=6379
> SPRING_DATA_REDIS_PASSWORD=PASSWORD
> ```
> Leave out `default` (the Redis user) and `/0` (the database number); those are the defaults. If the password in the URL has `%xx` codes in it, it is URL-encoded: copy the plain one from the resource's **Password** field instead.

Making the secrets (run on the server or in Git Bash):
```bash
openssl rand -base64 48     # JWT_SECRET (different for UAT and prod)
openssl rand -base64 24     # SUPERUSER_PASSWORD
```

Why each matters:
- **Different `JWT_SECRET` per environment.** If they matched, a UAT login token would also work on prod.
- **`SPRING_DATA_REDIS_PASSWORD`.** Coolify's Redis has a password. `application.yml` only maps host and port, so the password goes in through Spring's own property name. Without it the app still runs (Redis fails open to Postgres), but every cache call fails quietly.
- **`SPRING_JPA_SHOW_SQL=false`.** `application.yml` has `show-sql: true`. In prod it logs every query and fills the disk.
- **`CORS_ALLOWED_ORIGINS`** must be the exact web URL: `https`, no trailing slash, no wildcard in prod. Several are allowed, separated by commas with no spaces, e.g. the UAT domain plus the Vercel preview URL while you move over: `https://uat-app.yourdomain,https://<project>.vercel.app`.
- **Cookies.** The web app (Vercel) and the API are on different hosts, so the cookie must be `Secure` + `SameSite=None`.

**Check the deploy worked:**
```
💻 PC
curl https://uat-api.yourdomain/api/public/health
```
`{"status":"UP"}` means the app is running and talking to Postgres. Then open `https://uat-api.yourdomain/swagger-ui.html` (UAT only) and log in once as the superuser.

The **first** deploy takes 5–10 minutes (Maven downloads every dependency), and the app needs about a minute to start after the build. Later deploys are faster. If it fails, the last 30 lines of the deploy log or the app's **Logs** tab say why; section 9 "App won't start after deploy" lists the usual causes.

---

### 5.9 Web + mobile

**Vercel** (vercel.com → **Add New** → **Project** → import `agrum_fe`). Make two projects from the same repo:

| | prod | uat |
|---|---|---|
| Production Branch (Settings → Git) | `main` | `uat` |
| Domain (Settings → Domains) | `app.yourdomain` | `uat-app.yourdomain` |
| Env var `VITE_API_BASE_URL` (Settings → Environment Variables) | `https://api.yourdomain` | `https://uat-api.yourdomain` |

- Adding a domain in Vercel shows the DNS record it wants (usually a **CNAME** `uat-app` → `cname.vercel-dns.com`). Add it at the registrar, the same way as 5.6.
- `VITE_` variables are baked in **at build time**. After changing one, **redeploy** (Deployments → ⋯ → Redeploy), or the site keeps the old value.
- The web URL must match the backend's `CORS_ALLOWED_ORIGINS` **exactly**.

**Mobile:** the `agrum_mobile` GitHub secret `API_BASE_URL` (repo → Settings → Secrets and variables → Actions) decides which backend Firebase builds talk to. Set it to `https://uat-api.yourdomain`, push to `main` to trigger a build, then install the new build from App Tester (it doesn't update by itself). Prod and UAT builds share one app ID, so a phone can hold only one of them. An Android "flavor" would fix that if it's ever needed.

---

### 5.10 Off-site backups (browser)

> [!NOTE]
> **Why off-site:** OVH's backup lives with OVH and restores the **whole** server. If the VPS or the OVH account is lost, those backups go with it. A copy in another provider/region is the real safety net.

**Step 1. Make a bucket** in a **different region or provider** from the VPS: Backblaze B2, Cloudflare R2, or OVH Object Storage in another region. Create an **access key + secret** for it and save them in Bitwarden (`s3 backup bucket`).

**Step 2. Add it to Coolify:** **Storages** (S3) → **+ Add** → endpoint, bucket name, region, access key, secret → **Validate / Test connection** → save.

**Step 3. Schedule the backups:** prod Postgres → **Backups** → **+ Add**:
- Frequency `0 2 * * *` (every night at 02:00 server time)
- **Save to S3** on, pick the bucket
- Keep **14**

**Step 4. Test it once:** press **Backup now**, then check that the file appears in the bucket with a sensible size (not a few KB). The every-3-months restore test is in section 7.

---

## 6. Day-to-day workflow

```
feature branch → merge into uat → auto-deploys to UAT → client tests
              → merge uat into main → press Deploy on prod in Coolify
```

**Before every prod deploy:**
1. Coolify → prod Postgres → **Backup now**.
2. Deploy.
3. Watch the deploy log until the app is healthy, then log in on `app.yourdomain` once.

Why "Backup now" first: the schema is managed by Hibernate `ddl-auto: update`. There are no migrations, so **a deploy can change prod tables the moment the app starts**. A fresh dump is the only undo for that.

### How a deploy actually works (CI/CD)

There are **no GitHub Actions in `agrum_be`**. The hosting platform is the CD: it watches a branch, and on a push it builds the `Dockerfile` itself.

```
git push to uat ──► GitHub webhook ──► Coolify
                                        1. git clone the branch
                                        2. docker build (Dockerfile):
                                             stage 1: mvn clean package -DskipTests   (on the VPS)
                                             stage 2: copy the jar into a small Java 21 image
                                        3. start the new container
                                        4. wait for /api/public/health = 200
                                        5. switch traffic to it, stop the old one
                                        6. if the health check never passes → keep the old one running
```

- **UAT:** auto deploy on push to `uat`.
- **Prod:** auto deploy off. Merge `uat` → `main`, then press **Deploy** in Coolify.
- **Rollback:** Coolify → app → Deployments → redeploy an earlier one. Tables changed by `ddl-auto` are **not** rolled back; restore the "Backup now" dump for that.
- **No tests run on deploy.** `-DskipTests` still **compiles** the tests, so broken test code fails the build. **The tests run in GitHub Actions instead:** `.github/workflows/tests.yml` runs the unit tests on every pull request and on every push to `main` / `uat` (the context-load test is skipped because it needs a live DB). A red ❌ on a PR means: don't merge. To make that a hard rule, GitHub → Settings → Branches → the `main` rule → "Require status checks to pass" → pick **Unit tests / test**. Do the same for `uat` once that branch exists.
- Running the same tests locally: `./mvnw test -Dtest='!AgrumApplicationTests' -Dsurefire.failIfNoSpecifiedTests=false`.
- The tests and the deploy run **in parallel**: a push to `uat` deploys even if the tests fail. Requiring the check on pull requests is what stops bad code before it's merged.
- **What else deploys from git:**
  - `agrum_fe`: Vercel builds each branch the same way.
  - `agrum_mobile`: a GitHub Action builds the APK on push to `main` and sends it to Firebase App Distribution. It uses the `API_BASE_URL` secret.

---

## 7. Maintenance checklist

**Monthly**
- [ ] `sudo apt update && sudo apt upgrade -y` (reboot if it asks: `sudo reboot`, then check both apps came back)
- [ ] Update Coolify from its dashboard. It has had security bugs; stay current.
- [ ] `df -h`: disk below 70%? If not, see section 9.
- [ ] Check that the latest S3 backup exists and has a sensible size.

**Every 3 months**
- [ ] **Test a restore** of a prod backup into UAT. Check the data, then **wipe UAT again**. Prod has face photos (biometric data); don't leave copies in UAT.
- [ ] Check OVH invoices / auto-renew / card expiry.
- [ ] Review `~/.ssh/authorized_keys`: only keys you still use.

**Monitoring**
- UptimeRobot (free) on `https://api.yourdomain` and `https://uat-api.yourdomain`.
- Health endpoint: **`GET /api/public/health`**. No login needed, not rate-limited.
  - `200 {"status":"UP"}`: the app is running and Postgres answers.
  - `503 {"status":"DOWN"}`: the app is running but can't reach Postgres.
  - No answer: the app is down.
  - Redis is not checked on purpose (the app works without it).
- In Coolify → app → **Health check**: path `/api/public/health`, port `8080`. The Dockerfile also has a `HEALTHCHECK` (curl every 30 s after a 90 s start period).

---

## 8. Things that will surprise you

| Surprise | Why | What to do |
|---|---|---|
| A port is open even though `ufw` blocks it | **Docker writes its own iptables rules and bypasses ufw** | Never publish ports (`ports: 8080:8080`, 5432, 6379). Let Coolify's proxy route by domain. Never tick "Make it publicly available" on a database. |
| Coolify can't reach "localhost" server | `PermitRootLogin no`, root's `authorized_keys` was edited, or the server's "IP address or domain" field was changed | Use `prohibit-password`. Don't remove Coolify's key from `/root/.ssh/authorized_keys`. Servers → localhost → IP address = `host.docker.internal`, then Revalidate connection. |
| `https://coolify.yourdomain` shows Coolify's own "503 … domain is not configured" page | DNS and proxy work, but the Instance's Domain isn't set | Global **Settings** → Instance's Domain (5.6 step 3). |
| App can't log in to Postgres after you changed the DB password in Coolify | Postgres keeps the password it was created with | `ALTER USER` in the database terminal (5.7 step 3). |
| App can't reach Redis | `REDIS_HOST` holds the whole `redis://...` URL | Only the host goes there; the password goes in `SPRING_DATA_REDIS_PASSWORD` (5.8). |
| Deploy fails with "Killed" or exit code 137 | Out of memory during the Maven build | Swap file (5.3), lower memory limits, or a bigger VPS. |
| Prod got slow during a deploy | Maven builds the jar **on the VPS** (CPU + about 1–2 GB RAM) | Deploy prod at quiet hours. UAT deploys also share the CPU. |
| Disk suddenly full, apps crash | Old Docker images, build cache, SQL logs, backups kept locally | Docker cleanup on (Servers → localhost → Docker Cleanup), `SPRING_JPA_SHOW_SQL=false`, `docker system prune -a` (section 9) |
| Restoring the OVH backup wiped today's prod data | OVH backups restore the **whole VPS** (UAT + prod) to last night | To recover data only, restore the **Coolify Postgres backup** instead. |
| Login works on UAT but not on prod (or the other way round) | Wrong `CORS_ALLOWED_ORIGINS` or cookie settings, or the web app points at the wrong API | Check the browser console for CORS errors. Check `VITE_API_BASE_URL` on that Vercel project. |
| Everyone logged out after a change | `JWT_SECRET` changed, or Redis/Postgres refresh sessions reset | Expected. Users log in again. |
| New endpoint returns 403 for everyone except superuser | Access is data-driven: new endpoints must be added to a Permission Set | Add the projection to the right Permission Set in the web admin. |
| Phone still talks to the old server | Firebase App Tester doesn't auto-update; the build was made with the old `API_BASE_URL` | Check the GitHub secret, rebuild, install the new build. |
| Can't swap billing to the customer | Customer signed up in a different OVH zone | They need an account whose control panel is on `ca.ovh.com`. |
| VPS gone | Payment failed → OVH suspends, then deletes **with its backups** | Auto-renew on, valid card, and the off-site S3 backup always. |

---

## 9. Troubleshooting

**SSH refuses my key / locked out**
1. Try the second device's key.
2. KVM console → log in as `ubuntu` → check `~/.ssh/authorized_keys` and `sudo sshd -T | grep -i -E 'pubkey|password|permitroot'`.
3. Firewall: `sudo ufw status`. If 22 is missing: `sudo ufw allow 22/tcp`.
4. Banned by fail2ban: `sudo fail2ban-client status sshd` → `sudo fail2ban-client set sshd unbanip <your-IP>`.
5. Still stuck: rescue mode (section 4).

**Forgot the Coolify password**
```bash
sudo docker exec -ti coolify sh -c "php artisan root:reset-password"
```
Lost 2FA: use a recovery code from Bitwarden.

**Disk full**
```bash
df -h
sudo du -xh / --max-depth=2 2>/dev/null | sort -h | tail -20
sudo docker system df
sudo docker system prune -a          # removes unused images and build cache; NOT volumes
```
Never run `docker volume prune` or `docker system prune --volumes`. **The databases live in volumes.**

**App won't start after deploy**
- Coolify → app → Logs. The usual causes:
  - A missing env var (`JWT_SECRET`, `JWT_EXP`, `CORS_ALLOWED_ORIGINS` and `SUPERUSER_*` have no defaults).
  - A wrong `DB_URL` host.
  - Out of memory: raise the app's memory limit or lower `MaxRAMPercentage`.
- Roll back: Coolify keeps earlier deployments; redeploy the previous one. If the schema was changed, restore the "Backup now" dump too.

**Restore prod database from backup**
1. Coolify → prod Postgres → Backups → pick a backup → Restore. Or download the dump and use `pg_restore` / `psql`.
2. Restart the prod app.
3. Optionally clear Redis (the permission and estate caches rebuild themselves from Postgres).

**Rebuild everything on a new VPS**
Follow section 5 in order, then restore the latest S3 dump into the new prod Postgres, set the env vars from Bitwarden, and point the DNS A records at the new IP.

---

## 10. Costs (approximate, ex. tax)

| Item | Cost |
|---|---|
| OVH VPS-2 | about $8.50 / month |
| Premium automatic backup (7 days) | $1.65 / month (first 3 months free, Black Friday 2026) |
| Snapshot (optional) | $0.45 / month |
| Domain | about $10–15 / year |
| S3 backup storage | about $1 / month |
