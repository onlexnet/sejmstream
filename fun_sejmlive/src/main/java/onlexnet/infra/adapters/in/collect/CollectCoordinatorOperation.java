package onlexnet.infra.adapters.in.collect;

enum CollectCoordinatorOperation {
    REQUEST_COLLECT("requestCollect"),
    COLLECT_COMPLETED("collectCompleted"),
    COLLECT_FAILED("collectFailed");

    private final String methodName;

    CollectCoordinatorOperation(String methodName) {
        this.methodName = methodName;
    }

    String methodName() {
        return methodName;
    }
}
