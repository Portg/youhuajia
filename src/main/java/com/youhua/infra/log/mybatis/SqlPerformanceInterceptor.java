package com.youhua.infra.log.mybatis;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * MyBatis SQL 慢查询拦截器。
 * 超过阈值输出 WARN [SLOW_SQL]。
 */
@Slf4j
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class})
})
public class SqlPerformanceInterceptor implements Interceptor {

    @Value("${youhua.log.slow-sql-threshold-ms:1000}")
    private long slowSqlThresholdMs;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        long start = System.currentTimeMillis();
        try {
            return invocation.proceed();
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed >= slowSqlThresholdMs) {
                String sqlId = ms.getId();
                Object parameter = invocation.getArgs()[1];
                BoundSql boundSql = ms.getBoundSql(parameter);
                String sql = boundSql.getSql().replaceAll("[\\s]+", " ").trim();
                log.warn("[SLOW_SQL] {}ms | id={} | sql={}", elapsed, sqlId, sql);
            }
        }
    }
}
