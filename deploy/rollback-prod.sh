#!/usr/bin/env bash
# 生产回滚（<1 分钟）：恢复最近备份 jar；前端可选回退上一提交
set -euo pipefail
RDPMS=/home/ai/projects/rdpms
cd "$RDPMS"
BAK=$(ls -t yudao-server.jar.bak.* 2>/dev/null | head -1)
[ -n "$BAK" ] || { echo "!! 无备份可回滚"; exit 1; }
echo "回滚后端到: $BAK"
PID=$(ss -tlnp 2>/dev/null | grep ':48080' | grep -oP 'pid=\K[0-9]+' | head -1 || true)
if [ -n "$PID" ]; then kill "$PID" || true; sleep 4; fi
cp "$BAK" yudao-server.jar
setsid java -jar yudao-server.jar --spring.profiles.active=local > /tmp/yudao-server.log 2>&1 < /dev/null &
for i in $(seq 1 30); do sleep 2; ss -tln | grep -q ':48080' && break; done
ss -tln | grep -q ':48080' && echo "✅ 后端已回滚" || { echo "!! 回滚后未就绪，查 /tmp/yudao-server.log"; exit 1; }
echo "$(date '+%F %T') ROLLBACK -> $BAK by $(whoami)" >> "$RDPMS/releases.log"
echo "如需回滚前端: cd yudao-ui-admin-vue3 && git reset --hard HEAD@{1}"
