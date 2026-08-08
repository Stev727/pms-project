package cn.iocoder.yudao.module.pms.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * PMS 项目级权限点枚举
 *
 * 与 yudao 菜单级权限（pms:xxx:create）正交：
 * - 菜单级权限决定「能不能进这个功能」
 * - 项目级权限点决定「在某个具体项目里能干什么」
 */
@Getter
@AllArgsConstructor
public enum PmsPermKeyEnum {

    // ========== 任务 ==========
    TASK_CREATE("task_create", "任务", "创建任务"),
    TASK_EDIT("task_edit", "任务", "编辑任务"),
    TASK_DELETE("task_delete", "任务", "删除任务"),
    TASK_ASSIGN("task_assign", "任务", "派发任务"),
    TASK_REVIEW("task_review", "任务", "审核任务"),

    // ========== 文档 ==========
    DOCUMENT_VIEW("document_view", "文档", "查看文档列表"),
    DOCUMENT_PREVIEW("document_preview", "文档", "在线预览"),
    DOCUMENT_DOWNLOAD("document_download", "文档", "下载文档"),
    DOCUMENT_UPLOAD("document_upload", "文档", "上传文档"),
    DOCUMENT_DELETE("document_delete", "文档", "删除文档"),
    DOCUMENT_MANAGE_PERM("document_manage_perm", "文档", "配置文档权限"),

    // ========== 物料 ==========
    MATERIAL_VIEW("material_view", "物料", "查看物料"),
    MATERIAL_ADD("material_add", "物料", "新增物料"),
    MATERIAL_EDIT("material_edit", "物料", "编辑物料"),
    MATERIAL_DELETE("material_delete", "物料", "删除物料"),

    // ========== 质量 ==========
    QUALITY_VIEW("quality_view", "质量", "查看质量问题"),
    QUALITY_ADD("quality_add", "质量", "新增质量问题"),
    QUALITY_EDIT("quality_edit", "质量", "编辑质量问题"),
    QUALITY_DELETE("quality_delete", "质量", "删除质量问题"),
    QUALITY_IMPORT("quality_import", "质量", "Excel 批量导入"),

    // ========== 项目管理 ==========
    MEMBER_MANAGE("member_manage", "项目", "管理项目成员"),
    PROJECT_EDIT("project_edit", "项目", "编辑项目信息"),
    PERMISSION_MANAGE("permission_manage", "项目", "配置项目权限"),
    ;

    /**
     * 权限点编码，落库值
     */
    private final String key;

    /**
     * 分组（前端矩阵按组折叠展示）
     */
    private final String group;

    /**
     * 中文标签
     */
    private final String label;

    /**
     * 全部权限点编码
     */
    public static List<String> allKeys() {
        return Arrays.stream(values()).map(PmsPermKeyEnum::getKey).collect(Collectors.toList());
    }

    /**
     * 判断权限点编码是否合法
     */
    public static boolean isValidKey(String key) {
        if (key == null) {
            return false;
        }
        return Arrays.stream(values()).anyMatch(e -> e.getKey().equals(key));
    }

    /**
     * 按分组归集，用于前端矩阵渲染。保持枚举声明顺序。
     */
    public static Map<String, List<PmsPermKeyEnum>> groupedAll() {
        Map<String, List<PmsPermKeyEnum>> result = new LinkedHashMap<>();
        for (PmsPermKeyEnum item : values()) {
            result.computeIfAbsent(item.getGroup(), k -> new java.util.ArrayList<>()).add(item);
        }
        return result;
    }

}

