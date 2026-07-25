package cn.iocoder.yudao.module.system.service.auth;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthLoginRespVO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.DingTalkLoginReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.dal.dataobject.social.SocialClientDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.social.SocialClientMapper;
import cn.iocoder.yudao.module.system.enums.oauth2.OAuth2ClientConstants;
import cn.iocoder.yudao.module.system.enums.social.SocialTypeEnum;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2TokenService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;

/**
 * 钉钉免登 Service 实现类
 *
 * 实现钉钉企业内部应用（H5微应用）的免密登录：
 * 1. 前端在钉钉容器内通过 JSAPI 获取免登码 authCode
 * 2. 后端使用 authCode + access_token 调用钉钉 API 获取 userid
 * 3. 通过 userid 调用钉钉 API 获取用户详情（姓名、手机号等）
 * 4. 使用手机号匹配 system_users 表
 * 5. 匹配成功则创建 Token 返回，实现免密登录
 */
@Service
@Slf4j
public class DingTalkAuthServiceImpl implements DingTalkAuthService {

    /** 钉钉 social_type */
    private static final int SOCIAL_TYPE_DINGTALK = SocialTypeEnum.DINGTALK.getType();
    /** 管理后台 user_type */
    private static final int USER_TYPE_ADMIN = UserTypeEnum.ADMIN.getValue();

    /** 钉钉 API: 获取 access_token */
    private static final String URL_GET_TOKEN = "https://oapi.dingtalk.com/gettoken";
    /** 钉钉 API: 通过免登码获取用户 userid */
    private static final String URL_GET_USERINFO = "https://oapi.dingtalk.com/topapi/v2/user/getuserinfo";
    /** 钉钉 API: 通过 userid 获取用户详情（含手机号） */
    private static final String URL_GET_USER_DETAIL = "https://oapi.dingtalk.com/topapi/v2/user/get";

    @Value("${yudao.dingtalk.corp-id:}")
    private String corpId;

    @Resource
    private SocialClientMapper socialClientMapper;
    @Resource
    private AdminUserService userService;
    @Resource
    private OAuth2TokenService oauth2TokenService;

    /** 缓存的 access_token */
    private String cachedAccessToken;
    /** access_token 过期时间戳（毫秒） */
    private long tokenExpireTime;

    @Override
    public AuthLoginRespVO loginByAuthCode(DingTalkLoginReqVO reqVO) {
        // 1. 获取钉钉应用配置
        SocialClientDO client = socialClientMapper.selectBySocialTypeAndUserType(
                SOCIAL_TYPE_DINGTALK, USER_TYPE_ADMIN);
        if (client == null || StrUtil.isBlank(client.getClientId()) || StrUtil.isBlank(client.getClientSecret())) {
            log.warn("[loginByAuthCode] 钉钉客户端未配置");
            throw exception(AUTH_DINGTALK_NOT_CONFIGURED);
        }

        // 2. 获取 access_token
        String accessToken = getAccessToken(client.getClientId(), client.getClientSecret());

        // 3. 使用 authCode 换取钉钉 userid
        String dingTalkUserId = getDingTalkUserId(accessToken, reqVO.getAuthCode());
        log.info("[loginByAuthCode] 钉钉 userid={}", dingTalkUserId);

        // 4. 通过 userid 获取钉钉用户详情（含手机号）
        JSONObject dingTalkUser = getDingTalkUserDetail(accessToken, dingTalkUserId);
        String mobile = dingTalkUser.getStr("mobile");
        String userName = dingTalkUser.getStr("name");
        log.info("[loginByAuthCode] 钉钉用户: name={}, mobile={}", userName, mobile);

        if (StrUtil.isBlank(mobile)) {
            log.warn("[loginByAuthCode] 钉钉用户未绑定手机号, userid={}", dingTalkUserId);
            throw exception(AUTH_DINGTALK_LOGIN_FAILED, "钉钉账号未绑定手机号");
        }

        // 5. 使用手机号匹配系统用户
        AdminUserDO user = userService.getUserByMobile(mobile);
        if (user == null) {
            log.warn("[loginByAuthCode] 手机号 {} 未在系统中注册", mobile);
            throw exception(AUTH_MOBILE_NOT_EXISTS);
        }

        // 6. 创建 Token 令牌
        OAuth2AccessTokenDO accessTokenDO = oauth2TokenService.createAccessToken(
                user.getId(), USER_TYPE_ADMIN, OAuth2ClientConstants.CLIENT_ID_DEFAULT, null);
        log.info("[loginByAuthCode] 钉钉免登成功, userId={}, userMobile={}", user.getId(), mobile);
        return BeanUtils.toBean(accessTokenDO, AuthLoginRespVO.class);
    }

    @Override
    public String getCorpId() {
        return corpId != null ? corpId : "";
    }

    /**
     * 获取钉钉 access_token（带缓存，有效期 2 小时，提前 5 分钟刷新）
     */
    private synchronized String getAccessToken(String appKey, String appSecret) {
        long now = System.currentTimeMillis();
        // 提前 5 分钟刷新
        if (StrUtil.isNotBlank(cachedAccessToken) && now < tokenExpireTime - 5 * 60 * 1000L) {
            return cachedAccessToken;
        }

        String url = StrUtil.format("{}?appkey={}&appsecret={}", URL_GET_TOKEN, appKey, appSecret);
        String response = HttpUtil.get(url, 10 * 1000);
        log.info("[getAccessToken] 钉钉返回: {}", response);

        JSONObject json = JSONUtil.parseObj(response);
        int errcode = json.getInt("errcode", -1);
        if (errcode != 0) {
            String errmsg = json.getStr("errmsg", "未知错误");
            log.error("[getAccessToken] 获取 access_token 失败: errcode={}, errmsg={}", errcode, errmsg);
            throw exception(AUTH_DINGTALK_LOGIN_FAILED, errmsg);
        }

        cachedAccessToken = json.getStr("access_token");
        int expiresIn = json.getInt("expires_in", 7200);
        tokenExpireTime = now + (long) expiresIn * 1000;
        log.info("[getAccessToken] access_token 获取成功, 有效期={}s", expiresIn);
        return cachedAccessToken;
    }

    /**
     * 使用 authCode 获取钉钉 userid
     */
    private String getDingTalkUserId(String accessToken, String authCode) {
        String url = StrUtil.format("{}?access_token={}", URL_GET_USERINFO, accessToken);
        String body = JSONUtil.createObj().set("code", authCode).toString();
        String response = HttpUtil.post(url, body, 10 * 1000);
        log.info("[getDingTalkUserId] 钉钉返回: {}", response);

        JSONObject json = JSONUtil.parseObj(response);
        int errcode = json.getInt("errcode", -1);
        if (errcode != 0) {
            String errmsg = json.getStr("errmsg", "未知错误");
            log.error("[getDingTalkUserId] 获取用户信息失败: errcode={}, errmsg={}", errcode, errmsg);
            throw exception(AUTH_DINGTALK_LOGIN_FAILED, errmsg);
        }

        JSONObject result = json.getJSONObject("result");
        if (result == null) {
            throw exception(AUTH_DINGTALK_LOGIN_FAILED, "钉钉返回结果为空");
        }
        return result.getStr("userid");
    }

    /**
     * 通过 userid 获取钉钉用户详情（含手机号、姓名等）
     */
    private JSONObject getDingTalkUserDetail(String accessToken, String userId) {
        String url = StrUtil.format("{}?access_token={}", URL_GET_USER_DETAIL, accessToken);
        String body = JSONUtil.createObj().set("userid", userId).toString();
        String response = HttpUtil.post(url, body, 10 * 1000);
        log.info("[getDingTalkUserDetail] 钉钉返回: {}", response);

        JSONObject json = JSONUtil.parseObj(response);
        int errcode = json.getInt("errcode", -1);
        if (errcode != 0) {
            String errmsg = json.getStr("errmsg", "未知错误");
            log.error("[getDingTalkUserDetail] 获取用户详情失败: errcode={}, errmsg={}", errcode, errmsg);
            throw exception(AUTH_DINGTALK_LOGIN_FAILED, errmsg);
        }

        JSONObject result = json.getJSONObject("result");
        if (result == null) {
            throw exception(AUTH_DINGTALK_LOGIN_FAILED, "钉钉用户详情为空");
        }
        return result;
    }

}
