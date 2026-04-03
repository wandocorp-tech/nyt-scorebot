# Raspberry Pi Deployment Guide

Deploy the NYT Scorebot Discord bot to a Raspberry Pi using the GitHub Actions CI/CD pipeline.

## Prerequisites

| Requirement | Details |
|---|---|
| **Raspberry Pi** | Model 4 or 5, with Raspberry Pi OS (64-bit) freshly imaged |
| **Network** | Pi connected to your home network via Ethernet or Wi-Fi |
| **Discord Bot Token** | From the [Discord Developer Portal](https://discord.com/developers/applications) — **Message Content Intent** must be enabled |
| **GitHub Repo Access** | Admin permissions on the `nyt-scorebot` repository (to configure secrets and environments) |
| **Local Machine** | A computer on the same network as the Pi for initial setup |

---

## Part 1: Raspberry Pi Initial Setup

### 1.1 — Connect and Enable SSH

If you used Raspberry Pi Imager, you may have enabled SSH during imaging. If not:

```bash
# Connect a keyboard and monitor to the Pi, then:
sudo raspi-config
# Navigate to: Interface Options → SSH → Enable

# Or enable it directly:
sudo systemctl enable ssh
sudo systemctl start ssh
```

Find the Pi's local IP address:

```bash
hostname -I
# Example output: 192.168.1.42
```

From your local machine, verify SSH works:

```bash
ssh pi@192.168.1.42    # default user is 'pi' — use whatever you set during imaging
```

### 1.2 — Set a Static IP Address

A stable IP ensures GitHub Actions can always reach the Pi. Configure via DHCP reservation on your router (preferred) or directly on the Pi.

**Option A — Router DHCP Reservation (Recommended):**
Log into your router's admin page, find the Pi's MAC address in the connected devices list, and assign it a static lease.

**Option B — Static IP on the Pi:**

```bash
sudo nano /etc/dhcpcd.conf
```

Add at the end (adjust for your network):

```
interface eth0
static ip_address=192.168.1.42/24
static routers=192.168.1.1
static domain_name_servers=192.168.1.1 8.8.8.8
```

Then reboot:

```bash
sudo reboot
```

### 1.3 — Update Packages

```bash
sudo apt update && sudo apt upgrade -y
```

### 1.4 — Install Java 17

```bash
sudo apt install -y openjdk-17-jre-headless
```

Verify the installation:

```bash
java -version
# Expected: openjdk version "17.x.x" ...
```

---

## Part 2: Service User and Directories

Create a dedicated system user and directory structure so the bot runs in isolation with proper permissions.

### 2.1 — Create a System User

```bash
sudo useradd --system --shell /usr/sbin/nologin --home-dir /opt/scorebot scorebot
```

### 2.2 — Create the Deploy Directory

```bash
sudo mkdir -p /opt/scorebot/data
sudo chown -R scorebot:scorebot /opt/scorebot
sudo chmod 750 /opt/scorebot
```

The directory layout will be:

```
/opt/scorebot/
├── nyt-scorebot-app-1.0-SNAPSHOT.jar   ← deployed by GitHub Actions
└── data/
    └── scorebot.mv.db                  ← H2 database (created on first run)
```

### 2.3 — Allow the Deploy User to SSH

The `scorebot` system user has no login shell, so GitHub Actions needs a **separate SSH user** to connect. You can either:

- **Use the default `pi` user** (simplest), or
- **Create a dedicated `deploy` user** (more secure)

**Option A — Use the `pi` user (simplest):**
No extra setup needed. The `pi` user will SCP files and run `sudo systemctl restart`.

**Option B — Create a dedicated deploy user:**

```bash
sudo useradd --create-home --shell /bin/bash deploy
sudo passwd -l deploy    # disable password login — SSH keys only
```

Grant the deploy user permission to restart the bot service without a password:

```bash
sudo visudo -f /etc/sudoers.d/scorebot-deploy
```

Add this line:

```
deploy ALL=(ALL) NOPASSWD: /usr/bin/systemctl restart nyt-scorebot, /usr/bin/systemctl stop nyt-scorebot, /usr/bin/systemctl start nyt-scorebot, /usr/bin/systemctl status nyt-scorebot
```

> **Note:** Throughout this guide, replace `deploy` with `pi` if you chose Option A.

---

## Part 3: Create the systemd Service

### 3.1 — Write the Service Unit File

```bash
sudo nano /etc/systemd/system/nyt-scorebot.service
```

Paste the following:

```ini
[Unit]
Description=NYT Scorebot Discord Bot
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=scorebot
Group=scorebot
WorkingDirectory=/opt/scorebot
ExecStart=/usr/bin/java -Xms128m -Xmx256m -jar /opt/scorebot/nyt-scorebot-app-1.0-SNAPSHOT.jar
Restart=on-failure
RestartSec=10
StartLimitIntervalSec=60
StartLimitBurst=3

# Environment — set your Discord bot token here
Environment=DISCORD_TOKEN=your-discord-bot-token-here

# Security hardening
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/opt/scorebot
PrivateTmp=true

[Install]
WantedBy=multi-user.target
```

> **Important:** Replace `your-discord-bot-token-here` with your actual Discord bot token.

### 3.2 — Memory Settings

The `-Xms128m -Xmx256m` flags keep the JVM memory footprint small for a Raspberry Pi. Adjust if needed:

| Pi Model | RAM | Recommended `-Xmx` |
|---|---|---|
| Pi 4 (2 GB) | 2 GB | 256m |
| Pi 4 (4 GB) | 4 GB | 512m |
| Pi 4/5 (8 GB) | 8 GB | 512m |

### 3.3 — Enable and Start the Service

```bash
sudo systemctl daemon-reload
sudo systemctl enable nyt-scorebot
```

Don't start it yet — there's no JAR to run. It will start after the first deployment.

To manually test once you've placed a JAR:

```bash
sudo systemctl start nyt-scorebot
sudo systemctl status nyt-scorebot
# Check logs:
sudo journalctl -u nyt-scorebot -f
```

---

## Part 4: SSH Key Setup for GitHub Actions

GitHub Actions needs SSH access to your Pi to copy the JAR and restart the service.

### 4.1 — Generate an SSH Key Pair

On your **local machine** (not the Pi):

```bash
ssh-keygen -t ed25519 -C "github-actions-deploy" -f ~/.ssh/scorebot_deploy
# When prompted for a passphrase, press Enter (no passphrase)
```

This creates two files:

| File | Purpose | Goes Where |
|---|---|---|
| `~/.ssh/scorebot_deploy` | **Private key** | GitHub repository secret (`PI_SSH_KEY`) |
| `~/.ssh/scorebot_deploy.pub` | **Public key** | Pi's `~/.ssh/authorized_keys` |

### 4.2 — Install the Public Key on the Pi

Copy the public key to the Pi's deploy user:

```bash
# If using the 'pi' user:
ssh-copy-id -i ~/.ssh/scorebot_deploy.pub pi@192.168.1.42

# If using the 'deploy' user:
ssh-copy-id -i ~/.ssh/scorebot_deploy.pub deploy@192.168.1.42
```

Or manually:

```bash
# SSH into the Pi
ssh pi@192.168.1.42

# Append the public key
mkdir -p ~/.ssh && chmod 700 ~/.ssh
echo "paste-your-public-key-here" >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
```

### 4.3 — Test Key-Based SSH

```bash
ssh -i ~/.ssh/scorebot_deploy pi@192.168.1.42 "echo 'SSH works!'"
```

### 4.4 — Copy the Private Key for GitHub

You'll need the private key content in the next section:

```bash
cat ~/.ssh/scorebot_deploy
```

Copy the **entire output** including the `-----BEGIN` and `-----END` lines.

---

## Part 5: Network Access

GitHub Actions runners are in the cloud — they need a path through your home router to reach the Pi.

### 5.1 — Port Forwarding (Recommended for Simplicity)

1. Log into your router's admin page (usually `192.168.1.1`)
2. Find the **Port Forwarding** section (sometimes under NAT, Firewall, or Advanced)
3. Create a rule:

| Setting | Value |
|---|---|
| External Port | A non-standard port, e.g. `2222` (avoids bots scanning port 22) |
| Internal IP | Your Pi's static IP, e.g. `192.168.1.42` |
| Internal Port | `22` |
| Protocol | TCP |

4. Find your public IP:

```bash
curl -s https://ifconfig.me
```

5. Test from an external network (e.g., phone hotspot):

```bash
ssh -i ~/.ssh/scorebot_deploy -p 2222 pi@YOUR_PUBLIC_IP "echo 'External SSH works!'"
```

> **Dynamic DNS:** If your ISP assigns a dynamic IP, set up a free DDNS service (e.g., DuckDNS, No-IP) so you have a stable hostname instead of a changing IP address.

### 5.2 — Alternatives to Port Forwarding

If port forwarding isn't an option (CGNAT, restricted router):

- **[Tailscale](https://tailscale.com/)** — Install on both the Pi and a GitHub Actions self-hosted runner. Provides a private mesh VPN.
- **[Cloudflare Tunnel](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/)** — Exposes the Pi's SSH without opening router ports.

These are more complex to set up but avoid exposing SSH to the public internet.

### 5.3 — Security Hardening

Since you're exposing SSH to the internet, take these precautions:

```bash
sudo nano /etc/ssh/sshd_config
```

Make these changes:

```
# Disable password authentication (key-only)
PasswordAuthentication no

# Optionally change the SSH port (must match router forwarding)
# Port 2222

# Restrict to your deploy user
AllowUsers pi deploy
```

Apply:

```bash
sudo systemctl restart sshd
```

Install fail2ban to block brute-force attempts:

```bash
sudo apt install -y fail2ban
sudo systemctl enable fail2ban
sudo systemctl start fail2ban
```

---

## Part 6: GitHub Repository Configuration

### 6.1 — Create the Production Environment

1. Go to your repository on GitHub
2. **Settings → Environments → New environment**
3. Name it: `production`
4. (Optional) Enable **Required reviewers** to add a manual approval gate before deploys

### 6.2 — Add Repository Secrets

Go to **Settings → Secrets and variables → Actions → New repository secret** and add each:

| Secret Name | Value | Example |
|---|---|---|
| `PI_SSH_KEY` | Entire private key from step 4.4 (including BEGIN/END lines) | `-----BEGIN OPENSSH PRIVATE KEY-----` ... |
| `PI_HOST` | Your public IP or DDNS hostname | `203.0.113.42` or `mypi.duckdns.org` |
| `PI_USER` | The SSH user on the Pi | `pi` or `deploy` |
| `PI_SSH_PORT` | Your external SSH port (if not 22) | `2222` |
| `PI_DEPLOY_PATH` | The deployment directory on the Pi | `/opt/scorebot/` |
| `PI_SERVICE_NAME` | The systemd service name | `nyt-scorebot` |

> **Note:** If `DISCORD_TOKEN` isn't already set as a repository secret, add it too — it's needed by the E2E test workflow.

### 6.3 — Verify Secret Names Match the Workflow

The `deploy.yml` workflow expects exactly these secret names. Double-check by comparing against the workflow file (`.github/workflows/deploy.yml`).

---

## Part 7: Verify End-to-End

### 7.1 — Trigger the Pipeline

**Option A — Push to `main`:**

```bash
git commit --allow-empty -m "ci: trigger deployment pipeline"
git push origin main
```

**Option B — Manual trigger:**

1. Go to **Actions → Pipeline → Run workflow**
2. Select the `main` branch
3. Click **Run workflow**

### 7.2 — Monitor the Workflow

Watch the GitHub Actions run through each stage:

1. ✅ **Build** — compiles, runs unit tests, uploads JAR artifact
2. ✅ **Test** — runs E2E test against live Discord
3. ✅ **Deploy** — SCPs JAR to Pi, restarts systemd service
4. ✅ **Release** — creates a GitHub Release (on `main` branch pushes only)

### 7.3 — Verify on the Pi

```bash
# SSH into the Pi
ssh -i ~/.ssh/scorebot_deploy -p 2222 pi@YOUR_PI_HOST

# Check the JAR was deployed
ls -la /opt/scorebot/nyt-scorebot-app-1.0-SNAPSHOT.jar

# Check the service is running
sudo systemctl status nyt-scorebot

# Watch the logs
sudo journalctl -u nyt-scorebot -f
```

### 7.4 — Verify in Discord

Open your Discord server and confirm the bot is online (green status dot). Post a Wordle result in a monitored channel to test full functionality.

---

## Troubleshooting

### SSH Connection Failures

| Symptom | Fix |
|---|---|
| `Connection timed out` | Check port forwarding rules, Pi firewall (`sudo ufw status`), and that the Pi is running |
| `Permission denied (publickey)` | Verify the public key is in `~/.ssh/authorized_keys`, file permissions are `600`, and `.ssh` directory is `700` |
| `Host key verification failed` | The Pi's host key changed (e.g., after reimaging). Remove the old key: `ssh-keygen -R [host]:port` |

### Service Won't Start

```bash
# Check the full error
sudo journalctl -u nyt-scorebot --no-pager -n 50

# Common issues:
# "java: not found" → Java isn't installed or not at /usr/bin/java
which java

# "Permission denied" → ownership issue
ls -la /opt/scorebot/
sudo chown -R scorebot:scorebot /opt/scorebot

# "Unable to access jarfile" → JAR wasn't deployed or wrong path
ls -la /opt/scorebot/nyt-scorebot-app-1.0-SNAPSHOT.jar
```

### JAR Copy Fails

```bash
# Check disk space on the Pi
df -h /opt/scorebot

# Check write permissions
ls -la /opt/scorebot/

# If using 'deploy' user, ensure it can write to /opt/scorebot:
sudo usermod -aG scorebot deploy
sudo chmod 770 /opt/scorebot
```

### Bot Connects but Doesn't Respond to Messages

1. Verify **Message Content Intent** is enabled: [Discord Developer Portal](https://discord.com/developers/applications) → Your Bot → Bot → Privileged Gateway Intents → **Message Content Intent** ✅
2. Check that the channel IDs and user IDs in `application.properties` match your Discord server
3. Check logs for parsing errors: `sudo journalctl -u nyt-scorebot --no-pager | grep -i error`

### Database Issues

The H2 database is stored at `/opt/scorebot/data/scorebot.mv.db`. If you need to reset:

```bash
sudo systemctl stop nyt-scorebot
sudo rm /opt/scorebot/data/scorebot.mv.db
sudo systemctl start nyt-scorebot
# A fresh database will be created automatically (ddl-auto=update)
```

### Updating the Bot Token

If you need to change the Discord token:

```bash
sudo nano /etc/systemd/system/nyt-scorebot.service
# Update the Environment=DISCORD_TOKEN=... line
sudo systemctl daemon-reload
sudo systemctl restart nyt-scorebot
```

---

## Quick Reference

| Task | Command |
|---|---|
| Start the bot | `sudo systemctl start nyt-scorebot` |
| Stop the bot | `sudo systemctl stop nyt-scorebot` |
| Restart the bot | `sudo systemctl restart nyt-scorebot` |
| Check status | `sudo systemctl status nyt-scorebot` |
| View live logs | `sudo journalctl -u nyt-scorebot -f` |
| View recent logs | `sudo journalctl -u nyt-scorebot --no-pager -n 100` |
| Check Java version | `java -version` |
| Check disk space | `df -h /opt/scorebot` |
| Check database size | `ls -lh /opt/scorebot/data/` |
