package dev.cuboidplots.core;

/** DEFER never means deny. The parent owner and team keep their normal permissions. */
public final class Decision {
    public enum Result { REGION_GRANT, DEFER_TO_PARENT }
    public final Result result;
    public final String reason;
    private Decision(Result result, String reason) { this.result = result; this.reason = reason; }
    public static Decision grant() { return new Decision(Result.REGION_GRANT, "Explicit UUID grant covers every affected position; parent binding is current"); }
    public static Decision defer(String reason) { return new Decision(Result.DEFER_TO_PARENT, reason); }
    public boolean isGrant() { return result == Result.REGION_GRANT; }
}
