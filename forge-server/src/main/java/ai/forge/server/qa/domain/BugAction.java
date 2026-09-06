package ai.forge.server.qa.domain;

public enum BugAction {
    /* 研发领取 Bug 并开始修复。 */ START_FIX,
    /* 研发提交带修复证据的解决结果。 */ RESOLVE,
    /* QA 或管理员独立验证修复结果。 */ VERIFY,
    /* QA 或管理员关闭已验证的 Bug。 */ CLOSE,
    /* QA 将未通过验证或复发的 Bug 重新打开。 */ REOPEN,
    /* 有权限的成员取消不再处理的 Bug。 */ CANCEL
}
