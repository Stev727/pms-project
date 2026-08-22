#!/usr/bin/env bash
# 测试库重灌快照（在生产机 10.1.3.32 上执行）
# 网络现状：生产->测试单向可达（SSH 22/HTTP 80），测试->生产不通，故本脚本在生产机运行
# 流程：本地只读导出 -> 经 SSH 导入测试库 -> 远程重设测试账号密码
# ⚠️ 会覆盖测试库全部数据（含测试期产生的数据，含 deleted 标记等均回到生产快照状态）
set -euo pipefail
TEST=ai@10.1.4.23
DB=ruoyi-vue-pro
PW=Rdpms@2026!
HASH='$2a$10$VMzjD8jLjsqkWcmirniOF.HL4ddZJdO0zFh9AuLacWAsD/AAkuTEi'
echo "[1/3] 生产库只读导出"
mysqldump -uroot -p"$PW" --single-transaction "$DB" > /tmp/prod_snapshot.sql
[ -s /tmp/prod_snapshot.sql ] || { echo "!! 导出为空"; exit 1; }
echo "[2/3] 导入测试库（覆盖）"
ssh "$TEST" "mysql -uroot -p'$PW' $DB" < /tmp/prod_snapshot.sql
echo "[3/3] 重设测试账号密码(Test@2026!)"
ssh "$TEST" "mysql -uroot -p'$PW' $DB -e \"UPDATE system_users SET password='$HASH' WHERE username IN ('admin','ding_10488','pmtest');\""
rm -f /tmp/prod_snapshot.sql
echo "OK 测试库已与生产快照一致；admin/ding_10488/pmtest 密码已重置为 Test@2026!"
echo "提示: 涉及权限类改动时，登录测试机手动清理 Redis 缓存后重启后端"
