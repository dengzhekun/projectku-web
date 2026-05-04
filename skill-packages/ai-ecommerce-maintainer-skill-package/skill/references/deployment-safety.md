# Deployment Safety

Use this reference before touching servers, Docker, domains, reverse proxies, or production/staging data.

## Golden Rules

- Do not deploy unless the user explicitly asks for deployment/update.
- Do not assume GitHub push updates the server.
- Identify the target server and project directory before changing anything.
- Back up code/config before replacement.
- Preserve database volumes and uploaded files.
- Restart only the minimum service needed.
- Do not touch unrelated compose projects or side services.

## Read-Only Server Discovery

Use commands like:

```bash
hostname
pwd
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
docker compose ls
ls -la /opt
```

Do not run destructive cleanup, reset, volume deletion, or broad service restarts during discovery.

## Before Deployment

Record:

- server IP/host alias;
- SSH key used;
- project directory;
- compose file;
- container names;
- exposed ports;
- volumes;
- environment files;
- unrelated services to avoid.

Check whether the server directory is a git repo. If not, deploy by file sync/release artifact, not `git pull`.

## Backup Pattern

Example:

```bash
mkdir -p /opt/project/backups
tar --exclude='./logs' --exclude='./node_modules' --exclude='./target' \
  -czf /opt/project/backups/current-$(date +%Y%m%d-%H%M%S).tgz \
  -C /opt/project current
```

Adapt excludes carefully. Do not exclude uploaded user files unless they are persisted in a separate volume and verified.

## Restart Pattern

Prefer:

```bash
docker compose up -d --build backend
```

Avoid:

```bash
docker compose down -v
docker system prune -a
rm -rf /var/lib/docker/volumes/...
```

## Post-Deployment Verification

Verify:

- containers are running and healthy;
- backend health/root endpoint;
- product API;
- auth smoke;
- fake token behavior;
- AI customer-service regression;
- knowledge-base indexing/query if changed;
- frontend page loads if frontend changed.

Do not claim production is updated until verification output supports it.
