package cn.iocoder.yudao.module.pms.dal.mysql.document;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.dal.dataobject.document.PmsDocumentDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DocumentMapper extends BaseMapperX<PmsDocumentDO> {

    /**
     * 【#7 新增】按项目ID查询文档列表（按上传时间倒序）
     */
    default List<PmsDocumentDO> selectListByProjectId(Long projectId) {
        return selectList(new LambdaQueryWrapperX<PmsDocumentDO>()
                .eq(PmsDocumentDO::getProjectId, projectId)
                .orderByDesc(PmsDocumentDO::getUploadTime));
    }

    /**
     * 【#7 新增】按项目ID + 上传人查询（用于 private 文档过滤）
     */
    default List<PmsDocumentDO> selectListByProjectIdAndUploadBy(Long projectId, Long uploadBy) {
        return selectList(new LambdaQueryWrapperX<PmsDocumentDO>()
                .eq(PmsDocumentDO::getProjectId, projectId)
                .eq(PmsDocumentDO::getUploadBy, uploadBy)
                .orderByDesc(PmsDocumentDO::getUploadTime));
    }

}

