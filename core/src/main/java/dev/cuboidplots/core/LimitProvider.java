package dev.cuboidplots.core;

import java.util.UUID;

/** A future permission integration implements this one method. Failures must throw. */
public interface LimitProvider {
    Limits effectiveLimits(UUID creator) throws Exception;
}
