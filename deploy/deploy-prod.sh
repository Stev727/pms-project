#!/usr/bin/env bash
# PMS 生产环境一键部署（在 10.1.3.32 上执行）
# ⚠️ 本脚本设计上不包含任何 mysql/redis 操作；只动 代码/构建产物/进程
set -euo pipefail
RDPMS=/home/ai/projects/rdpms
BRANCH=codex/pms-upgrade-prod-20260808
MVN=$(ls /opt/apache-maven-*/bin/mvn /home/ai/apache-maven-*/bin/mvn 2>/dev/null | head -1)
[ -n "$MVN" ] || { echo "!! 未找到 maven"; exit 1; }

echo "=============================================="
echo "  即将部署到【生产环境 10.1.3.32】"
echo "  本操作不动数据库，只更新代码并重启服务"
echo "=============================================="
read -r -p "确认请输入 yes: " CONFIRM
[ "$CONFIRM" = "yes" ] || { echo "已取消"; exit 1; }

cd "$RDPMS"
echo "[1/6] 工作区检查"
git diff-index --quiet HEAD -- || { echo "!! 有未提交修改，请先处理"; exit 1; }

echo "[2/6] 拉取代码 $BRANCH"
git pull origin "$BRANCH"

echo "[3/6] 构建后端"
cd "$RDPMS/yudao-source"
"$MVN" -pl yudao-server -am package -DskipTests -q

echo "[4/6] 备份并替换 jar"
TS=$(date +%Y%m%d_%H%M%S)
cp "$RDPMS/yudao-server.jar" "$RDPMS/yudao-server.jar.bak.$TS"
cp yudao-server/target/yudao-server.jar "$RDPMS/yudao-server.jar"

echo "[5/6] 重启后端"
PID=$(ss -tlnp 2>/dev/null | grep ':48080' | grep -oP 'pid=\K[0-9]+' | head -1 || true)
if [ -n "$PID" ]; then kill "$PID" || true; sleep 4; fi
cd "$RDPMS"
setsid java -jar yudao-server.jar --spring.profiles.active=local > /tmp/yudao-server.log 2>&1 < /dev/null &

echo "[6/6] 等待就绪"
for i in $(seq 1 30); do sleep 2; ss -tln | grep -q ':48080' && break; done
ss -tln | grep -q ':48080' || { echo "!! 后端未就绪！立即回滚: bash deploy/rollback-prod.sh"; exit 1; }
echo "✅ 部署完成 commit=$(git rev-parse --short HEAD)"
echo "$(date '+%F %T') deploy-prod $(git rev-parse --short HEAD) by $(whoami)" >> "$RDPMS/releases.log"
echo "前端由 Vite dev 自动热更。请立即抽查生产关键功能！"
