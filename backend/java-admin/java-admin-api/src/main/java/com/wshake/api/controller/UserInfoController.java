package com.wshake.api.controller;

import com.wshake.api.vo.UserInfoVO;
import com.wshake.common.exception.BizException;
import com.wshake.common.request.RequestContext;
import com.wshake.common.result.Result;
import com.wshake.common.result.ResultCode;
import com.wshake.service.auth.AuthService;
import com.wshake.service.auth.LoginResult;
import com.wshake.service.entity.SysUser;
import com.wshake.service.user.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户信息别名路径：与前端既有 {@code /api/user/info} 对齐。
 *
 * <p>语义同 {@code /api/auth/info}。登录校验由 {@code WebConfig} SaInterceptor 统一完成。
 *
 * @author wshake
 */
@Tag(name = "用户信息", description = "当前用户信息（路径别名）")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserInfoController {

    private final AuthService authService;
    private final SysUserService sysUserService;

    /**
     * 当前登录用户信息（别名路径，与 {@code /api/auth/info} 等价）。
     */
    @GetMapping("/info")
    @Operation(summary = "当前登录用户信息", description = "别名路径，与 /api/auth/info 等价")
    @SecurityRequirement(name = "bearerAuth")
    public Result<UserInfoVO> info() {
        Long userId = RequestContext.requireUserId();
        SysUser user = sysUserService.findById(userId);
        if (user == null) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "用户不存在");
        }
        LoginResult summary = authService.toUserSummary(user);
        return Result.ok(new UserInfoVO(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                summary.roles(),
                summary.homePath(),
                user.getAvatar()));
    }
}
