# Patch Agent v2.0

A tool for coordinating Linux server patching across multiple clusters. It SSHs into servers, stops or starts services in the correct order, and provides a web UI so the infrastructure team can select and control servers visually.

---

## Table of Contents

1. [Overview](#overview)
2. [How It Works](#how-it-works)
3. [Project Structure](#project-structure)
4. [Excel Sheet Setup](#excel-sheet-setup)
5. [Configuration](#configuration)
6. [Installation](#installation)
7. [Running the Web UI](#running-the-web-ui)
8. [Running the CLI Tool](#running-the-cli-tool)
9. [Typical Patching Workflow](#typical-patching-workflow)
10. [Service Stop / Start Ordering](#service-stop--start-ordering)
11. [Execution Modes](#execution-modes)
12. [Troubleshooting](#troubleshooting)

---

## Overview

Patch Agent solves three problems that come up every monthly patching cycle:

- **Multiple clusters** — each cluster is isolated; you pick which one to work on via the UI's cluster tabs.
- **Multiple services per server** — one Excel row per service. The tool groups them by server and stops/starts in the right order automatically.
- **Coordination between people** — the infra team running the actual OS patching and the person managing services can hand off cleanly using row ranges (`1-8`, `9-15`, etc.) and the built-in progress history.

---

## How It Works

```
servers_template.xlsx
        │
        ▼
  Web UI (browser)  ──►  Select rows by cluster / checkbox / range
        │
        ▼
  Flask backend (patch_web.py)
        │
        ├── Groups selected rows by physical server
        │
        ├── Round-Robin servers  →  one server at a time (sequential)
        │       └── Services stop in row-ID order (ascending)
        │           Services start in row-ID order (descending / reversed)
        │
        └── Batch servers  →  all servers in parallel (threads)
                └── Same service ordering within each server
```

Each service action is streamed live to the browser log as it happens.

---

## Project Structure

```
SshTool/
├── patch_web.py              # Flask web app — main entry point for the UI
├── patch_agent.py            # CLI tool — alternative to the web UI
├── config.yaml               # SSH credentials and patching settings
├── servers_template.xlsx     # Server and service list (edit this with your servers)
├── requirements.txt          # Python dependencies
├── patch_state.json          # Auto-generated — tracks run history
├── patch_agent.log           # Auto-generated — full execution log
└── templates/
    └── index.html            # Web UI (served by Flask)
```

---

## Excel Sheet Setup

Open `servers_template.xlsx` and edit the **Servers** sheet. Each row represents **one service on one server**. If a server runs three services, it gets three rows.

| Column | Required | Description |
|--------|----------|-------------|
| **ID** | ✔ | Unique integer. Used to select rows in the UI (e.g. `1,3,5-10`). |
| **Cluster** | ✔ | Cluster name (e.g. `Cluster-A`). Shown as a filter tab in the UI. |
| **Server Name** | ✔ | Friendly name (e.g. `web-01`). Rows with the same Server Name + IP are treated as one physical server. |
| **Hostname / IP** | ✔ | SSH destination — IP address or resolvable hostname. |
| **Service Name** | ✔ | Label for this service, used in log output (e.g. `nginx`). |
| **Stop Command** | ✔ | Shell command to stop the service (e.g. `systemctl stop nginx`). |
| **Start Command** | ✔ | Shell command to start the service (e.g. `systemctl start nginx`). |
| **Mode** | ✔ | `round_robin` — server is patched one at a time. `batch` — server is patched in parallel with other batch servers. |
| **Notes** | — | Free text. Not used by the tool. |

### Example — server with two services

| ID | Cluster | Server Name | Hostname / IP | Service Name | Stop Command | Start Command | Mode |
|----|---------|-------------|---------------|--------------|--------------|---------------|------|
| 1 | Cluster-A | web-01 | 10.0.1.10 | nginx | systemctl stop nginx | systemctl start nginx | round_robin |
| 2 | Cluster-A | web-01 | 10.0.1.10 | node-app | systemctl stop node-app | systemctl start node-app | round_robin |

When you select rows 1 and 2 and click **STOP**, the tool will:
1. Connect to `10.0.1.10`
2. Stop `nginx` first (lower ID)
3. Stop `node-app` second (higher ID)

When you click **START**, the order reverses:
1. Start `node-app` first
2. Start `nginx` second

---

## Configuration

Edit `config.yaml` before running.

```yaml
ssh:
  username: "your_username"
  password: "your_password"       # Leave blank to use key-based auth
  private_key_path: ""            # e.g. ~/.ssh/id_rsa
  port: 22
  connect_timeout: 15             # seconds
  command_timeout: 30             # seconds

patching:
  round_robin_delay: 5            # seconds to wait between round-robin servers
  batch_max_workers: 10           # max parallel SSH connections for batch mode
  log_file: "patch_agent.log"     # set to "" to disable file logging
```

**SSH authentication priority:**
1. If `private_key_path` is set → uses the key file
2. Else if `password` is set → uses password auth
3. Else → relies on the SSH agent or default key (`~/.ssh/id_rsa`)

---

## Installation

### Requirements

- Python 3.10 or higher — download from [python.org](https://www.python.org/downloads/) if not installed
- Network access to the target servers over SSH (port 22 by default)

> **Windows note:** The Python installer on Windows registers the command as `python` (not `python3`). Replace `python3` with `python` in every command below when running on Windows.

### Steps

**1. Open a terminal in the project folder**

- **Windows:** Open PowerShell or Command Prompt, then:
  ```powershell
  cd C:\Users\YourName\Projects\SSHTool
  ```
- **macOS / Linux:**
  ```bash
  cd /path/to/SshTool
  ```

**2. (Recommended) Create a virtual environment**

```bash
# macOS / Linux
python3 -m venv venv
source venv/bin/activate

# Windows (PowerShell)
python -m venv venv
venv\Scripts\Activate.ps1

# Windows (Command Prompt)
python -m venv venv
venv\Scripts\activate.bat
```

**3. Install dependencies**

```bash
pip install -r requirements.txt
```

This installs: `flask`, `paramiko`, `openpyxl`, `pyyaml`, `colorama`.

> **Windows:** If you see a permissions error, run PowerShell as Administrator, or add `--user` to the pip command:
> ```powershell
> pip install --user -r requirements.txt
> ```

---

## Running the Web UI

```bash
# macOS / Linux
python3 patch_web.py

# Windows
python patch_web.py
```

Then open your browser at:

```
http://localhost:5000
```

The server listens on all interfaces (`0.0.0.0:5000`) so colleagues on the same network can reach it at `http://<your-ip>:5000`.

### What the UI looks like

```
┌─────────────────────────────────────────────────────────────────┐
│  🖥  Patch Agent  v2.0                    22 service rows loaded │
├─────────────────────────────────────────────────────────────────┤
│  [All]  [Cluster-A]  [Cluster-B]               ← cluster tabs   │
│                                                                  │
│  ☐  ID  Cluster    Server    Host/IP     Service  Mode  Notes   │
│  ☐   1  Cluster-A  web-01    10.0.1.10   nginx    RR           │
│  ☑   2  Cluster-A  web-01    10.0.1.10   node-app RR           │
│  ...                                                             │
│                                                                  │
│  Row selection: [ 1,3,5-10 ]  [Apply]   3 rows selected         │
│                                         [STOP Services] [START] │
├──────────────────────────────┬──────────────────────────────────┤
│  Progress  ████████░░  60%   │  Execution Log                   │
│                              │  ✔ [1] 10.0.1.10/nginx STOPPED  │
│  Recent Runs                 │  ✔ [2] 10.0.1.10/node-app …    │
│  STOP · 12 rows · 12 ok      │  · [4] 10.0.1.20/tomcat9 …     │
└──────────────────────────────┴──────────────────────────────────┘
```

### Selecting servers

There are three ways to select which rows to act on:

| Method | How |
|--------|-----|
| **Checkboxes** | Click individual checkboxes in the table |
| **Cluster tab** | Click a cluster tab to filter, then select all with the header checkbox |
| **Range input** | Type a range string and click **Apply** |

**Range string examples:**

| Input | Selects |
|-------|---------|
| `1,3,5` | Rows 1, 3, and 5 only |
| `5-15` | Rows 5 through 15 inclusive |
| `1,3,7-12` | Rows 1, 3, and 7 through 12 |
| `*` or blank | All visible rows |

### Dry-run mode

Tick the **Dry-run** checkbox before clicking STOP or START. The tool will print exactly what it would do (including which command it would run) without SSHing into any server. Use this to verify your selection before a real run.

---

## Running the CLI Tool

`patch_agent.py` is a terminal alternative that does not require a browser.

> **Windows:** replace `python3` with `python` in all commands below.

```bash
# Patch all servers
python3 patch_agent.py   # macOS / Linux
python  patch_agent.py   # Windows

# Patch a specific row range (hand off to a colleague)
python3 patch_agent.py --start-row 1 --stop-row 8

# Patch specific individual rows
python3 patch_agent.py --rows 1,3,5,7

# Resume — skip rows already completed in patch_state.json
python3 patch_agent.py --start-row 9 --resume

# Dry-run
python3 patch_agent.py --dry-run

# Show current saved progress
python3 patch_agent.py --status

# Clear saved progress and start fresh
python3 patch_agent.py --reset-state

# Use different files
python3 patch_agent.py --excel prod_servers.xlsx --config prod.yaml
```

**CLI flags reference:**

| Flag | Description |
|------|-------------|
| `--excel FILE` | Excel file to read (default: `servers_template.xlsx`) |
| `--config FILE` | Config file to use (default: `config.yaml`) |
| `--rows 1,3,5` | Process specific row IDs only |
| `--start-row N` | Process rows with ID ≥ N |
| `--stop-row N` | Process rows with ID ≤ N |
| `--resume` | Skip rows already marked done in `patch_state.json` |
| `--status` | Print progress summary and exit |
| `--reset-state` | Wipe `patch_state.json` |
| `--dry-run` | Print what would happen without SSHing |
| `--verbose` / `-v` | Show SSH connection details |
| `--mode-override` | Force `round_robin` or `batch` for all rows |

---

## Typical Patching Workflow

### Monthly patch cycle

```
Week before patch day
─────────────────────
1. Update servers_template.xlsx with any new or removed servers.
2. Run a dry-run across all rows to confirm everything looks correct:
      python3 patch_agent.py --dry-run
   or tick Dry-run in the UI.

Patch day — Person A (service team)
────────────────────────────────────
3. Open the Web UI:  python3 patch_web.py
4. Select Cluster-A tab.
5. Enter range  1-15  (round-robin servers) → Apply.
6. Click STOP Services — watch the live log.
7. Confirm all services are down, hand off to infra team.

Patch day — Infra team
───────────────────────
8. Apply OS patches to Cluster-A servers (yum/apt update, reboot, etc.)
9. Signal service team when done.

Patch day — Person A resumes
─────────────────────────────
10. Select the same rows → click START Services.
11. Verify services are back up (check your monitoring).
12. Repeat steps 4–11 for Cluster-B.
```

### Multi-person handoff using row ranges

If two people are splitting the work across rounds:

```bash
# Person A: handles rows 1-8
python3 patch_agent.py --start-row 1 --stop-row 8

# Person B: handles rows 9-15, skipping anything A already did
python3 patch_agent.py --start-row 9 --stop-row 15 --resume
```

`patch_state.json` is updated after every single server so nothing is lost if a run is interrupted.

---

## Service Stop / Start Ordering

When a physical server has multiple service rows, the order matters for dependencies.

**On STOP:** services are stopped in ascending row-ID order.
**On START:** services are started in descending row-ID order (automatic reverse).

**Example — web-01 has nginx and node-app:**

| ID | Service |
|----|---------|
| 1 | nginx |
| 2 | node-app |

- STOP: nginx (1) → node-app (2) — stop the frontend first, then the backend
- START: node-app (2) → nginx (1) — start the backend first, then expose the frontend

You control the order simply by choosing which row ID is lower. There is no separate ordering column to manage.

---

## Execution Modes

| Mode | Behaviour | When to use |
|------|-----------|-------------|
| `round_robin` | Servers processed one at a time. A configurable delay (`round_robin_delay` in `config.yaml`) is inserted between each server. | Critical servers where you want to observe each one before moving to the next. Typically the first 10–15 servers per cluster. |
| `batch` | All servers processed in parallel using a thread pool. | Non-critical or identical servers (cache nodes, monitoring, etc.) where speed matters more than caution. |

The mode is set per-row in the Excel sheet. You can mix modes freely within the same run.

---

## Troubleshooting

### `paramiko not installed`
```bash
pip install paramiko
```

### `flask not installed` / `ModuleNotFoundError`
```bash
pip install -r requirements.txt
```

### `Excel file not found`
Make sure `servers_template.xlsx` is in the same folder as `patch_web.py`, or pass a full path:
```bash
python3 patch_web.py  # reads servers_template.xlsx from the same directory
```

### `SSH Authentication failed`
- Check `config.yaml` — username and password (or key path) must be correct.
- Test SSH manually: `ssh username@hostname`
- If using a key, ensure `private_key_path` in `config.yaml` points to the right file and the key is not password-protected (or that the SSH agent has it loaded).

### `Connection timed out`
- Verify the server is reachable: `ping hostname`
- Check that port 22 is open: `nc -zv hostname 22`
- Increase `connect_timeout` in `config.yaml`.

### Service command fails with `exit code 1`
- Test the stop/start commands manually by SSHing in and running them.
- Make sure the user in `config.yaml` has `sudo` rights if the commands require it.
  Update the command to: `sudo systemctl stop nginx`

### Web UI shows `Could not load servers`
- Check that `servers_template.xlsx` exists and has the correct column headers.
- Check the terminal running `patch_web.py` for the Python traceback.

### Port 5000 already in use

```bash
# macOS / Linux
export FLASK_RUN_PORT=5001
python3 patch_web.py

# Windows (PowerShell)
$env:FLASK_RUN_PORT=5001
python patch_web.py
```

Or edit the last line of `patch_web.py` and change `port=5000` to any free port.

---

## Windows Quick-Start

If you are on Windows and this is your first time, follow these steps exactly:

**1. Check Python is installed**

Open PowerShell and run:
```powershell
python --version
```
You should see `Python 3.10.x` or higher. If not, download it from [python.org](https://www.python.org/downloads/) and tick **"Add Python to PATH"** during install.

**2. Navigate to the project**
```powershell
cd C:\Users\YourName\Projects\SSHTool
```

**3. Install dependencies**
```powershell
pip install -r requirements.txt
```

**4. Edit config.yaml** — set your SSH username and password.

**5. Start the web app**
```powershell
python patch_web.py
```

**6. Open** `http://localhost:5000` in your browser.

### Common Windows errors

| Error | Fix |
|-------|-----|
| `'python3' is not recognized` | Use `python` instead of `python3` on Windows |
| `IndentationError` or `SyntaxError` on first run | Open the `.py` file in VS Code, click the encoding label bottom-right, choose **Save with Encoding → UTF-8** |
| `pip is not recognized` | Re-run the Python installer and tick **"Add Python to PATH"** |
| `Activate.ps1 cannot be loaded` (virtual env) | Run `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` in PowerShell first |
| Port 5000 blocked by Windows Firewall | Allow Python through the firewall, or use a different port (see above) |
