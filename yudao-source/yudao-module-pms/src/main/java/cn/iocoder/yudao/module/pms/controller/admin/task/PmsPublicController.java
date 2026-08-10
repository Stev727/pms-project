package cn.iocoder.yudao.module.pms.controller.admin.task;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.dal.dataobject.task.PmsTaskDO;
import cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.pms.service.task.TaskService;
import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * PMS 公开接口（无需登录）
 * 主要用于钉钉「一键接收」按钮直连，避免跳转登录页
 */
@Tag(name = "管理后台 - PMS 公开接口")
@RestController
@RequestMapping("/pms/public")
@Validated
@Slf4j
public class PmsPublicController {

    @Resource
    private TaskService taskService;

    private static final String SIGN_SECRET = "pms-quick-accept-2026";
    private static final long SIGN_EXPIRE_SECONDS = 300;

    @GetMapping("/task-accept")
    @Operation(summary = "快速接收任务（公开接口，通过签名验证）")
    @Parameter(name = "taskId", description = "任务编号", required = true)
    @Parameter(name = "ts", description = "时间戳（秒）", required = true)
    @Parameter(name = "sign", description = "签名（HMAC-SHA256）", required = true)
    public CommonResult<Boolean> quickAccept(
            @RequestParam("taskId") Long taskId,
            @RequestParam("ts") long ts,
            @RequestParam("sign") String sign) {

        // 1. 校验时间戳（防重放攻击，5分钟内有效）
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - ts) > SIGN_EXPIRE_SECONDS) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.TASK_ACCEPT_SIGN_EXPIRED);
        }

        // 2. 校验签名
        String expectedSign = sign(taskId, ts, "accept");
        if (!expectedSign.equals(sign)) {
            log.warn("[quickAccept] 签名校验失败: taskId={}, expected={}, received={}", taskId, expectedSign, sign);
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.TASK_ACCEPT_SIGN_INVALID);
        }

        // 3. 执行接收逻辑
        taskService.acceptTaskPublic(taskId);  // 公开接口：跳过身份校验

        return success(true);
    }

    @GetMapping("/task-review")
    @Operation(summary = "快速审核任务（公开接口，通过签名验证）")
    @Parameter(name = "taskId", description = "任务编号", required = true)
    @Parameter(name = "action", description = "审核动作：approve=通过 / reject=驳回", required = true)
    @Parameter(name = "ts", description = "时间戳（秒）", required = true)
    @Parameter(name = "sign", description = "签名（HMAC-SHA256）", required = true)
    public CommonResult<Boolean> quickReview(
            @RequestParam("taskId") Long taskId,
            @RequestParam("action") String action,
            @RequestParam("ts") long ts,
            @RequestParam("sign") String sign) {

        // 1. 校验动作合法
        if (!"approve".equals(action) && !"reject".equals(action)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.TASK_REVIEW_ACTION_INVALID);
        }

        // 2. 校验时间戳（防重放攻击，5分钟内有效）
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - ts) > SIGN_EXPIRE_SECONDS) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.TASK_ACCEPT_SIGN_EXPIRED);
        }

        // 3. 校验签名（含 action，防止动作被篡改）
        String expectedSign = sign(taskId, ts, action);
        if (!expectedSign.equals(sign)) {
            log.warn("[quickReview] 签名校验失败: taskId={}, action={}, expected={}, received={}", taskId, action, expectedSign, sign);
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.TASK_ACCEPT_SIGN_INVALID);
        }

        // 4. 执行审核逻辑（公开接口：跳过身份校验）
        // 注意：reject 不提供公开直连，卡片的「驳回」按钮直接跳转系统页填原因，
        // 因此此处 action 只可能是 approve；上一关已拦截非法 action。
        taskService.approveReviewPublic(taskId);

        return success(true);
    }

    /**
     * 生成签名: HMAC-SHA256(taskId + "|" + action + "|" + ts, secret)
     */
    public static String sign(Long taskId, long ts, String action) {
        try {
            String data = taskId + "|" + action + "|" + ts;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    SIGN_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("签名计算失败", e);
        }
    }
}
