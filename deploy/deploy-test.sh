#!/usr/bin/env bash
# PMS 测试环境一键部署（在 10.1.4.23 上执行）
# 原则：只动 代码/构建产物/进程，绝不操作数据库
set -euo pipefail
RDPMS=/home/ai/projects/rdpms
BRANCH=codex/pms-upgrade-prod-20260808
MVN=""
for c in /opt/apache-maven-*/bin/mvn /home/ai/apache-maven-*/bin/mvn; do
  [ -x "$c" ] && MVN="$c" && break
done
[ -n "$MVN" ] || { echo "!! 未找到 maven"; exit 1; }
echo "使用 maven: $MVN"

cd "$RDPMS"
echo "[1/6] 工作区检查"
git diff-index --quiet HEAD -- || { echo "!! 有未提交修改，请先 commit/stash"; exit 1; }

echo "[2/6] 拉取代码 $BRANCH"
git pull origin "$BRANCH"

echo "[3/6] 构建后端（首次较慢）"
cd "$RDPMS/yudao-source"
"$MVN" -pl yudao-server -am package -DskipTests -q

echo "[4/6] 备份并替换 jar"
TS=$(date +%Y%m%d_%H%M%S)
[ -f "$RDPMS/yudao-server.jar" ] && cp "$RDPMS/yudao-server.jar" "$RDPMS/yudao-server.jar.bak.$TS"
cp yudao-server/target/yudao-server.jar "$RDPMS/yudao-server.jar"

echo "[5/6] 重启后端"
PID=$(ss -tlnp 2>/dev/null | grep ':48080' | grep -oP 'pid=\K[0-9]+' | head -1 || true)
if [ -n "$PID" ]; then kill "$PID" || true; fi
# 等待端口真正释放再启动(旧进程 shutdown hook 最长约 30-60s;固定 sleep 4 会竞态:
# 新进程 Port already in use 启动失败,旧进程死透后服务空窗)
PORT_WAIT=0
while ss -tln 2>/dev/null | grep -q ':48080 '; do
  PORT_WAIT=$((PORT_WAIT+2))
  [ $PORT_WAIT -ge 90 ] && { echo "!! 端口 48080 释放超时(90s),强制 kill -9"; ss -tlnp 2>/dev/null | grep ':48080' | grep -oP 'pid=\K[0-9]+' | xargs -r kill -9; sleep 2; break; }
  sleep 2
done
echo "端口已释放(等待 ${PORT_WAIT}s),启动新进程..."
cd "$RDPMS"
setsid java -jar yudao-server.jar --spring.profiles.active=local > /tmp/yudao-server.log 2>&1 < /dev/null &

echo "[6/6] 等待就绪"
for i in $(seq 1 45); do sleep 2; ss -tln | grep -q ':48080' && break; done
ss -tln | grep -q ':48080' || { echo "!! 后端未就绪，查 /tmp/yudao-server.log"; exit 1; }
echo "OK 部署完成 commit=$(git rev-parse --short HEAD)"
echo "$(date '+%F %T') deploy-test $(git rev-parse --short HEAD) by $(whoami)" >> "$RDPMS/releases.log"
echo "前端由 Vite dev 自动热更（改 vite.config 时 Vite 自动重启）"
