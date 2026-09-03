package ai.forge.server.workitem.domain;

public interface TransitionGuard {

    GuardResult evaluate(TransitionContext context);
}
