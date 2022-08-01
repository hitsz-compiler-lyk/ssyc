package top.origami404.ssyc.ir;

public class GlobalModifitationStatus {
    public void resetStatus() {
        hasChanged = false;
    }

    public void markAsChanged() {
        hasChanged = true;
    }

    public boolean hasChanged() {
        return hasChanged;
    }

    public static GlobalModifitationStatus newStage() {
        status = new GlobalModifitationStatus(status);
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
        final var status = GlobalModifitationStatus.newStage();

        do {
            status.resetStatus();
            runner.run();
        } while (status.hasChanged());

        GlobalModifitationStatus.mergeStage();
    }

    public static GlobalModifitationStatus current() {
        return status;
    }

    private GlobalModifitationStatus(GlobalModifitationStatus parent) {
        this.hasChanged = false;
        this.parent = parent;
    }

    private static GlobalModifitationStatus status = new GlobalModifitationStatus(null);
    private boolean hasChanged;
    private GlobalModifitationStatus parent;
}
