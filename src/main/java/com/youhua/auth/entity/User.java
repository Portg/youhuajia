package com.youhua.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.youhua.auth.enums.UserStatus;
import com.youhua.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("t_user")
public class User extends BaseEntity {

    private String phone;

    private String phoneHash;

    private String nickname;

    private String avatarUrl;

    private UserStatus status;

    private LocalDateTime cancellationTime;

    private LocalDateTime lastLoginTime;

    private String lastLoginIp;

    private String deviceFingerprint;

    // ---- V2.0 预留 ----
    private String digitalWalletId;

    // ---- V2.1 预留 ----
    private Long familyId;

    private String memberLevel;

    private Integer growthValue;
}
