package cn.iocoder.yudao.module.system.service.auth;

import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthLoginRespVO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.DingTalkLoginReqVO;

/**
 * 钉钉免登 Service 接口
 *
 * 用于钉钉企业内部应用（H5微应用）的免密登录场景：
 * 1. 前端在钉钉容器内通过 JSAPI 获取免登码 authCode
 * 2. 后端使用 authCode 换取钉钉 userId
 * 3. 根据 userId 查找绑定的系统用户，创建 Token 返回
 */
public interface DingTalkAuthService {

    /**
     * 钉钉免登
     *
     * @param reqVO 钉钉免登请求（包含 authCode）
     * @return 登录响应（包含 token）
     */
    AuthLoginRespVO loginByAuthCode(DingTalkLoginReqVO reqVO);

    /**
     * 获取钉钉企业 CorpId（供前端 JSSDK 初始化使用）
     *
     * @return CorpId，未配置则返回空字符串
     */
    String getCorpId();

}
