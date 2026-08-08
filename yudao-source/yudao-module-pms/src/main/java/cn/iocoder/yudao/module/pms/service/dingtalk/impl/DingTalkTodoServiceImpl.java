package cn.iocoder.yudao.module.pms.service.dingtalk.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.module.pms.config.PmsDingTalkProperties;
import cn.iocoder.yudao.module.pms.dal.dataobject.dingtalk.PmsDingTalkTodoDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.dingtalk.PmsDingTalkUserDO;
import cn.iocoder.yudao.module.pms.dal.mysql.dingtalk.PmsDingTalkTodoMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.dingtalk.PmsDingTalkUserMapper;
import cn.iocoder.yudao.module.pms.service.dingtalk.DingTalkApiService;
import cn.iocoder.yudao.module.pms.service.dingtalk.DingTalkTodoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 钉钉待办 Service 实现（#4 钉钉待办）
 *
 * <p>实现要点：
 * <ul>
 *   <li>调新版 API：{@code POST https://api.dingtalk.com/v1.0/todo/users/{unionId}/tasks}</li>
 *   <li>header: {@code x-acs-dingtalk-access-token: <access_token>}（与老版 token 同源）</li>
 *   <li>失败降级：捕获异常 → 落 pms_dingtalk_todo 失败记录 → 返回 false（不抛异常）</li>
 *   <li>幂等性：同一 (bizTaskId, userId) 已 pending 视为已创建，直接返回 true</li>
 * </ul>
 *
 * <p>钉钉待办 API 请求/响应结构按官方文档实现：
 * <ul>
 *   <li>请求 body: {@code {"summary":"xxx","description":"xxx","dueDate":1700000000000,
 *       "sourceName":"PMS","creatorId":"<unionId of creator>","priority":"high"}}</li>
 *   <li>响应: {@code {"taskId":"xxx","status":"0","incident":0}}</li>
 * </ul>
 *
 * <p>注意：未实际联调，请求/响应结构以官方文档为准，需真机验证。
 */
@Service
@Slf4j
public class DingTalkTodoServiceImpl implements DingTalkTodoService {

    /**
     * 钉钉待办创建成功状态码（与 token 无关，是业务返回 code）
     */
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_COMPLETED = "completed";
    private static final String STATUS_FAILED = "failed";

    /**
     * 待办创建 API（新版）：路径参数为接收人 unionId
     */
    private static final String CREATE_TODO_PATH = "/v1.0/todo/users/{unionId}/tasks";

    /**
     * 待办完成 API（新版）：路径参数为接收人 unionId 与待办 taskId
     */
    private static final String COMPLETE_TODO_PATH = "/v1.0/todo/users/{unionId}/tasks/{taskId}";

    @Resource
    private DingTalkApiService dingTalkApiService;

    @Resource
    private PmsDingTalkUserMapper dingTalkUserMapper;

    @Resource
    private PmsDingTalkTodoMapper dingTalkTodoMapper;

    @Resource
    private PmsDingTalkProperties properties;

    // ==================================================================
    // 创建待办
    // ==================================================================

    @Override
    public boolean createTodoForTask(Long bizTaskId, Long userId, String title, String content) {
        if (bizTaskId == null || userId == null || StrUtil.isBlank(title)) {
            log.warn("[DingTalkTodo] 参数缺失，跳过创建待办: bizTaskId={}, userId={}, title={}", bizTaskId, userId, title);
            return false;
        }
        // 幂等性：已有 pending 记录直接返回成功
        PmsDingTalkTodoDO existing = dingTalkTodoMapper.selectByBizTaskIdAndUserId(bizTaskId, userId);
        if (existing != null && STATUS_PENDING.equals(existing.getStatus()) && StrUtil.isNotBlank(existing.getDingTodoId())) {
            log.info("[DingTalkTodo] 已存在 pending 待办，跳过创建: bizTaskId={}, userId={}", bizTaskId, userId);
            return true;
        }

        // 拿接收人 unionId
        PmsDingTalkUserDO dingUser = dingTalkUserMapper.selectByUserId(userId);
        if (dingUser == null || StrUtil.isBlank(dingUser.getDingtalkUnionId())) {
            log.warn("[DingTalkTodo] 接收人缺少钉钉 unionId 映射，跳过创建: userId={}", userId);
            saveFailedRecord(bizTaskId, userId, null, title, content, "接收人缺少钉钉 unionId 映射");
            return false;
        }
        String unionId = dingUser.getDingtalkUnionId();

        // 拿 token
        String token = dingTalkApiService.getAccessToken();
        if (StrUtil.isBlank(token)) {
            log.warn("[DingTalkTodo] access_token 为空，跳过创建待办: bizTaskId={}, userId={}", bizTaskId, userId);
            saveFailedRecord(bizTaskId, userId, unionId, title, content, "access_token 为空");
            return false;
        }

        // 调用新版 API 创建待办
        String url = properties.getNewApiBaseUrl() + StrUtil.format(CREATE_TODO_PATH, unionId);
        JSONObject body = new JSONObject();
        body.set("summary", title);
        body.set("description", StrUtil.isBlank(content) ? "" : content);
        // sourceName 用于在钉钉待办里显示来源
        body.set("sourceName", "PMS");
        // priority: L / M / H（低/中/高），PMS 任务派发默认中等
        body.set("priority", "M");
        // creatorId：钉钉要求 unionId，这里用接收人自己的（自派自待办场景），
        // 跨用户派发场景下用任务派发人的 unionId 更合适，但当前 sendNotifyDirect 调用方
        // 传入的 receiverUserIds 即为接收人，无法区分派发人。简化处理：用接收人自己。
        body.set("creatorId", unionId);

        try {
            HttpResponse response = HttpRequest.post(url)
                    .header("x-acs-dingtalk-access-token", token)
                    .body(body.toString())
                    .timeout(10000)
                    .execute();
            if (!response.isOk()) {
                log.error("[DingTalkTodo] 创建待办 HTTP 异常: bizTaskId={}, userId={}, status={}, body={}",
                        bizTaskId, userId, response.getStatus(), response.body());
                saveFailedRecord(bizTaskId, userId, unionId, title, content,
                        "HTTP " + response.getStatus() + ": " + response.body());
                return false;
            }
            JSONObject result = JSONUtil.parseObj(response.body());
            // 官方文档：成功无 errcode 字段，直接返回 {taskId, status, incident}
            // 部分错误场景会返回 errcode + message
            Integer errcode = result.getInt("errcode");
            if (errcode != null && errcode != 0) {
                log.error("[DingTalkTodo] 创建待办业务失败: bizTaskId={}, userId={}, result={}",
                        bizTaskId, userId, result);
                saveFailedRecord(bizTaskId, userId, unionId, title, content, result.toString());
                return false;
            }
            String dingTodoId = result.getStr("taskId");
            if (StrUtil.isBlank(dingTodoId)) {
                // 钉钉某些版本字段名可能是 task_id
                dingTodoId = result.getStr("task_id");
            }
            if (StrUtil.isBlank(dingTodoId)) {
                log.warn("[DingTalkTodo] 创建待办响应缺少 taskId: bizTaskId={}, userId={}, result={}",
                        bizTaskId, userId, result);
                saveFailedRecord(bizTaskId, userId, unionId, title, content, "响应缺少 taskId: " + result);
                return false;
            }
            saveSuccessRecord(bizTaskId, userId, unionId, dingTodoId, title, content);
            log.info("[DingTalkTodo] 创建待办成功: bizTaskId={}, userId={}, dingTodoId={}",
                    bizTaskId, userId, dingTodoId);
            return true;
        } catch (Exception e) {
            log.error("[DingTalkTodo] 创建待办异常: bizTaskId={}, userId={}", bizTaskId, userId, e);
            saveFailedRecord(bizTaskId, userId, unionId, title, content, "异常: " + e.getMessage());
            return false;
        }
    }

    // ==================================================================
    // 完成待办
    // ==================================================================

    @Override
    public int completeTodoByTask(Long bizTaskId) {
        if (bizTaskId == null) {
            return 0;
        }
        List<PmsDingTalkTodoDO> todos = dingTalkTodoMapper.selectListByBizTaskIdAndStatus(bizTaskId, STATUS_PENDING);
        if (todos == null || todos.isEmpty()) {
            return 0;
        }
        int success = 0;
        for (PmsDingTalkTodoDO todo : todos) {
            if (completeSingleTodo(todo)) {
                success++;
            }
        }
        log.info("[DingTalkTodo] 批量完成待办: bizTaskId={}, total={}, success={}",
                bizTaskId, todos.size(), success);
        return success;
    }

    @Override
    public boolean completeTodoByTaskAndUser(Long bizTaskId, Long userId) {
        if (bizTaskId == null || userId == null) {
            return false;
        }
        PmsDingTalkTodoDO todo = dingTalkTodoMapper.selectByBizTaskIdAndUserId(bizTaskId, userId);
        if (todo == null || !STATUS_PENDING.equals(todo.getStatus()) || StrUtil.isBlank(todo.getDingTodoId())) {
            // 无对应待办或已完结/失败 → 视为完成（不报错）
            return true;
        }
        return completeSingleTodo(todo);
    }

    @Override
    public List<PmsDingTalkTodoDO> getTodoListByTask(Long bizTaskId) {
        if (bizTaskId == null) {
            return java.util.Collections.emptyList();
        }
        return dingTalkTodoMapper.selectListByBizTaskId(bizTaskId);
    }

    // ==================================================================
    // 私有方法
    // ==================================================================

    /**
     * 完成单条待办：调 PUT 接口，更新本地记录状态。
     */
    private boolean completeSingleTodo(PmsDingTalkTodoDO todo) {
        String token = dingTalkApiService.getAccessToken();
        if (StrUtil.isBlank(token)) {
            log.warn("[DingTalkTodo] access_token 为空，跳过完成待办: id={}, bizTaskId={}",
                    todo.getId(), todo.getBizTaskId());
            return false;
        }
        if (StrUtil.isBlank(todo.getDingtalkUnionId()) || StrUtil.isBlank(todo.getDingTodoId())) {
            log.warn("[DingTalkTodo] 待办缺 unionId/todoId，跳过完成: id={}", todo.getId());
            return false;
        }
        String url = properties.getNewApiBaseUrl() + StrUtil.format(COMPLETE_TODO_PATH,
                todo.getDingtalkUnionId(), todo.getDingTodoId());
        try {
            // 官方文档：PUT body 可以为空，仅传 status 字段（0=未完成,1=已完成,2=已忽略）
            JSONObject body = new JSONObject();
            body.set("status", "1");
            HttpResponse response = HttpRequest.put(url)
                    .header("x-acs-dingtalk-access-token", token)
                    .body(body.toString())
                    .timeout(10000)
                    .execute();
            if (!response.isOk()) {
                log.error("[DingTalkTodo] 完成待办 HTTP 异常: id={}, status={}, body={}",
                        todo.getId(), response.getStatus(), response.body());
                return false;
            }
            // 更新本地状态
            PmsDingTalkTodoDO update = new PmsDingTalkTodoDO();
            update.setId(todo.getId());
            update.setStatus(STATUS_COMPLETED);
            update.setTodoCompleteTime(LocalDateTime.now());
            dingTalkTodoMapper.updateById(update);
            return true;
        } catch (Exception e) {
            log.error("[DingTalkTodo] 完成待办异常: id={}, bizTaskId={}", todo.getId(), todo.getBizTaskId(), e);
            return false;
        }
    }

    /**
     * 落"创建成功"记录到本地表。
     */
    private void saveSuccessRecord(Long bizTaskId, Long userId, String unionId,
                                   String dingTodoId, String title, String content) {
        try {
            // 同 (bizTaskId, userId) 既有 failed 记录 → 更新；无 → 新建
            PmsDingTalkTodoDO existing = dingTalkTodoMapper.selectByBizTaskIdAndUserId(bizTaskId, userId);
            PmsDingTalkTodoDO record = existing != null ? existing : new PmsDingTalkTodoDO();
            record.setBizTaskId(bizTaskId);
            record.setUserId(userId);
            record.setDingtalkUnionId(unionId);
            record.setDingTodoId(dingTodoId);
            record.setStatus(STATUS_PENDING);
            record.setTitle(title);
            record.setContent(content);
            record.setTodoCreateTime(LocalDateTime.now());
            record.setFailReason(null);
            if (existing == null) {
                dingTalkTodoMapper.insert(record);
            } else {
                dingTalkTodoMapper.updateById(record);
            }
        } catch (Exception e) {
            log.error("[DingTalkTodo] 落成功记录失败: bizTaskId={}, userId={}", bizTaskId, userId, e);
        }
    }

    /**
     * 落"创建失败"记录到本地表，便于后续排错或补偿。
     */
    private void saveFailedRecord(Long bizTaskId, Long userId, String unionId,
                                  String title, String content, String failReason) {
        try {
            PmsDingTalkTodoDO existing = dingTalkTodoMapper.selectByBizTaskIdAndUserId(bizTaskId, userId);
            PmsDingTalkTodoDO record = existing != null ? existing : new PmsDingTalkTodoDO();
            record.setBizTaskId(bizTaskId);
            record.setUserId(userId);
            record.setDingtalkUnionId(unionId);
            record.setDingTodoId(null);
            record.setStatus(STATUS_FAILED);
            record.setTitle(title);
            record.setContent(content);
            record.setFailReason(failReason);
            if (existing == null) {
                dingTalkTodoMapper.insert(record);
            } else {
                dingTalkTodoMapper.updateById(record);
            }
        } catch (Exception e) {
            log.error("[DingTalkTodo] 落失败记录异常: bizTaskId={}, userId={}", bizTaskId, userId, e);
        }
    }

}

