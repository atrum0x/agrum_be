# Hosting agrum on OVH (UAT + Production on one VPS)

How the backend is hosted, why it is set up this way, and what to do when something goes wrong.
Read the **Lockout rules** and **Things that will surprise you** sections before touching the server.

> Placeholders: `yourdomain` = the real domain, `<VPS-IP>` = the server's IP address.
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
| Provider | OVHcloud, VPS-2: 4 vCores, 8 GB RAM, 75 GB NVMe |
| Location | Singapore (or Mumbai), the closest to Sri Lanka |
| OS | Ubuntu 24.04 |
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

### 5.1 Order
- VPS-2, Singapore/Mumbai, Ubuntu 24.04, SSH public key pasted into the order.
- Add-on: **Premium automatic backup** (daily, keeps 7 days). Snapshot add-on optional.
- The VPS already includes a free daily backup that keeps only the last 24 hours.

### 5.2 First login + safety net
```bash
ssh ubuntu@<VPS-IP>
sudo passwd ubuntu                 # console password → Bitwarden. Test it in KVM now.
nano ~/.ssh/authorized_keys        # add the second device's public key, one per line
```

### 5.3 Updates + firewall
```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y ufw fail2ban unattended-upgrades
sudo dpkg-reconfigure -plow unattended-upgrades     # Yes

sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 8000/tcp            # temporary, for the first Coolify login
sudo ufw enable
sudo ufw status
```
Test a new SSH session before continuing.

### 5.4 SSH hardening
`/etc/ssh/sshd_config.d/99-hardening.conf`:
```
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin prohibit-password
```
```bash
sudo sshd -t && sudo systemctl restart ssh
```
Test a new SSH session before closing the old one.

> Ubuntu 24.04 note: if `/etc/ssh/sshd_config.d/50-cloud-init.conf` exists and says `PasswordAuthentication yes`, it is read **first** and wins. Set it to `no` there too, or delete it. Check the result with `sudo sshd -T | grep -i -E 'passwordauth|permitroot'`.

### 5.5 Coolify
```bash
curl -fsSL https://cdn.coollabs.io/coolify/install.sh | sudo bash
```
- Open `http://<VPS-IP>:8000` **immediately**. The first person to register becomes the admin.
- Turn on 2FA and save the recovery codes.
- Settings → **Docker cleanup** on (old images fill the disk).

### 5.6 DNS + HTTPS
A records → `<VPS-IP>`:

| Name | Use |
|---|---|
| `coolify.yourdomain` | Coolify dashboard |
| `api.yourdomain` | Prod backend |
| `uat-api.yourdomain` | UAT backend |

- Coolify → Settings → Instance domain = `https://coolify.yourdomain`.
- When that loads over HTTPS: `sudo ufw delete allow 8000/tcp`.

### 5.7 Coolify resources
Project **agrum** with two environments, **production** and **uat**, holding the same resources:

| Resource | production | uat |
|---|---|---|
| PostgreSQL 16, **not public** | memory limit 1.5 GB | 512 MB |
| Redis/Valkey, **not public** | 256 MB | 256 MB |
| App: GitHub `agrum_be`, build pack **Dockerfile**, port 8080 | branch `main`, `https://api.yourdomain`, 1.5 GB | branch `uat`, `https://uat-api.yourdomain`, 1 GB |
| Auto deploy | **off** (press Deploy) | on |
| Scheduled backups | daily `0 2 * * *` to S3, keep 14 | optional |

Memory budget: about 6 GB of 8. The rest is for Coolify itself, the OS and the Maven build that runs during each deploy.

### 5.8 App environment variables (per environment, never shared)
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
Why each matters:
- **Different `JWT_SECRET` per environment.** If they matched, a UAT login token would also work on prod.
- **`SPRING_DATA_REDIS_PASSWORD`.** Coolify's Redis has a password. `application.yml` only maps host and port, so the password goes in through Spring's own property name. Without it the app still runs (Redis fails open to Postgres) but every cache call fails quietly.
- **`SPRING_JPA_SHOW_SQL=false`.** `application.yml` has `show-sql: true`. In prod it logs every query and fills the disk.
- **`CORS_ALLOWED_ORIGINS`** must be the exact web URL: `https`, no trailing slash, no wildcard in prod.
- **Cookies.** The web app (Vercel) and API are on different hosts, so the cookie must be `Secure` + `SameSite=None`.

### 5.9 Web + mobile
- Vercel: two projects from `agrum_fe`.
  - **prod:** branch `main`, domain `app.yourdomain`, `VITE_API_BASE_URL=https://api.yourdomain`
  - **uat:** branch `uat`, domain `uat-app.yourdomain`, `VITE_API_BASE_URL=https://uat-api.yourdomain`
- Mobile: `agrum_mobile` GitHub secret `API_BASE_URL` decides which backend Firebase builds talk to. Prod and UAT builds share one app ID, so a phone can hold only one of them (an Android "flavor" would fix that if ever needed).

### 5.10 Off-site backups
- S3-compatible bucket in a **different region/provider** from the VPS (OVH Object Storage in another region, Backblaze B2 or Cloudflare R2).
- Coolify → Storage (S3) → add bucket. Prod Postgres → Backups → schedule + S3 + keep 14.

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
| Coolify can't reach "localhost" server | `PermitRootLogin no`, or root's `authorized_keys` was edited | Use `prohibit-password`. Don't remove Coolify's key from `/root/.ssh/authorized_keys`. |
| Prod got slow during a deploy | Maven builds the jar **on the VPS** (CPU + about 1–2 GB RAM) | Deploy prod at quiet hours. UAT deploys also share the CPU. |
| Disk suddenly full, apps crash | Old Docker images, build cache, SQL logs, backups kept locally | Docker cleanup on, `SPRING_JPA_SHOW_SQL=false`, `docker system prune -a` (section 9) |
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
