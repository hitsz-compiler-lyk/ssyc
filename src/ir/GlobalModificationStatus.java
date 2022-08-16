package ir;

public class GlobalModificationStatus {
    public void resetStatus() {
        hasChanged = false;
    }

    public void markAsChanged() {
        hasChanged = true;
    }

    public boolean hasChanged() {
        return hasChanged;
    }

    public static GlobalModificationStatus newStage() {
        status = new GlobalModificationStatus(status);
        return status;
    }

    public static void mergeStage() {
        final var old = status;

        status = old.parent;
        if (old.hasChanged) {
            status.hasChanged = true;
        }
    }

    public static void doUntilNoChange(Runnable runner) {
        final var status = GlobalModificationStatus.newStage();

        do {
            status.resetStatus();
            runner.run();
        } while (status.hasChanged());

        GlobalModificationStatus.mergeStage();
    }

    public static GlobalModificationStatus current() {
        return status;
    }

    private GlobalModificationStatus(GlobalModificationStatus parent) {
        this.hasChanged = false;
        this.parent = parent;
    }

    private static GlobalModificationStatus status = new GlobalModificationStatus(null);
    private boolean hasChanged;
    private GlobalModificationStatus parent;
}
