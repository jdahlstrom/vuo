package org.sharlin.vuo.react.harnesses;

import java.util.function.Supplier;

import org.sharlin.vuo.Flow;
import org.sharlin.vuo.Subscriber;

public class SyncFlowTestHarness extends FlowTestHarness {

    @Override
    @SuppressWarnings("unchecked")
    public <T> Flow<T> flow(T... actual) {
        return Flow.of(actual);
    }

    @Override
    public <T> void verifyFlow(Flow<T> flow,
            Supplier<Subscriber<? super T>> subSup) {
        for (int i = 0; i < 2; i++) {
            verifyFlow(flow, subSup.get());
        }
    }
}
