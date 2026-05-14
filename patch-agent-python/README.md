# Patch Agent — Python

Python implementation of the Linux Server Patching Agent.  
Contains two entry points:

| File | Purpose |
|------|---------|
| `patch_web.py` | Flask web application — browser UI + REST API |
| `patch_agent.py` | Command-line tool — headless patching runs |

Shared data files (`servers_template.xlsx`, `patch_state.json`, `patch_agent.log`) live one level up in the `SshTool/` root so both the Python and Spring Boot apps can share them.

---

## Prerequisites

| Tool | Version | Check |
|------|---------|-------|
| Python | 3.11+ | `python3 --version` |
| pip | 23+ | `pip --version` |

---

## Installation

```bash
# From the SshTool/ folder:
cd patch-agent-python

pip install -r requirements.txt
```

Dependencies installed:

| Package | Purpose |
|---------|---------|
| `flask` | Web server + SSE streaming |
| `paramiko` | SSH client |
| `openpyxl` | Excel inventory reader |
| `pyyaml` | Config file loader |
| `ldap3` | Active Directory / LDAP authentication |
| `colorama` | Coloured terminal output (CLI only) |

---

## Configuration

Edit **`config.yaml`** before starting either app:

```yaml
ssh:
  username: "your_username"    # overridden at runtime by AD login credentials
  password: "your_password"    # Leave blank to use key-based auth
  private_key_path: ""         # e.g. ~/.ssh/id_rsa

patching:
  round_robin_delay: 5         # seconds between round-robin servers
  batch_max_workers: 10        # max parallel SSH threads (batch mode)
  log_file: "patch_agent.log"  # resolved relative to SshTool/ root

ldap:
  environment: nonprod         # "nonprod" | "prod"  (default — overridden at login)

  nonprod:
    server:         "ldap://nonprod-dc01.company.com:389"
    domain:         "NONPROD"
    base_dn:        "DC=nonprod,DC=company,DC=com"
    required_group: "CN=patch-admins,OU=Groups,DC=nonprod,DC=company,DC=com"
    recursive_group: true

  prod:
    server:         "ldap://prod-dc01.company.com:389"
    domain:         "PROD"
    base_dn:        "DC=prod,DC=company,DC=com"
    required_group: "CN=patch-admins,OU=Groups,DC=prod,DC=company,DC=com"
    recursive_group: true
```

**`ldap.environment`** sets the fallback default. At runtime the user chooses the environment on the login screen, which selects the matching LDAP block automatically.

---

## Running the Web App (`patch_web.py`)

```bash
cd patch-agent-python

python3 patch_web.py
```

Open **http://localhost:5001**

### Login

The login screen asks for three things:

1. **Environment** — `Non-Prod` or `Prod` (dropdown)
2. **Username** — AD username (no domain prefix required)
3. **Password** — AD password

Credentials are validated against the Active Directory server for the chosen environment using an NTLM bind. Group membership is checked recursively (nested groups are resolved). A server-side session is created on success — only a random session token is stored in the browser cookie; credentials never leave the server.

Sessions expire after **8 hours** (matching the AD password rotation window).

### Environment scoping

After login, every API call is scoped to the environment chosen at login:

- `/api/servers` returns only rows tagged with the session environment in the Excel `Environment` column
- Jobs and status checks only touch servers in the session environment
- The environment is shown as a badge in the top bar for the duration of the session

---

## Running the CLI Tool (`patch_agent.py`)

```bash
cd patch-agent-python

# Dry run — shows what would happen without SSH-ing
python3 patch_agent.py --dry-run

# Stop all servers
python3 patch_agent.py --action stop

# Start servers 1–10 in cluster A
python3 patch_agent.py --action start --rows 1-10

# Resume an interrupted run
python3 patch_agent.py --action stop --resume
```

Key flags:

| Flag | Default | Description |
|------|---------|-------------|
| `--action` | `stop` | `stop` or `start` |
| `--excel` | `../servers_template.xlsx` | Path to Excel inventory |
| `--config` | `config.yaml` | Path to YAML config |
| `--rows` | all | Row range e.g. `1,3,5-10` |
| `--dry-run` | off | Simulate without SSH |
| `--resume` | off | Skip already-completed rows |

> **Note:** The CLI tool uses the `ssh.username` / `ssh.password` from `config.yaml` directly. It does not go through the LDAP login flow.

---

## Project Structure

```
patch-agent-python/
├── patch_web.py          Flask web app — routes, SSE, session auth
├── patch_agent.py        CLI patching tool
├── config.yaml           SSH + LDAP configuration (edit before use)
├── requirements.txt      Python dependencies
└── templates/
    ├── login.html        Full-page AD login form (environment dropdown)
    └── index.html        Main web UI (server table, log panel, history)

SshTool/                  (shared — one level up)
├── servers_template.xlsx Server inventory (shared with Spring Boot app)
├── patch_state.json      Job progress / resume state
└── patch_agent.log       Runtime log file
```

---

## Excel Inventory (`servers_template.xlsx`)

Lives in `SshTool/` (one level up). Key columns:

| Column | Required | Description |
|--------|----------|-------------|
| ID | ✓ | Unique row number |
| Cluster | ✓ | Logical grouping shown in the cluster dropdown |
| Server Name | ✓ | Display name |
| Hostname / IP | ✓ | SSH target |
| Service Name | ✓ | Display label |
| Stop Command | ✓ | Shell command to stop the service |
| Start Command | ✓ | Shell command to start the service |
| Status Check Command | | `systemctl is-active <svc>` — enables green/red dot |
| Mode | ✓ | `round_robin` or `batch` |
| Delay (s) | | Per-server pause after stop/start (round-robin only) |
| Group | | Scopes dependency checks to servers in the same group |
| Notes | | Free text |
| Environment | | `nonprod` or `prod` — filters rows by login environment |

---

## Troubleshooting

**`ModuleNotFoundError: No module named 'ldap3'`**  
Run `pip install -r requirements.txt` from inside `patch-agent-python/`.

**Login fails with "Invalid username or password"**  
Verify the `ldap.nonprod.server` (or `prod`) URL and `domain` in `config.yaml`. Confirm the AD server is reachable from the host running the app.

**Login fails with "not a member of the required group"**  
The user authenticated successfully but is not in `required_group`. Either add them to the group in AD, or clear `required_group` in `config.yaml` to allow any AD user.

**`Excel not found`**  
`servers_template.xlsx` is expected at `../servers_template.xlsx` relative to `patch_web.py`. Ensure the file exists in the `SshTool/` root.

**Port 5001 already in use**  
Change the port at the bottom of `patch_web.py`:
```python
app.run(host="0.0.0.0", port=5001, ...)
```
