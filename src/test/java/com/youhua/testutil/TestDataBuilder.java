package com.youhua.testutil;

import com.youhua.auth.entity.User;
import com.youhua.auth.enums.UserStatus;
import com.youhua.debt.entity.Debt;
import com.youhua.debt.enums.DebtSourceType;
import com.youhua.debt.enums.DebtStatus;
import com.youhua.debt.enums.DebtType;
import com.youhua.debt.enums.OverdueStatus;
import com.youhua.profile.entity.FinanceProfile;
import com.youhua.profile.entity.IncomeRecord;
import com.youhua.profile.enums.IncomeType;
import com.youhua.profile.enums.ProfileGenerationStatus;
import com.youhua.profile.enums.RiskLevel;
import com.youhua.profile.enums.VerificationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Test data factory providing builder-pattern constructors for entity objects.
 *
 * <p>Usage:
 * <pre>
 *   User user = TestDataBuilder.aUser().withNickname("测试").build();
 *   Debt debt = TestDataBuilder.aDebt(userId).withPrincipal(new BigDecimal("50000")).build();
 * </pre>
 *
 * <p>Seed data IDs from V3__seed_test_data.sql:
 * <ul>
 *   <li>User A (healthy):   id=100001, phone=13800000001</li>
 *   <li>User B (medium):    id=100002, phone=13800000002</li>
 *   <li>User C (critical):  id=100003, phone=13800000003</li>
 *   <li>User D (zero-rate): id=100004, phone=13800000004</li>
 *   <li>User E (draft):     id=100005, phone=13800000005</li>
 * </ul>
 */
public final class TestDataBuilder {

    // ---- Seed IDs (align with V3__seed_test_data.sql) ----

    public static final long USER_A_ID = 100001L;
    public static final long USER_B_ID = 100002L;
    public static final long USER_C_ID = 100003L;
    public static final long USER_D_ID = 100004L;
    public static final long USER_E_ID = 100005L;

    public static final long DEBT_A1_ID = 300001L;
    public static final long DEBT_A2_ID = 300002L;
    public static final long DEBT_A3_ID = 300003L;
    public static final long DEBT_B1_ID = 300004L;
    public static final long DEBT_B2_ID = 300005L;
    public static final long DEBT_B3_ID = 300006L;
    public static final long DEBT_C1_ID = 300007L;
    public static final long DEBT_C2_ID = 300008L;
    public static final long DEBT_C3_ID = 300009L;
    public static final long DEBT_C4_ID = 300010L;
    public static final long DEBT_C5_ID = 300011L;
    public static final long DEBT_C6_ID = 300012L;
    public static final long DEBT_C7_ID = 300013L;
    public static final long DEBT_D1_ID = 300014L;
    public static final long DEBT_E1_ID = 300015L;

    public static final long PROFILE_A_ID = 400001L;
    public static final long PROFILE_B_ID = 400002L;
    public static final long PROFILE_C_ID = 400003L;
    public static final long PROFILE_D_ID = 400004L;

    private TestDataBuilder() {}

    // ============================================================
    // User builder
    // ============================================================

    public static UserBuilder aUser() {
        return new UserBuilder();
    }

    public static class UserBuilder {
        private Long id = null;
        private String phone = "ENCRYPTED_13800000001";
        private String phoneHash = "test_phone_hash";
        private String nickname = "测试用户";
        private UserStatus status = UserStatus.ACTIVE;
        private LocalDateTime createTime = LocalDateTime.of(2026, 1, 1, 10, 0, 0);
        private LocalDateTime updateTime = LocalDateTime.of(2026, 1, 1, 10, 0, 0);

        public UserBuilder withId(Long id) {
            this.id = id;
            return this;
        }

        public UserBuilder withPhone(String phone) {
            this.phone = phone;
            return this;
        }

        public UserBuilder withPhoneHash(String phoneHash) {
            this.phoneHash = phoneHash;
            return this;
        }

        public UserBuilder withNickname(String nickname) {
            this.nickname = nickname;
            return this;
        }

        public UserBuilder withStatus(UserStatus status) {
            this.status = status;
            return this;
        }

        public UserBuilder withCreateTime(LocalDateTime createTime) {
            this.createTime = createTime;
            return this;
        }

        public User build() {
            User user = new User();
            user.setId(id);
            user.setPhone(phone);
            user.setPhoneHash(phoneHash);
            user.setNickname(nickname);
            user.setStatus(status);
            user.setCreateTime(createTime);
            user.setUpdateTime(updateTime);
            user.setDeleted(0);
            user.setGrowthValue(0);
            return user;
        }
    }

    // ============================================================
    // Debt builder
    // ============================================================

    public static DebtBuilder aDebt(Long userId) {
        return new DebtBuilder(userId);
    }

    public static class DebtBuilder {
        private Long id = null;
        private final Long userId;
        private String creditor = "测试债权机构";
        private DebtType debtType = DebtType.CONSUMER_LOAN;
        private BigDecimal principal = new BigDecimal("10000.0000");
        private BigDecimal totalRepayment = new BigDecimal("12000.0000");
        private BigDecimal nominalRate = null;
        private BigDecimal apr = null;
        private int loanDays = 365;
        private BigDecimal monthlyPayment = null;
        private BigDecimal remainingPrincipal = null;
        private Integer remainingPeriods = null;
        private OverdueStatus overdueStatus = OverdueStatus.NORMAL;
        private int overdueDays = 0;
        private DebtSourceType sourceType = DebtSourceType.MANUAL;
        private BigDecimal confidenceScore = null;
        private DebtStatus status = DebtStatus.IN_PROFILE;
        private String remark = null;
        private LocalDateTime createTime = LocalDateTime.of(2026, 1, 1, 10, 0, 0);
        private LocalDateTime updateTime = LocalDateTime.of(2026, 1, 1, 10, 0, 0);

        private DebtBuilder(Long userId) {
            this.userId = userId;
        }

        public DebtBuilder withId(Long id) {
            this.id = id;
            return this;
        }

        public DebtBuilder withCreditor(String creditor) {
            this.creditor = creditor;
            return this;
        }

        public DebtBuilder withDebtType(DebtType debtType) {
            this.debtType = debtType;
            return this;
        }

        public DebtBuilder withPrincipal(BigDecimal principal) {
            this.principal = principal;
            return this;
        }

        public DebtBuilder withTotalRepayment(BigDecimal totalRepayment) {
            this.totalRepayment = totalRepayment;
            return this;
        }

        public DebtBuilder withNominalRate(BigDecimal nominalRate) {
            this.nominalRate = nominalRate;
            return this;
        }

        public DebtBuilder withApr(BigDecimal apr) {
            this.apr = apr;
            return this;
        }

        public DebtBuilder withLoanDays(int loanDays) {
            this.loanDays = loanDays;
            return this;
        }

        public DebtBuilder withMonthlyPayment(BigDecimal monthlyPayment) {
            this.monthlyPayment = monthlyPayment;
            return this;
        }

        public DebtBuilder withRemainingPrincipal(BigDecimal remainingPrincipal) {
            this.remainingPrincipal = remainingPrincipal;
            return this;
        }

        public DebtBuilder withRemainingPeriods(Integer remainingPeriods) {
            this.remainingPeriods = remainingPeriods;
            return this;
        }

        public DebtBuilder withOverdueStatus(OverdueStatus overdueStatus) {
            this.overdueStatus = overdueStatus;
            return this;
        }

        public DebtBuilder withOverdueDays(int overdueDays) {
            this.overdueDays = overdueDays;
            return this;
        }

        public DebtBuilder withSourceType(DebtSourceType sourceType) {
            this.sourceType = sourceType;
            return this;
        }

        public DebtBuilder withConfidenceScore(BigDecimal confidenceScore) {
            this.confidenceScore = confidenceScore;
            return this;
        }

        public DebtBuilder withStatus(DebtStatus status) {
            this.status = status;
            return this;
        }

        public DebtBuilder withRemark(String remark) {
            this.remark = remark;
            return this;
        }

        public DebtBuilder withCreateTime(LocalDateTime createTime) {
            this.createTime = createTime;
            return this;
        }

        public Debt build() {
            Debt debt = new Debt();
            debt.setId(id);
            debt.setUserId(userId);
            debt.setCreditor(creditor);
            debt.setDebtType(debtType);
            debt.setPrincipal(principal);
            debt.setTotalRepayment(totalRepayment);
            debt.setNominalRate(nominalRate);
            debt.setApr(apr);
            debt.setLoanDays(loanDays);
            debt.setMonthlyPayment(monthlyPayment);
            debt.setRemainingPrincipal(remainingPrincipal);
            debt.setRemainingPeriods(remainingPeriods);
            debt.setOverdueStatus(overdueStatus);
            debt.setOverdueDays(overdueDays);
            debt.setSourceType(sourceType);
            debt.setConfidenceScore(confidenceScore);
            debt.setStatus(status);
            debt.setRemark(remark);
            debt.setCreateTime(createTime);
            debt.setUpdateTime(updateTime);
            debt.setDeleted(0);
            debt.setVersion(0);
            return debt;
        }
    }

    // ============================================================
    // IncomeRecord builder
    // ============================================================

    public static IncomeBuilder anIncome(Long userId) {
        return new IncomeBuilder(userId);
    }

    public static class IncomeBuilder {
        private Long id = null;
        private final Long userId;
        private IncomeType incomeType = IncomeType.SALARY;
        private BigDecimal amount = new BigDecimal("10000.0000");
        private boolean primary = true;
        private VerificationStatus verificationStatus = VerificationStatus.UNVERIFIED;
        private LocalDateTime createTime = LocalDateTime.of(2026, 1, 1, 10, 0, 0);
        private LocalDateTime updateTime = LocalDateTime.of(2026, 1, 1, 10, 0, 0);

        private IncomeBuilder(Long userId) {
            this.userId = userId;
        }

        public IncomeBuilder withId(Long id) {
            this.id = id;
            return this;
        }

        public IncomeBuilder withIncomeType(IncomeType incomeType) {
            this.incomeType = incomeType;
            return this;
        }

        public IncomeBuilder withAmount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public IncomeBuilder withPrimary(boolean primary) {
            this.primary = primary;
            return this;
        }

        public IncomeBuilder withVerificationStatus(VerificationStatus verificationStatus) {
            this.verificationStatus = verificationStatus;
            return this;
        }

        public IncomeRecord build() {
            IncomeRecord record = new IncomeRecord();
            record.setId(id);
            record.setUserId(userId);
            record.setIncomeType(incomeType);
            record.setAmount(amount);
            record.setPrimary(primary);
            record.setVerificationStatus(verificationStatus);
            record.setCreateTime(createTime);
            record.setUpdateTime(updateTime);
            record.setDeleted(0);
            return record;
        }
    }

    // ============================================================
    // FinanceProfile builder
    // ============================================================

    public static FinanceProfileBuilder aFinanceProfile(Long userId) {
        return new FinanceProfileBuilder(userId);
    }

    public static class FinanceProfileBuilder {
        private Long id = null;
        private final Long userId;
        private BigDecimal totalDebt = new BigDecimal("50000.0000");
        private int debtCount = 3;
        private BigDecimal weightedApr = new BigDecimal("8.440000");
        private BigDecimal monthlyPayment = new BigDecimal("3700.0000");
        private BigDecimal monthlyIncome = new BigDecimal("15000.0000");
        private BigDecimal debtIncomeRatio = new BigDecimal("0.246700");
        private BigDecimal liquidityScore = new BigDecimal("75.00");
        private BigDecimal restructureScore = new BigDecimal("82.00");
        private RiskLevel riskLevel = RiskLevel.LOW;
        private int overdueCount = 0;
        private Long highestAprDebtId = null;
        private String scoreDetailJson = null;
        private LocalDateTime lastCalculatedTime = LocalDateTime.of(2026, 1, 1, 10, 30, 0);
        private ProfileGenerationStatus generationStatus = ProfileGenerationStatus.COMPLETED;
        private int generationRetryCount = 0;
        private LocalDateTime createTime = LocalDateTime.of(2026, 1, 1, 10, 0, 0);
        private LocalDateTime updateTime = LocalDateTime.of(2026, 1, 1, 10, 0, 0);

        private FinanceProfileBuilder(Long userId) {
            this.userId = userId;
        }

        public FinanceProfileBuilder withId(Long id) {
            this.id = id;
            return this;
        }

        public FinanceProfileBuilder withTotalDebt(BigDecimal totalDebt) {
            this.totalDebt = totalDebt;
            return this;
        }

        public FinanceProfileBuilder withDebtCount(int debtCount) {
            this.debtCount = debtCount;
            return this;
        }

        public FinanceProfileBuilder withWeightedApr(BigDecimal weightedApr) {
            this.weightedApr = weightedApr;
            return this;
        }

        public FinanceProfileBuilder withMonthlyPayment(BigDecimal monthlyPayment) {
            this.monthlyPayment = monthlyPayment;
            return this;
        }

        public FinanceProfileBuilder withMonthlyIncome(BigDecimal monthlyIncome) {
            this.monthlyIncome = monthlyIncome;
            return this;
        }

        public FinanceProfileBuilder withDebtIncomeRatio(BigDecimal debtIncomeRatio) {
            this.debtIncomeRatio = debtIncomeRatio;
            return this;
        }

        public FinanceProfileBuilder withLiquidityScore(BigDecimal liquidityScore) {
            this.liquidityScore = liquidityScore;
            return this;
        }

        public FinanceProfileBuilder withRestructureScore(BigDecimal restructureScore) {
            this.restructureScore = restructureScore;
            return this;
        }

        public FinanceProfileBuilder withRiskLevel(RiskLevel riskLevel) {
            this.riskLevel = riskLevel;
            return this;
        }

        public FinanceProfileBuilder withOverdueCount(int overdueCount) {
            this.overdueCount = overdueCount;
            return this;
        }

        public FinanceProfileBuilder withHighestAprDebtId(Long highestAprDebtId) {
            this.highestAprDebtId = highestAprDebtId;
            return this;
        }

        public FinanceProfileBuilder withScoreDetailJson(String scoreDetailJson) {
            this.scoreDetailJson = scoreDetailJson;
            return this;
        }

        public FinanceProfileBuilder withGenerationStatus(ProfileGenerationStatus generationStatus) {
            this.generationStatus = generationStatus;
            return this;
        }

        public FinanceProfileBuilder withLastCalculatedTime(LocalDateTime lastCalculatedTime) {
            this.lastCalculatedTime = lastCalculatedTime;
            return this;
        }

        public FinanceProfile build() {
            FinanceProfile profile = new FinanceProfile();
            profile.setId(id);
            profile.setUserId(userId);
            profile.setTotalDebt(totalDebt);
            profile.setDebtCount(debtCount);
            profile.setWeightedApr(weightedApr);
            profile.setMonthlyPayment(monthlyPayment);
            profile.setMonthlyIncome(monthlyIncome);
            profile.setDebtIncomeRatio(debtIncomeRatio);
            profile.setLiquidityScore(liquidityScore);
            profile.setRestructureScore(restructureScore);
            profile.setRiskLevel(riskLevel);
            profile.setOverdueCount(overdueCount);
            profile.setHighestAprDebtId(highestAprDebtId);
            profile.setScoreDetailJson(scoreDetailJson);
            profile.setLastCalculatedTime(lastCalculatedTime);
            profile.setGenerationStatus(generationStatus);
            profile.setGenerationRetryCount(generationRetryCount);
            profile.setCreateTime(createTime);
            profile.setUpdateTime(updateTime);
            profile.setDeleted(0);
            profile.setVersion(0);
            return profile;
        }
    }
}
