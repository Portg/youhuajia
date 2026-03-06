package com.youhua.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Unified error codes following google.rpc.Status pattern.
 * Format: XYYZZZ — X=category(4=business,5=system), YY=module, ZZZ=sequence.
 */
@Getter
public enum ErrorCode {

    SUCCESS(200, HttpStatus.OK, "OK", "success"),

    // ===== 认证模块 (401xxx) =====
    SMS_RATE_LIMIT(401001, HttpStatus.TOO_MANY_REQUESTS, "RESOURCE_EXHAUSTED", "验证码发送频率过高，请稍后重试"),
    SMS_CODE_INVALID(401002, HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "验证码错误或已过期"),
    PHONE_FORMAT_INVALID(401003, HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "手机号格式不正确"),
    TOKEN_EXPIRED(401004, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Token 已过期，请重新登录"),
    TOKEN_INVALID(401005, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Token 无效"),
    ACCOUNT_FROZEN(401006, HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "账号已被冻结，请联系客服"),
    DEVICE_ABNORMAL(401007, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "设备异常，请重新验证"),
    ACCOUNT_CANCELLED(401008, HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "账号已注销"),

    // ===== 债务模块 (402xxx) =====
    DEBT_NOT_FOUND(402001, HttpStatus.NOT_FOUND, "NOT_FOUND", "债务记录不存在"),
    DEBT_PRINCIPAL_INVALID(402002, HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "本金必须大于零"),
    DEBT_REPAYMENT_INVALID(402003, HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "总还款额不能小于本金"),
    DEBT_LOAN_DAYS_INVALID(402004, HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "借款天数必须大于零"),
    DEBT_CREDITOR_EMPTY(402005, HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "债权机构名称不能为空"),
    DEBT_STATE_INVALID(402006, HttpStatus.BAD_REQUEST, "FAILED_PRECONDITION", "当前状态不允许此操作"),
    DEBT_VERSION_CONFLICT(402007, HttpStatus.CONFLICT, "ABORTED", "数据已被其他操作修改，请刷新后重试"),
    DEBT_TYPE_INVALID(402008, HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "债务类型不合法"),
    DEBT_COUNT_EXCEEDED(402009, HttpStatus.TOO_MANY_REQUESTS, "RESOURCE_EXHAUSTED", "债务数量已达上限"),
    DEBT_CONFIRM_MISSING_FIELDS(402010, HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "必填字段缺失，无法确认"),
    DEBT_IN_PROFILE(402011, HttpStatus.BAD_REQUEST, "FAILED_PRECONDITION", "该债务已纳入画像，修改前请先移出"),
    DUPLICATE_REQUEST(402012, HttpStatus.CONFLICT, "ALREADY_EXISTS", "重复请求，请勿重复提交"),

    // ===== 画像模块 (403xxx) =====
    PROFILE_NO_CONFIRMED_DEBT(403001, HttpStatus.BAD_REQUEST, "FAILED_PRECONDITION", "暂无已确认的债务数据，无法生成画像"),
    PROFILE_NO_INCOME(403002, HttpStatus.BAD_REQUEST, "FAILED_PRECONDITION", "收入信息未填写，无法计算负债收入比"),
    PROFILE_CALCULATING(403003, HttpStatus.CONFLICT, "ABORTED", "画像正在计算中，请稍后"),
    PROFILE_DATA_ABNORMAL(403004, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "画像数据异常，请重新触发计算"),
    INCOME_AMOUNT_INVALID(403005, HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "收入金额必须大于等于零"),

    // ===== 引擎模块 (404xxx) =====
    ENGINE_APR_PARAMS_INCOMPLETE(404001, HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "APR 计算参数不完整"),
    ENGINE_APR_RESULT_ABNORMAL(404002, HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "计算结果异常（APR 超过合理范围）"),
    ENGINE_SCORE_FAILED(404003, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "评分模型计算失败"),
    ENGINE_RULE_FAILED(404004, HttpStatus.BAD_REQUEST, "FAILED_PRECONDITION", "规则引擎校验不通过"),
    STRATEGY_NOT_FOUND(404006, HttpStatus.NOT_FOUND, "NOT_FOUND", "评分策略不存在"),
    STRATEGY_LOAD_FAILED(404007, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "评分策略加载失败"),
    SCORE_SIMULATE_FAILED(404008, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "评分模拟计算失败"),

    // ===== AI 模块 (405xxx) =====
    OCR_FILE_FORMAT_INVALID(405001, HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "文件格式不支持，请上传 JPG/PNG/PDF"),
    OCR_FILE_SIZE_EXCEEDED(405002, HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "文件大小超出限制（最大10MB）"),
    OCR_FAILED(405003, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "OCR 识别失败，请重试或手动录入"),
    OCR_TASK_NOT_FOUND(405004, HttpStatus.NOT_FOUND, "NOT_FOUND", "OCR 任务不存在"),
    OCR_TASK_PROCESSING(405005, HttpStatus.CONFLICT, "ABORTED", "OCR 任务正在处理中"),
    OCR_RETRY_EXCEEDED(405006, HttpStatus.TOO_MANY_REQUESTS, "RESOURCE_EXHAUSTED", "OCR 重试次数已达上限"),
    AI_SUGGESTION_FAILED(405007, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "AI 建议生成失败，报告将不含 AI 建议"),
    OCR_LOW_CONFIDENCE(405008, HttpStatus.BAD_REQUEST, "FAILED_PRECONDITION", "OCR 结果置信度过低，建议手动核对"),

    // ===== 限流 (429xxx) =====
    RATE_LIMITED(429001, HttpStatus.TOO_MANY_REQUESTS, "RESOURCE_EXHAUSTED", "操作过于频繁，请稍后重试"),

    // ===== 报告模块 (406xxx) =====
    REPORT_NOT_FOUND(406001, HttpStatus.NOT_FOUND, "NOT_FOUND", "报告不存在"),
    REPORT_PROFILE_INCOMPLETE(406002, HttpStatus.BAD_REQUEST, "FAILED_PRECONDITION", "画像数据不完整，无法生成报告"),
    REPORT_GENERATING(406003, HttpStatus.CONFLICT, "ABORTED", "报告正在生成中，请稍后"),
    REPORT_PDF_FAILED(406004, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "PDF 导出失败"),

    // ===== 系统异常 (5xxxxx) =====
    SYSTEM_BUSY(500001, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "系统繁忙，请稍后重试"),
    DB_ERROR(500002, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "数据库操作异常"),
    CACHE_ERROR(500003, HttpStatus.SERVICE_UNAVAILABLE, "UNAVAILABLE", "缓存服务异常"),
    STORAGE_ERROR(500004, HttpStatus.SERVICE_UNAVAILABLE, "UNAVAILABLE", "文件存储服务异常"),
    AI_TIMEOUT(500005, HttpStatus.SERVICE_UNAVAILABLE, "UNAVAILABLE", "AI 服务调用超时"),
    AI_UNAVAILABLE(500006, HttpStatus.SERVICE_UNAVAILABLE, "UNAVAILABLE", "AI 服务不可用"),
    VALIDATION_FAILED(500007, HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "参数校验失败");

    private final int code;
    private final HttpStatus httpStatus;
    private final String status;
    private final String defaultMessage;

    ErrorCode(int code, HttpStatus httpStatus, String status, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.status = status;
        this.defaultMessage = defaultMessage;
    }
}
