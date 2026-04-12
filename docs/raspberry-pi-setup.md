# Raspberry Pi Deployment Guide

This guide takes you from a **blank Raspberry Pi 4** all the way to a fully automated deployment using the GitHub Actions CI/CD pipeline. Follow the parts in order — some steps depend on previous ones.

## What You'll Need

| Item | Details |
|---|---|
| **Raspberry Pi 4** | Any RAM variant |
| **MicroSD card** | 8 GB minimum; 16 GB+ recommended |
| **MicroSD card reader** | For imaging the card from your computer |
| **Ethernet cable** | Recommended for first setup; Wi-Fi instructions included |
| **Power supply** | Official Pi 4 USB-C power supply (5V 3A) |
| **Local machine** | macOS, Linux, or Windows computer for imaging and SSH |
| **Discord bot token** | From the [Discord Developer Portal](https://discord.com/developers/applications) — **Message Content Intent** must be enabled |
| **GitHub repo access** | Admin permissions on the `nyt-scorebot` repository |

---

## Part 0: Generate Your SSH Key (Do This First)

**You must do this before imaging the SD card.** Raspberry Pi Imager will embed your public key into the image — the key must already exist at the time of imaging.

On your **local machine**, run:

```bash
ssh-keygen -t ed25519 -C "scorebot-deploy" -f ~/.ssh/scorebot_deploy
# When asked for a passphrase, press Enter twice (no passphrase needed)
```

This creates two files:

| File | What it is |
|---|---|
| `~/.ssh/scorebot_deploy` | **Private key** — stays on your machine and goes into GitHub as a secret |
| `~/.ssh/scorebot_deploy.pub` | **Public key** — gets embedded in the Pi image by Raspberry Pi Imager |

View your public key — you'll need to copy it in the next step:

```bash
cat ~/.ssh/scorebot_deploy.pub
# Output will look like: ssh-ed25519 AAAA... scorebot-deploy
```

---

## Part 1: Image the SD Card

### 1.1 — Download Raspberry Pi Imager

Download and install [Raspberry Pi Imager](https://www.raspberrypi.com/software/) for your operating system.

### 1.2 — Choose the OS and Storage

1. Insert your microSD card into your card reader
2. Open Raspberry Pi Imager
3. Click **"Choose Device"** → select **Raspberry Pi 4**
4. Click **"Choose OS"** → **Raspberry Pi OS (other)** → **Raspberry Pi OS Lite (64-bit)**
   > Lite is recommended for a headless server — no desktop environment, smaller footprint
5. Click **"Choose Storage"** → select your microSD card

### 1.3 — Configure OS Customisation Settings

Click **"Next"**, then when prompted **"Would you like to apply OS customisation settings?"** click **"Edit Settings"**.

#### General tab

| Setting | Value |
|---|---|
| **Set hostname** | e.g. `botnatius` (you'll reach it at `botnatius.local`) |
| **Set username and password** | Choose a username (e.g. `wando`) and a strong password — **note both down** |
| **Configure wireless LAN** | Fill in your Wi-Fi SSID and password if not using Ethernet |
| **Set locale settings** | Set your timezone and keyboard layout |

> **Important:** The username you set here is what you'll use for SSH and in the `PI_USER` GitHub secret. There is no default `pi` user in modern Raspberry Pi OS.

#### Services tab

| Setting | Value |
|---|---|
| **Enable SSH** | ✅ Checked |
| **Allow public-key authentication only** | ✅ Selected |
| **Authorised keys** | Paste the contents of `~/.ssh/scorebot_deploy.pub` |

To copy your public key to paste:

```bash
# macOS
cat ~/.ssh/scorebot_deploy.pub | pbcopy

# Linux
cat ~/.ssh/scorebot_deploy.pub | xclip -selection clipboard

# Windows (Git Bash)
cat ~/.ssh/scorebot_deploy.pub | clip
```

Click **"Save"**, then **"Yes"** to apply the settings.

### 1.4 — Write the Image

Click **"Yes"** to confirm writing to the SD card. Imager will download the OS, write it, and verify it. This takes 5–15 minutes depending on your connection speed.

When complete, remove the SD card and insert it into your Raspberry Pi.

---

## Part 2: First Boot and Connection

### 2.1 — Boot the Pi

1. Insert the imaged SD card into the Pi
2. Connect an Ethernet cable (or rely on the Wi-Fi you configured)
3. Plug in the power supply
4. Wait approximately **60–90 seconds** for first boot (it's slower than subsequent boots as it initialises the filesystem)

### 2.2 — Find the Pi on Your Network

**Option A — Using the hostname (easiest):**

```bash
ping botnatius.local
# If it responds, note the IP address shown
```

> If `botnatius.local` doesn't resolve, try `raspberrypi.local` or wait another 30 seconds and retry.

**Option B — Check your router:**
Log into your router's admin page and look at the list of connected devices. Find the one named `botnatius` and note its IP address.

### 2.3 — SSH Into the Pi

```bash
ssh -i ~/.ssh/scorebot_deploy YOUR_USERNAME@botnatius.local
# Or use the IP address:
ssh -i ~/.ssh/scorebot_deploy YOUR_USERNAME@192.168.1.42
```

Replace `YOUR_USERNAME` with the username you set in Raspberry Pi Imager (e.g. `wando`).

You'll see a prompt like this on first connection — type `yes`:

```
The authenticity of host 'botnatius.local' can't be established.
Are you sure you want to continue connecting? yes
```

You should now be logged into the Pi. Run a quick sanity check:

```bash
uname -a
# Expected: Linux scorebot 6.x.x ... aarch64 GNU/Linux
```

---

## Part 3: System Configuration

All commands in this section run **on the Pi** via SSH.

### 3.1 — Update Packages

```bash
sudo apt update && sudo apt upgrade -y
```

This may take a few minutes on first run.

### 3.2 — Set a Static IP Address

A stable, predictable IP ensures GitHub Actions can always reach the Pi. The recommended approach is a **DHCP reservation on your router** — this is more reliable than configuring a static IP on the Pi itself.

**Option A — Router DHCP Reservation (Recommended):**

1. Log into your router's admin page (usually `http://192.168.1.1` or `http://192.168.0.1`)
2. Find the connected devices or DHCP client list
3. Find the Pi (hostname: `botnatius`)
4. Assign it a static/reserved IP, e.g. `192.168.1.42`

**Option B — Static IP on the Pi (Raspberry Pi OS Bookworm):**

Raspberry Pi OS Bookworm uses NetworkManager. Find your connection name first:

```bash
nmcli con show
# Look for something like "Wired connection 1" or "preconfigured" (Wi-Fi)
```

Set a static IP (adjust values for your network):

```bash
# For Ethernet:
sudo nmcli con mod "Wired connection 1" \
  ipv4.method manual \
  ipv4.addresses 192.168.1.42/24 \
  ipv4.gateway 192.168.1.1 \
  ipv4.dns "8.8.8.8 1.1.1.1"
sudo nmcli con up "Wired connection 1"

# For Wi-Fi (replace with your SSID):
sudo nmcli con mod "YOUR_SSID" \
  ipv4.method manual \
  ipv4.addresses 192.168.1.42/24 \
  ipv4.gateway 192.168.1.1 \
  ipv4.dns "8.8.8.8 1.1.1.1"
sudo nmcli con up "YOUR_SSID"
```

Verify the new IP is active:

```bash
hostname -I
# Should show your chosen static IP
```

### 3.3 — Install Java

```bash
sudo apt install -y openjdk-17-jre-headless
```

If that fails with `Package ... is not available`, Raspberry Pi OS only has Java 21 available. That's fine — the bot supports it:

```bash
sudo apt install -y openjdk-21-jre-headless
```

Verify:

```bash
java -version
# Expected: openjdk version "17.x.x" or "21.x.x"
```

Either version works.

---

## Part 4: Bot Service User and Directories

Create a dedicated system user so the bot process runs with minimal privileges.

### 4.1 — Create the Service User

```bash
sudo useradd --system --shell /usr/sbin/nologin scorebot
```

This creates a user named `scorebot` with no login shell — it can run processes but no one can log into the Pi as this user.

### 4.2 — Create the Directory Structure

```bash
sudo mkdir -p /opt/scorebot/data
sudo chown YOUR_USERNAME:scorebot /opt/scorebot
sudo chmod 755 /opt/scorebot
sudo chown scorebot:scorebot /opt/scorebot/data
sudo chmod 750 /opt/scorebot/data
```

Replace `YOUR_USERNAME` with your Pi username (e.g. `wando`).

The directory layout:

```
/opt/scorebot/
├── nyt-scorebot-app-1.0-SNAPSHOT.jar   ← written by GitHub Actions (SCP as YOUR_USERNAME)
└── data/
    └── scorebot.mv.db                  ← written by the service (as scorebot user)
```

**Why this ownership split:**
- `YOUR_USERNAME` owns `/opt/scorebot/` — so GitHub Actions can SCP the JAR there
- `scorebot` owns `data/` — so the service process can write the H2 database
- Mode `755` on `/opt/scorebot/` allows the `scorebot` user to read the JAR without granting write access (prevents JAR tampering)
- Mode `750` on `data/` confines write access to the `scorebot` user only (database isolation)

### 4.3 — Grant the Deploy User sudo Access for the Service

GitHub Actions restarts the bot via `sudo systemctl restart`. Allow your user to do this without a password:

```bash
sudo visudo -f /etc/sudoers.d/scorebot-deploy
```

Add this single line (replace `YOUR_USERNAME`):

```
YOUR_USERNAME ALL=(root) NOPASSWD: /usr/bin/systemctl restart nyt-scorebot, /usr/bin/systemctl stop nyt-scorebot, /usr/bin/systemctl start nyt-scorebot
```

Save and exit (`Ctrl+X`, `Y`, `Enter` in nano).

> **Security note:** `(root)` restricts the commands to run only as the `root` user. This prevents privilege escalation if the deploy key is compromised — an attacker cannot use `sudo -u <other-user>` to run these commands as arbitrary users.

---

## Part 5: Create the systemd Service

All commands in this section run **on the Pi** via SSH.

### 5.1 — Write the Service Unit File

```bash
sudo nano /etc/systemd/system/nyt-scorebot.service
```

Paste the following (replace `your-discord-bot-token-here`):

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

Save and exit (`Ctrl+X`, `Y`, `Enter`).

### 5.2 — Memory Settings

The `-Xms128m -Xmx256m` flags keep the JVM footprint small. Adjust based on your Pi's RAM:

| Pi 4 RAM | Recommended `-Xmx` |
|---|---|
| 2 GB | `256m` |
| 4 GB | `512m` |
| 8 GB | `512m` |

### 5.3 — Enable the Service

```bash
sudo systemctl daemon-reload
sudo systemctl enable nyt-scorebot
```

Don't start it yet — there's no JAR to run until after the first deployment.

---

## Part 6: Network Access (GitHub Actions → Pi)

GitHub Actions runners are in the cloud — they need to reach your Pi over SSH. Since your Pi is on a home network, you'll need to expose it externally.

### 6.1 — Port Forwarding

1. Log into your router's admin page (usually `http://192.168.1.1`)
2. Find the **Port Forwarding** section (sometimes under NAT, Firewall, or Advanced)
3. Create a new rule:

| Setting | Value |
|---|---|
| **External Port** | `2222` (non-standard port reduces automated scanning) |
| **Internal IP** | Your Pi's static IP, e.g. `192.168.1.42` |
| **Internal Port** | `22` |
| **Protocol** | TCP |

4. Find your home's public IP:

```bash
curl -s https://ifconfig.me
```

5. Test connectivity from outside your home network (e.g., disconnect from Wi-Fi and use your phone's hotspot):

```bash
ssh -i ~/.ssh/scorebot_deploy -p 2222 YOUR_USERNAME@YOUR_PUBLIC_IP "echo 'External SSH works!'"
```

> **Dynamic IP?** If your ISP changes your IP regularly, set up a free Dynamic DNS service like [DuckDNS](https://www.duckdns.org/) or [No-IP](https://www.noip.com/). You'll get a stable hostname (e.g. `mypi.duckdns.org`) to use instead of a raw IP.

### 6.2 — Alternatives to Port Forwarding

If port forwarding isn't available (CGNAT, restricted router, ISP block), use **Tailscale**. It creates a private mesh VPN between your machines — GitHub Actions can reach the Pi through a secure tunnel without exposing SSH to the internet.

#### Tailscale Setup

**Prerequisites:**
- A free Tailscale account (https://tailscale.com)

**Step 1 — Install Tailscale on the Pi:**

```bash
curl -fsSL https://tailscale.com/install.sh | sh
```

**Step 2 — Authenticate on the Pi:**

```bash
sudo tailscale up
```

This prints a browser link. Open it on your Mac, authenticate with Tailscale, and authorize the Pi. You'll see the Pi's Tailscale IP (e.g. `100.x.x.x`).

**Step 3 — Find the Pi's Tailscale IP:**

```bash
tailscale ip -4
# Output: 100.x.x.x
```

Note this IP — it's your `PI_HOST` for GitHub secrets.

**Step 4 — Install Tailscale on your Mac (optional but recommended):**

Visit https://tailscale.com and install. Authenticate with the same account. Your Mac is now on the Tailscale network.

**Step 5 — Test SSH through Tailscale (from your Mac):**

```bash
ssh -i ~/.ssh/scorebot_deploy wando@100.x.x.x
```

Replace `100.x.x.x` with the Pi's Tailscale IP from Step 3.

**Step 6 — Set GitHub Secrets for Tailscale:**

| Secret Name | Value |
|---|---|
| `PI_HOST` | Your Pi's Tailscale IP (e.g. `100.x.x.x`) |
| `PI_USER` | `wando` |
| `PI_SSH_PORT` | `22` |
| `PI_DEPLOY_PATH` | `/opt/scorebot/` |
| `PI_SERVICE_NAME` | `nyt-scorebot` |
| `TAILSCALE_OAUTH_CLIENT_ID` | OAuth credential (see Step 7) |
| `TAILSCALE_OAUTH_CLIENT_SECRET` | OAuth credential (see Step 7) |

**Step 7 — Create Tailscale OAuth Credentials:**

The deploy workflow uses OAuth for secure authentication. Create OAuth credentials in your Tailscale account:

1. Go to [Tailscale Admin Console](https://login.tailscale.com/admin/settings/keys)
2. Under **Keys**, find **OAuth clients** and click **Create OAuth client**
3. Set the name to `github-actions` or similar
4. Copy the **Client ID** and **Client Secret**
5. Add them as GitHub secrets:
   - `TAILSCALE_OAUTH_CLIENT_ID` ← Client ID
   - `TAILSCALE_OAUTH_CLIENT_SECRET` ← Client Secret

> The OAuth approach is more secure than machine auth keys — each deployment authenticates through OAuth instead of sharing a static key.

### 6.3 — Security Hardening

Since you're using SSH (via Tailscale or port forwarding), lock it down. **On the Pi:**

```bash
sudo nano /etc/ssh/sshd_config
```

Verify or set these values (password auth was disabled at imaging time, but confirm):

```
PasswordAuthentication no
PubkeyAuthentication yes

# Restrict access to your user only (replace YOUR_USERNAME)
AllowUsers YOUR_USERNAME
```

Apply the changes:

```bash
sudo systemctl restart sshd
```

No need for fail2ban with Tailscale (it's private and encrypted), but you can install it if using port forwarding:

```bash
sudo apt install -y fail2ban
sudo systemctl enable --now fail2ban
```

---

## Part 7: GitHub Repository Configuration

### 7.1 — Create the Production Environment

The `deploy.yml` workflow is gated by a GitHub environment named `production`.

1. Go to your repository on GitHub
2. **Settings → Environments → New environment**
3. Name it: `production`
4. (Optional) Enable **Required reviewers** to require manual approval before each deployment

### 7.2 — Add Repository Secrets

Go to **Settings → Secrets and variables → Actions → New repository secret** and add each of the following:

| Secret Name | Value | Notes |
|---|---|---|
| `PI_SSH_KEY` | Contents of `~/.ssh/scorebot_deploy` on your local machine | Include the full `-----BEGIN` and `-----END` lines |
| `PI_HOST` | Your public IP or DDNS hostname | e.g. `203.0.113.42` or `mypi.duckdns.org` |
| `PI_USER` | The username you set during imaging | e.g. `wando` |
| `PI_SSH_PORT` | The external port you forwarded | e.g. `2222` |
| `PI_DEPLOY_PATH` | Remote deployment directory | `/opt/scorebot/` |
| `PI_SERVICE_NAME` | systemd service name | `nyt-scorebot` |
| `DISCORD_TOKEN` | Your Discord bot token | From the Discord Developer Portal |

To get the private key content for `PI_SSH_KEY`:

```bash
# macOS
cat ~/.ssh/scorebot_deploy | pbcopy

# Linux
cat ~/.ssh/scorebot_deploy | xclip -selection clipboard

# Or just print it and copy manually:
cat ~/.ssh/scorebot_deploy
```

### 7.3 — Verify Secret Names Match the Workflow

The `deploy.yml` workflow expects exactly these secret names. You can verify by checking `.github/workflows/deploy.yml` in the repository.

---

## Part 8: Verify End-to-End

### 8.1 — Trigger the Pipeline

**Option A — Push to `main`:**

```bash
git commit --allow-empty -m "ci: trigger deployment pipeline"
git push origin main
```

**Option B — Manual trigger:**

1. Go to **Actions → Pipeline → Run workflow**
2. Select the `main` branch
3. Click **Run workflow**

### 8.2 — Monitor the Workflow

**Note:** Build and test run on every branch push. Deploy and release only run on pushes to `main`.

Watch the GitHub Actions run through each stage:

1. ✅ **Build** — compiles, runs unit tests, uploads JAR artifact
2. ✅ **Test** — runs E2E test against live Discord
3. ✅ **Deploy** — SCPs JAR to Pi, restarts systemd service (main branch only)
4. ✅ **Release** — creates a GitHub Release with the JAR attached (main branch only)

If the Deploy stage is waiting, check that you created the `production` environment (step 7.1) — it's required by the workflow.

### 8.3 — Verify on the Pi

SSH in and check everything landed correctly:

```bash
ssh -i ~/.ssh/scorebot_deploy -p 2222 YOUR_USERNAME@YOUR_PI_HOST

# Check the JAR was deployed
ls -lh /opt/scorebot/nyt-scorebot-app-1.0-SNAPSHOT.jar

# Check the service is running
sudo systemctl status nyt-scorebot

# Watch live logs
sudo journalctl -u nyt-scorebot -f
```

A healthy startup looks like:

```
Started NYT Scorebot Discord Bot.
... Logged in as YourBot#1234
```

### 8.4 — Verify in Discord

Open your Discord server. The bot should show as online (green status dot). Post a Wordle result in a monitored channel — the bot should respond with an acceptance or rejection message.

---

## Troubleshooting

### SSH: `Permission denied (publickey)`

| Cause | Fix |
|---|---|
| Wrong key specified | Use `-i ~/.ssh/scorebot_deploy` explicitly |
| Public key not on the Pi | Check `~/.ssh/authorized_keys` on the Pi contains your key |
| Permissions wrong on Pi | `chmod 700 ~/.ssh && chmod 600 ~/.ssh/authorized_keys` |
| Wrong username | Must match exactly what you set in Raspberry Pi Imager |

If you imaged the Pi without a valid public key in place, the easiest fix is to re-image (following this guide from Part 0).

### SSH: `Connection timed out` or `Connection refused`

| Cause | Fix |
|---|---|
| Port forwarding not set up | Check router rules; ensure external port maps to Pi IP:22 |
| Pi not running / wrong IP | `ping botnatius.local` from local machine |
| Wrong port specified | Try with and without `-p 2222` |
| Firewall blocking | `sudo ufw status` on the Pi — disable if not intentionally configured |

### SSH: `Host key verification failed`

This happens after re-imaging. Remove the stale key on your local machine:

```bash
ssh-keygen -R [YOUR_PI_HOST]:2222
```

### Service Won't Start

```bash
# See the full error
sudo journalctl -u nyt-scorebot --no-pager -n 50
```

| Error Message | Fix |
|---|---|
| `java: not found` or `No such file` | `which java` — if empty, reinstall: `sudo apt install -y openjdk-17-jre-headless` |
| `Unable to access jarfile` | JAR not deployed yet; check `/opt/scorebot/` for the file |
| `Permission denied` on data dir | `sudo chown scorebot:scorebot /opt/scorebot/data && sudo chmod 750 /opt/scorebot/data` |
| Exits immediately | Check `DISCORD_TOKEN` is set correctly in the service file |

### JAR Not Arriving on Pi (Deploy Step Fails)

Check the GitHub Actions deploy job logs for the exact error. Common causes:

```bash
# Check disk space on the Pi (run on the Pi)
df -h /opt/scorebot

# Check YOUR_USERNAME can write to /opt/scorebot
ls -la /opt/scorebot/
# Should show YOUR_USERNAME as owner

# Fix if needed:
sudo chown YOUR_USERNAME:scorebot /opt/scorebot
sudo chmod 755 /opt/scorebot
```

### Bot Connects but Doesn't Respond to Messages

1. Verify **Message Content Intent** is enabled: [Discord Developer Portal](https://discord.com/developers/applications) → Your Bot → Bot → Privileged Gateway Intents → **Message Content Intent** ✅
2. Check that the channel IDs and user IDs in `application.properties` match your Discord server
3. Check logs: `sudo journalctl -u nyt-scorebot --no-pager | grep -i "error\|warn\|reject"`

### Database Issues

The H2 database persists at `/opt/scorebot/data/scorebot.mv.db`. If you need to reset it:

```bash
sudo systemctl stop nyt-scorebot
sudo rm /opt/scorebot/data/scorebot.mv.db
sudo systemctl start nyt-scorebot
# Schema is recreated automatically on startup (ddl-auto=update)
```

### Updating the Bot Token

```bash
sudo nano /etc/systemd/system/nyt-scorebot.service
# Update the Environment=DISCORD_TOKEN=... line
sudo systemctl daemon-reload
sudo systemctl restart nyt-scorebot
```

---

## Quick Reference

### Pi Service Commands (run on the Pi via SSH)

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

### SSH From Local Machine

```bash
# Standard connection
ssh -i ~/.ssh/scorebot_deploy -p 2222 YOUR_USERNAME@YOUR_PI_HOST

# Run a single command
ssh -i ~/.ssh/scorebot_deploy -p 2222 YOUR_USERNAME@YOUR_PI_HOST "sudo systemctl status nyt-scorebot"
```
