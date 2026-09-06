package ai.forge.server.qa.domain;

public enum BugSeverity {
    /* 阻断发布且必须优先处置的缺陷。 */ BLOCKER,
    /* 高影响并阻断发布的严重缺陷。 */ CRITICAL,
    /* 影响主要能力但存在替代路径的缺陷。 */ MAJOR,
    /* 局部影响且不阻断主要流程的缺陷。 */ MINOR
}
