package cn.iocoder.yudao.module.pms.service.dingtalk;

import cn.iocoder.yudao.module.pms.dal.dataobject.dingtalk.PmsDingTalkConfigDO;

import java.util.List;
import java.util.Map;

/**
 * 钉钉核心 API 服务
 * 封装 access_token 管理、工作通知发送、组织架构数据获取
 */
public interface DingTalkApiService {

    /**
     * 获取钉钉配置
     */
    PmsDingTalkConfigDO getConfig();

    /**
     * 更新钉钉配置
     */
    void updateConfig(PmsDingTalkConfigDO config);

    /**
     * 获取 access_token（带缓存）
     */
    String getAccessToken();

    /**
     * 发送工作通知
     *
     * @param userIdList   接收人钉钉 userid 列表（逗号分隔）
     * @param title        消息标题
     * @param content      消息内容
     * @return 任务 ID
     */
    String sendWorkNotification(String userIdList, String title, String content);

    /**
     * 发送 Markdown 工作通知
     *
     * @param userIdList   接收人钉钉 userid 列表（逗号分隔）
     * @param title        消息标题
     * @param markdownText Markdown 内容
     * @return 任务 ID
     */
    String sendMarkdownNotification(String userIdList, String title, String markdownText);

    /**
     * 获取子部门列表
     *
     * @param parentDeptId 父部门 ID（根部门传 1）
     * @return 部门列表
     */
    List<Map<String, Object>> getDepartmentList(Long parentDeptId);

    /**
     * 获取部门用户列表
     *
     * @param deptId 部门 ID
     * @return 用户列表
     */
    List<Map<String, Object>> getUserListByDept(Long deptId);

    /**
     * 根据手机号获取钉钉用户 userid
     *
     * @param mobile 手机号
     * @return 钉钉 userid
     */
    String getUserIdByMobile(String mobile);

    /**
     * 测试钉钉连接
     *
     * @return 测试结果
     */
    String testConnection();
}
