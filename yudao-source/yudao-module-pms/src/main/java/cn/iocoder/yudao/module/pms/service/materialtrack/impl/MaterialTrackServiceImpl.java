package cn.iocoder.yudao.module.pms.service.materialtrack.impl;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.controller.admin.materialtrack.vo.MaterialTrackImportErrorExcel;
import cn.iocoder.yudao.module.pms.controller.admin.materialtrack.vo.MaterialTrackImportExcel;
import cn.iocoder.yudao.module.pms.controller.admin.materialtrack.vo.MaterialTrackImportRespVO;
import cn.iocoder.yudao.module.pms.dal.dataobject.materialtrack.PmsMaterialTrackDO;
import cn.iocoder.yudao.module.pms.dal.mysql.materialtrack.MaterialTrackMapper;
import cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.pms.dal.dataobject.materialtrack.PmsMaterialTrackDO;
import cn.iocoder.yudao.module.pms.dal.mysql.materialtrack.MaterialTrackMapper;
import cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.pms.service.materialtrack.MaterialTrackService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 物料跟踪 Service 实现
 *
 * 改造说明（#10 物料跟踪嵌入项目详情）：
 *  - getMaterialTrackList 增加 projectId 参数；非空走 selectListByProjectId，空走 selectList(null)
 *  - deleteMaterialTrack 增加存在性校验，缺失抛 MATERIAL_NOT_EXISTS
 */
@Service
public class MaterialTrackServiceImpl implements MaterialTrackService {

    @Resource
    private MaterialTrackMapper materialTrackMapper;

    @Override
    public Long createMaterialTrack(PmsMaterialTrackDO entity) {
        materialTrackMapper.insert(entity);
        return entity.getTrackId();
    }

    @Override
    public void updateMaterialTrack(PmsMaterialTrackDO entity) {
        validateMaterialTrackExists(entity.getTrackId());
        materialTrackMapper.updateById(entity);
    }

    @Override
    public void deleteMaterialTrack(Long id) {
        validateMaterialTrackExists(id);
        materialTrackMapper.deleteById(id);
    }

    @Override
    public PmsMaterialTrackDO getMaterialTrack(Long id) {
        return materialTrackMapper.selectById(id);
    }

    @Override
    public List<PmsMaterialTrackDO> getMaterialTrackList(Long projectId) {
        if (projectId == null) {
            return materialTrackMapper.selectList(null);
        }
        return materialTrackMapper.selectListByProjectId(projectId);
    }

    private void validateMaterialTrackExists(Long id) {
        if (id == null || materialTrackMapper.selectById(id) == null) {
            throw new ServiceException(ErrorCodeConstants.MATERIAL_NOT_EXISTS);
        }
    }



    // ==================== Excel 批量导入 ====================

    private static final Set<String> VALID_STATUSES = new HashSet<>(java.util.Arrays.asList(
            "not_ordered", "ordered", "delivered", "delayed"
    ));

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public MaterialTrackImportRespVO importMaterialTrackList(Long projectId, List<MaterialTrackImportExcel> rows) {
        // 0. 空文件 / 无数据行
        if (rows == null || rows.isEmpty()) {
            throw new ServiceException(ErrorCodeConstants.MATERIAL_IMPORT_EMPTY);
        }

        List<MaterialTrackImportErrorExcel> failureRows = new ArrayList<>();
        int rowIndex = 0;

        for (MaterialTrackImportExcel row : rows) {
            rowIndex++;
            List<String> errors = new ArrayList<>();
            validateRequired(row, errors);
            validateStatus(row, errors);
            validateDates(row, errors);

            if (!errors.isEmpty()) {
                failureRows.add(buildErrorExcel(row, String.join("；", errors)));
            }
        }

        // 2. 存在错误行 → 整批不落库，返回错误行供 Controller 生成错误 Excel 下载
        if (!failureRows.isEmpty()) {
            return MaterialTrackImportRespVO.builder()
                    .success(false)
                    .successCount(0)
                    .failureRows(failureRows)
                    .build();
        }

        // 3. 全部校验通过 → 批量插入
        int inserted = 0;
        for (MaterialTrackImportExcel row : rows) {
            PmsMaterialTrackDO entity = new PmsMaterialTrackDO();
            entity.setProjectId(projectId);
            entity.setMaterialName(row.getMaterialName().trim());
            entity.setMaterialCode(row.getMaterialCode() != null ? row.getMaterialCode().trim() : null);
            entity.setSupplier(row.getSupplier() != null ? row.getSupplier().trim() : null);
            entity.setQuantity(row.getQuantity());
            entity.setUnit(row.getUnit() != null ? row.getUnit().trim() : null);
            entity.setPlanOrderDate(row.getPlanOrderDate());
            entity.setPlanDeliveryDate(row.getPlanDeliveryDate());
            entity.setCurrentStatus(row.getCurrentStatus() != null ? row.getCurrentStatus() : "not_ordered");
            entity.setWarningStatus("normal");
            materialTrackMapper.insert(entity);
            inserted++;
        }

        return MaterialTrackImportRespVO.builder()
                .success(true)
                .successCount(inserted)
                .failureRows(new ArrayList<>())
                .build();
    }

    /** 必填项校验：物料名称不能为空 */
    private void validateRequired(MaterialTrackImportExcel row, List<String> errors) {
        if (row.getMaterialName() == null || row.getMaterialName().trim().isEmpty()) {
            errors.add("物料名称不能为空");
        }
    }

    /** 当前状态校验：必须在允许值范围内 */
    private void validateStatus(MaterialTrackImportExcel row, List<String> errors) {
        String status = row.getCurrentStatus();
        if (status != null && !VALID_STATUSES.contains(status.trim())) {
            errors.add("当前状态\"" + status + "\"无效，可选值：not_ordered / ordered / delivered / delayed");
        }
    }

    /** 日期合理性校验：承诺交期不能早于计划下单日期 */
    private void validateDates(MaterialTrackImportExcel row, List<String> errors) {
        java.time.LocalDate orderDate = row.getPlanOrderDate();
        java.time.LocalDate deliveryDate = row.getPlanDeliveryDate();
        if (orderDate != null && deliveryDate != null && deliveryDate.isBefore(orderDate)) {
            errors.add("承诺交期（" + deliveryDate + "）不能早于计划下单日期（" + orderDate + "）");
        }
    }

    /** 构造错误 Excel 行 */
    private MaterialTrackImportErrorExcel buildErrorExcel(MaterialTrackImportExcel row, String errorMsg) {
        return MaterialTrackImportErrorExcel.builder()
                .errorMessage(errorMsg)
                .materialName(row.getMaterialName())
                .materialCode(row.getMaterialCode())
                .supplier(row.getSupplier())
                .quantity(row.getQuantity())
                .unit(row.getUnit())
                .planOrderDate(row.getPlanOrderDate())
                .planDeliveryDate(row.getPlanDeliveryDate())
                .currentStatus(row.getCurrentStatus())
                .build();
    }

}
