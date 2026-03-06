package com.youhua.common.util;

import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Utility for accessing the current HTTP request context.
 */
public final class RequestContextUtil {

    private RequestContextUtil() {
    }

    /**
     * Returns the authenticated userId from the current request attributes.
     * The userId attribute is set by the auth filter.
     *
     * @throws BizException TOKEN_INVALID if no request context, user not logged in, or invalid identity
     */
    public static Long getCurrentUserId() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new BizException(ErrorCode.TOKEN_INVALID, "无法获取请求上下文");
        }
        HttpServletRequest request = attrs.getRequest();
        Object userIdAttr = request.getAttribute("userId");
        if (userIdAttr == null) {
            throw new BizException(ErrorCode.TOKEN_INVALID, "用户未登录");
        }
        if (userIdAttr instanceof Long l) {
            return l;
        }
        try {
            return Long.parseLong(userIdAttr.toString());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.TOKEN_INVALID, "无效的用户身份");
        }
    }
}
