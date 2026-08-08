package cn.iocoder.yudao.module.pms.controller.admin.message;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.dal.dataobject.message.PmsMessageDO;
import cn.iocoder.yudao.module.pms.service.message.PmsMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * PMS 站内消息 Controller（#4 站内消息中心）
 *
 * <p>提供前端铃铛组件调用的接口：
 * <ul>
 *   <li>{@code GET  /pms/message/unread-count}    → 未读数（轮询）</li>
 *   <li>{@code GET  /pms/message/unread-list}    → 未读列表（铃铛下拉用）</li>
 *   <li>{@code GET  /pms/message/page}           → 分页查询（消息中心页用）</li>
 *   <li>{@code POST /pms/message/mark-read}      → 批量已读</li>
 *   <li>{@code POST /pms/message/mark-all-read}  → 全部已读</li>
 * </ul>
 *
 * <p>权限：所有登录用户均可访问自己的消息，无 @PreAuthorize 限制（除菜单级）。
 */
@Tag(name = "管理后台 - PMS 站内消息")
@RestController
@RequestMapping("/pms/message")
@Validated
public class MessageController {

    @Resource
    private PmsMessageService pmsMessageService;

    @GetMapping("/unread-count")
    @Operation(summary = "获取当前用户未读消息数（铃铛红点用）")
    public CommonResult<Map<String, Long>> getUnreadCount() {
        long count = pmsMessageService.countUnread();
        return success(java.util.Collections.singletonMap("count", count));
    }

    @GetMapping("/unread-list")
    @Operation(summary = "获取当前用户未读消息列表（铃铛下拉用）")
    public CommonResult<List<PmsMessageDO>> getUnreadList() {
        return success(pmsMessageService.listMyUnread());
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询当前用户消息")
    @Parameter(name = "readStatus", description = "阅读状态：0 未读 / 1 已读 / 不传为全部")
    public CommonResult<PageResult<PmsMessageDO>> getPage(
            @RequestParam(value = "readStatus", required = false) Integer readStatus,
            PageParam pageParam) {
        return success(pmsMessageService.listMyMessages(readStatus, pageParam));
    }

    @PostMapping("/mark-read")
    @Operation(summary = "批量标记消息为已读")
    @Parameter(name = "messageIds", description = "消息ID列表", required = true)
    public CommonResult<Integer> markRead(@RequestBody List<Long> messageIds) {
        return success(pmsMessageService.markRead(messageIds));
    }

    @PostMapping("/mark-all-read")
    @Operation(summary = "全部标记已读")
    public CommonResult<Integer> markAllRead() {
        return success(pmsMessageService.markAllRead());
    }

}

