package com.youhua.common.entity;

import com.baomidou.mybatisplus.annotation.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Base entity with optimistic lock support.
 * Use this for tables requiring concurrency control.
 */
@Getter
@Setter
public abstract class BaseVersionEntity extends BaseEntity {

    @Version
    private Integer version;
}
