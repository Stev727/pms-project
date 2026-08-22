#!/usr/bin/env bash
# 测试库重灌快照（在测试机 10.1.4.23 执行；需已配置到生产机 ai@10.1.3.32 的免密 SSH）
# 流程：生产机只读导出 -> 覆盖导入测试库 -> 重设测试账号密码
# ⚠️ 会覆盖测试库全部数据（含测试期产生的数据）
set -euo pipefail
PROD=ai@10.1.3.32
DB=ruoyi-vue-pro
PW=Rdpms@2026!
HASH='$2a$10$VMzjD8jLjsqkWcmirniOF.HL4ddZJdO0zFh9AuLacWAsD/AAkuTEi'
echo "[1/3] 生产机只读导出"
ssh "$PROD" "mysqldump -uroot -p'$PW' --single-transaction $DB" > /tmp/prod_snapshot.sql
[ -s /tmp/prod_snapshot.sql ] || { echo "!! 导出为空"; exit 1; }
echo "[2/3] 导入测试库（覆盖）"
mysql -uroot -p"$PW" "$DB" < /tmp/prod_snapshot.sql
echo "[3/3] 重设测试账号密码(Test@2026!)"
mysql -uroot -p"$PW" "$DB" -e "UPDATE system_users SET password='$HASH' WHERE username IN ('admin','ding_10488','pmtest');"
echo "✅ 测试库已与生产快照一致；admin/ding_10488/pmtest 密码已重置为 Test@2026!"
echo "提示: 涉及权限类改动时，登录测试机手动清理 Redis 缓存后重启后端"
