package cn.iocoder.yudao.module.pms.service.dingtalk.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.module.pms.config.PmsDingTalkProperties;
import cn.iocoder.yudao.module.pms.dal.dataobject.dingtalk.PmsDingTalkConfigDO;
import cn.iocoder.yudao.module.pms.dal.mysql.dingtalk.PmsDingTalkConfigMapper;
import cn.iocoder.yudao.module.pms.service.dingtalk.DingTalkApiService;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 钉钉核心 API 服务实现
 */
@Service
@Slf4j
public class DingTalkApiServiceImpl implements DingTalkApiService {

    private static final String TOKEN_CACHE_KEY = "pms:dingtalk:access_token";
    private static final String GET_TOKEN_URL = "/gettoken?appkey={}&appsecret={}";
    private static final String SEND_WORK_MSG_URL = "/topapi/message/corpconversation/asyncsend_v2?access_token={}";
    private static final String GET_DEPT_LIST_URL = "/topapi/v2/department/listsub?access_token={}";
    private static final String GET_USER_LIST_URL = "/topapi/v2/user/list?access_token={}";
    private static final String GET_USERID_BY_MOBILE_URL = "/topapi/v2/user/getbymobile?access_token={}";

    @Resource
    private PmsDingTalkConfigMapper dingTalkConfigMapper;

    @Resource
    private PmsDingTalkProperties properties;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public PmsDingTalkConfigDO getConfig() {
        List<PmsDingTalkConfigDO> list = dingTalkConfigMapper.selectList(null);
        if (list != null && !list.isEmpty()) {
            return list.get(0);
        }
        // 返回默认配置
        PmsDingTalkConfigDO config = new PmsDingTalkConfigDO();
        config.setAppKey("");
        config.setAppSecret("");
        config.setAgentId(0L);
        config.setNotifyEnabled(false);
        config.setSsoEnabled(false);
        config.setSyncEnabled(false);
        config.setStatus("disabled");
        return config;
    }

    @Override
    public void updateConfig(PmsDingTalkConfigDO config) {
        PmsDingTalkConfigDO existing = getConfig();
        if (existing.getConfigId() != null) {
            config.setConfigId(existing.getConfigId());
            dingTalkConfigMapper.updateById(config);
        } else {
            dingTalkConfigMapper.insert(config);
        }
        // 清除 token 缓存，强制重新获取
        stringRedisTemplate.delete(TOKEN_CACHE_KEY);
    }

    @Override
    public String getAccessToken() {
        // 先从 Redis 缓存取
        String cachedToken = stringRedisTemplate.opsForValue().get(TOKEN_CACHE_KEY);
        if (StrUtil.isNotBlank(cachedToken)) {
            return cachedToken;
        }

        PmsDingTalkConfigDO config = getConfig();
        if (StrUtil.isBlank(config.getAppKey()) || StrUtil.isBlank(config.getAppSecret())) {
            log.warn("[DingTalk] AppKey/AppSecret 未配置，无法获取 access_token");
            return null;
        }

        String url = properties.getBaseUrl() + StrUtil.format(GET_TOKEN_URL, config.getAppKey(), config.getAppSecret());
        try {
            HttpResponse response = HttpRequest.get(url).timeout(10000).execute();
            JSONObject result = JSONUtil.parseObj(response.body());
            if (result.getInt("errcode") == 0) {
                String token = result.getStr("access_token");
                // 缓存到 Redis
                stringRedisTemplate.opsForValue().set(TOKEN_CACHE_KEY, token,
                        properties.getTokenCacheSeconds(), TimeUnit.SECONDS);
                log.info("[DingTalk] 获取 access_token 成功");
                return token;
            } else {
                log.error("[DingTalk] 获取 access_token 失败: {}", result);
                return null;
            }
        } catch (Exception e) {
            log.error("[DingTalk] 获取 access_token 异常", e);
            return null;
        }
    }

    @Override
    public String sendWorkNotification(String userIdList, String title, String content, String detailUrl) {
        String token = getAccessToken();
        if (token == null) {
            log.warn("[DingTalk] access_token 为空，跳过发送工作通知");
            return null;
        }

        PmsDingTalkConfigDO config = getConfig();
        String url = properties.getBaseUrl() + StrUtil.format(SEND_WORK_MSG_URL, token);

        JSONObject msg = new JSONObject();
        // #4 增强：有 detailUrl 时用 link 卡片样式（OA 风格，整卡片可点击跳转），否则降级为 text
        if (StrUtil.isNotBlank(detailUrl)) {
            msg.set("msgtype", "link");
            JSONObject link = new JSONObject();
            link.set("title", title);
            link.set("text", content);
            link.set("messageUrl", detailUrl);
            // 从 detailUrl 提取前端基址拼接 logo 作为卡片缩略图（钉钉要求 picUrl 非空）
            String picBase = detailUrl;
            int pmsIdx = detailUrl.indexOf("/pms/");
            if (pmsIdx > 0) {
                picBase = detailUrl.substring(0, pmsIdx);
            }
            link.set("picUrl", picBase + "/logo.gif");
            msg.set("link", link);
            log.info("[DingTalk] 发送 link 工作通知 messageURL={}", detailUrl);
        } else {
            msg.set("msgtype", "text");
            JSONObject textContent = new JSONObject();
            textContent.set("content", title + "\n\n" + content);
            msg.set("text", textContent);
        }

        JSONObject body = new JSONObject();
        body.set("agent_id", config.getAgentId());
        body.set("userid_list", userIdList);
        body.set("msg", msg);

        try {
            HttpResponse response = HttpRequest.post(url)
                    .body(body.toString())
                    .timeout(10000)
                    .execute();
            JSONObject result = JSONUtil.parseObj(response.body());
            if (result.getInt("errcode") == 0) {
                String taskId = result.getStr("task_id");
                log.info("[DingTalk] 工作通知发送成功: task_id={}, receivers={}, hasDetailUrl={}",
                        taskId, userIdList, StrUtil.isNotBlank(detailUrl));
                return taskId;
            } else {
                log.error("[DingTalk] 工作通知发送失败: {}", result);
                return null;
            }
        } catch (Exception e) {
            log.error("[DingTalk] 工作通知发送异常", e);
            return null;
        }
    }

    // ==================== 机器人单聊消息（独立机器人身份） ====================

    private static final String ROBOT_BATCH_SEND_URL = "https://api.dingtalk.com/v1.0/robot/oToMessages/batchSend";

    @Override
    public String sendRobotMessage(List<String> userIds, String title, String content, String detailUrl) {
        String token = getAccessToken();
        if (token == null) {
            log.warn("[DingTalk] access_token 为空，跳过发送机器人消息");
            return null;
        }

        PmsDingTalkConfigDO config = getConfig();
        if (StrUtil.isBlank(config.getRobotCode())) {
            log.warn("[DingTalk] robotCode 未配置，无法发送机器人消息");
            return null;
        }

        // 钉钉批量单聊 API 每次最多 20 个用户
        List<List<String>> batches = splitList(userIds, 20);
        String lastQueryKey = null;

        for (int i = 0; i < batches.size(); i++) {
            List<String> batch = batches.get(i);
            JSONObject body = new JSONObject();
            body.set("robotCode", config.getRobotCode());
            body.set("userIds", batch);

            if (StrUtil.isNotBlank(detailUrl)) {
                // 交互式卡片：两个独立跳转按钮「一键接收」+「查看详情」，减少员工操作
                body.set("msgKey", "sampleActionCard");
                JSONObject cardParam = new JSONObject();
                cardParam.set("title", title);
                cardParam.set("markdown", content);
                // 一键接收深链：在详情 URL 后追加 action=accept，前端自动弹出接收确认
                String acceptUrl = detailUrl.contains("?")
                        ? detailUrl + "&action=accept"
                        : detailUrl + "?action=accept";
                JSONObject btnAccept = new JSONObject();
                btnAccept.set("title", "一键接收");
                btnAccept.set("actionURL", acceptUrl);
                JSONObject btnView = new JSONObject();
                btnView.set("title", "查看详情");
                btnView.set("actionURL", detailUrl);
                JSONArray btns = new JSONArray();
                btns.add(btnAccept);
                btns.add(btnView);
                cardParam.set("btnOrientation", "1");
                cardParam.set("btns", btns);
                body.set("msgParam", cardParam.toString());
                log.info("[DingTalk] 发送机器人交互卡片(含一键接收) robotCode={} users={} acceptUrl={}",
                        config.getRobotCode(), batch.size(), acceptUrl);
            } else {
                // 纯文本（无详情链接时降级）
                body.set("msgKey", "sampleText");
                JSONObject textParam = new JSONObject();
                textParam.set("text", title + "\n\n" + content);
                body.set("msgParam", textParam.toString());
            }
            try {
                HttpRequest request = HttpRequest.post(ROBOT_BATCH_SEND_URL)
                        .header("x-acs-dingtalk-access-token", token)
                        .body(body.toString())
                        .timeout(10000);
                HttpResponse response = request.execute();
                JSONObject result = JSONUtil.parseObj(response.body());

                if (result.getInt("errcode") == null || result.getInt("errcode") == 0) {
                    String queryKey = result.getStr("processQueryKey");
                    log.info("[DingTalk] 机器人消息批次 {}/{} 发送成功: queryKey={}, users={}",
                            i + 1, batches.size(), queryKey, batch.size());
                    lastQueryKey = queryKey;
                } else {
                    log.error("[DingTalk] 机器人消息批次 {}/{} 发送失败: {}", i + 1, batches.size(), result);
                    // 某个批次失败不中断后续批次，但返回 null 表示部分失败
                    lastQueryKey = null;
                }
            } catch (Exception e) {
                log.error("[DingTalk] 机器人消息批次 {}/{} 发送异常", i + 1, batches.size(), e);
                lastQueryKey = null;
            }
        }

        return lastQueryKey;
    }

    /**
     * 将列表按指定大小分批
     */
    private <T> List<List<T>> splitList(List<T> list, int batchSize) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            result.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return result;
    }

    @Override
    public String sendMarkdownNotification(String userIdList, String title, String markdownText) {
        String token = getAccessToken();
        if (token == null) {
            return null;
        }

        PmsDingTalkConfigDO config = getConfig();
        String url = properties.getBaseUrl() + StrUtil.format(SEND_WORK_MSG_URL, token);

        JSONObject msg = new JSONObject();
        msg.set("msgtype", "markdown");
        JSONObject markdown = new JSONObject();
        markdown.set("title", title);
        markdown.set("text", markdownText);
        msg.set("markdown", markdown);

        JSONObject body = new JSONObject();
        body.set("agent_id", config.getAgentId());
        body.set("userid_list", userIdList);
        body.set("msg", msg);

        try {
            HttpResponse response = HttpRequest.post(url)
                    .body(body.toString())
                    .timeout(10000)
                    .execute();
            JSONObject result = JSONUtil.parseObj(response.body());
            if (result.getInt("errcode") == 0) {
                String taskId = result.getStr("task_id");
                log.info("[DingTalk] Markdown 通知发送成功: task_id={}", taskId);
                return taskId;
            } else {
                log.error("[DingTalk] Markdown 通知发送失败: {}", result);
                return null;
            }
        } catch (Exception e) {
            log.error("[DingTalk] Markdown 通知发送异常", e);
            return null;
        }
    }

    @Override
    public List<Map<String, Object>> getDepartmentList(Long parentDeptId) {
        String token = getAccessToken();
        if (token == null) {
            return new ArrayList<>();
        }

        String url = properties.getBaseUrl() + StrUtil.format(GET_DEPT_LIST_URL, token);
        JSONObject body = new JSONObject();
        body.set("dept_id", parentDeptId);

        try {
            HttpResponse response = HttpRequest.post(url)
                    .body(body.toString())
                    .timeout(10000)
                    .execute();
            JSONObject result = JSONUtil.parseObj(response.body());
            if (result.getInt("errcode") == 0) {
                JSONArray deptArray = result.getJSONArray("result");
                List<Map<String, Object>> list = new ArrayList<>();
                for (int i = 0; i < deptArray.size(); i++) {
                    JSONObject dept = deptArray.getJSONObject(i);
                    Map<String, Object> map = new HashMap<>();
                    map.put("dept_id", dept.getLong("dept_id"));
                    map.put("name", dept.getStr("name"));
                    map.put("parent_id", dept.getLong("parent_id"));
                    map.put("dept_id_list", dept.getJSONArray("dept_id_list"));
                    list.add(map);
                }
                return list;
            } else {
                log.error("[DingTalk] 获取部门列表失败: {}", result);
                return new ArrayList<>();
            }
        } catch (Exception e) {
            log.error("[DingTalk] 获取部门列表异常", e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<Map<String, Object>> getUserListByDept(Long deptId) {
        String token = getAccessToken();
        if (token == null) {
            return new ArrayList<>();
        }

        String url = properties.getBaseUrl() + StrUtil.format(GET_USER_LIST_URL, token);
        List<Map<String, Object>> allUsers = new ArrayList<>();
        int cursor = 0;
        int size = 100;

        // 分页获取
        while (true) {
            JSONObject body = new JSONObject();
            body.set("dept_id", deptId);
            body.set("cursor", cursor);
            body.set("size", size);

            try {
                HttpResponse response = HttpRequest.post(url)
                        .body(body.toString())
                        .timeout(10000)
                        .execute();
                JSONObject result = JSONUtil.parseObj(response.body());
                if (result.getInt("errcode") == 0) {
                    JSONObject pageResult = result.getJSONObject("result");
                    JSONArray userList = pageResult.getJSONArray("list");
                    if (userList == null || userList.isEmpty()) {
                        break;
                    }
                    for (int i = 0; i < userList.size(); i++) {
                        JSONObject user = userList.getJSONObject(i);
                        Map<String, Object> map = new HashMap<>();
                        map.put("userid", user.getStr("userid"));
                        map.put("unionid", user.getStr("unionid"));
                        map.put("name", user.getStr("name"));
                        map.put("avatar", user.getStr("avatar"));
                        map.put("mobile", user.getStr("mobile"));
                        map.put("email", user.getStr("email"));
                        map.put("job_number", user.getStr("job_number"));
                        map.put("title", user.getStr("title"));
                        map.put("work_place", user.getStr("work_place"));
                        map.put("dept_id_list", user.getJSONArray("dept_id_list"));
                        allUsers.add(map);
                    }
                    Boolean hasMore = pageResult.getBool("has_more");
                    if (hasMore != null && hasMore) {
                        cursor = pageResult.getInt("next_cursor");
                    } else {
                        break;
                    }
                } else {
                    log.error("[DingTalk] 获取用户列表失败: {}", result);
                    break;
                }
            } catch (Exception e) {
                log.error("[DingTalk] 获取用户列表异常", e);
                break;
            }
        }
        return allUsers;
    }

    @Override
    public String getUserIdByMobile(String mobile) {
        String token = getAccessToken();
        if (token == null || StrUtil.isBlank(mobile)) {
            return null;
        }

        String url = properties.getBaseUrl() + StrUtil.format(GET_USERID_BY_MOBILE_URL, token);
        JSONObject body = new JSONObject();
        body.set("mobile", mobile);

        try {
            HttpResponse response = HttpRequest.post(url)
                    .body(body.toString())
                    .timeout(10000)
                    .execute();
            JSONObject result = JSONUtil.parseObj(response.body());
            if (result.getInt("errcode") == 0) {
                return result.getStr("result");
            } else {
                log.warn("[DingTalk] 根据手机号获取 userid 失败: mobile={}, result={}", mobile, result);
                return null;
            }
        } catch (Exception e) {
            log.error("[DingTalk] 根据手机号获取 userid 异常", e);
            return null;
        }
    }

    @Override
    public String testConnection() {
        PmsDingTalkConfigDO config = getConfig();
        if (StrUtil.isBlank(config.getAppKey()) || StrUtil.isBlank(config.getAppSecret())) {
            return "FAIL: AppKey/AppSecret 未配置";
        }

        String token = getAccessToken();
        if (token != null) {
            return "OK: 连接成功，access_token 已获取";
        } else {
            return "FAIL: 无法获取 access_token，请检查 AppKey/AppSecret";
        }
    }
}
