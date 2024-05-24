package org.axonframework.eventhandling.pooled;

import java.util.function.Function;

public interface MaxSegmentProvider extends Function<String, Integer> {
    int accept(String processingGroup);
}
