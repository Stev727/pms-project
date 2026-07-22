package cn.iocoder.yudao.module.pms.dal.dataobject.materialtrack;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 物料跟踪 DO
 */
@TableName("pms_material_track")
@Data
@EqualsAndHashCode(callSuper = true)
public class PmsMaterialTrackDO extends TenantBaseDO {
    /**
     * 跟踪主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long trackId;

    /**
     * 关联项目ID
     */
    private Long projectId;

    /**
     * 关联任务ID
     */
    private Long taskId;

    /**
     * 物料编码
     */
    private String materialCode;

    /**
     * 物料名称
     */
    private String materialName;

    /**
     * 供应商
     */
    private String supplier;

    /**
     * 需求数量
     */
    private BigDecimal quantity;

    /**
     * 单位
     */
    private String unit;

    /**
     * 计划下单日期
     */
    private LocalDate planOrderDate;

    /**
     * 最迟下单日期
     */
    private LocalDate latestOrderDate;

    /**
     * 实际下单日期
     */
    private LocalDate actualOrderDate;

    /**
     * 计划交货日期
     */
    private LocalDate planDeliveryDate;

    /**
     * 实际交货日期
     */
    private LocalDate actualDeliveryDate;

    /**
     * 交货偏差天数
     */
    private Integer deliveryDeviation;

    /**
     * 预警状态
     */
    private String warningStatus;

    /**
     * 责任人ID
     */
    private Long responsibleId;

    /**
     * 当前状态
     */
    private String currentStatus;

}