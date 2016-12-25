package org.sharlin.vuo.react;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.sharlin.vuo.Flow;
import org.sharlin.vuo.react.harnesses.FlowTestHarness;
import org.sharlin.vuo.react.harnesses.SyncFlowTestHarness;

public class FlowCreationTest extends FlowTestBase {

    @Rule
    public Timeout timeout = new Timeout(1000000 * FlowTestHarness.TIMEOUT_SEC);

    @Test
    public void testEmpty() {
        Flow<String> flow = Flow.empty();
        verifyFlow(flow, expect());
    }

    @Test
    public void testOfValues() {
        Flow<String> flow = Flow.of("a", "b", "c");
        verifyFlow(flow, expect("a", "b", "c"));

        flow = Flow.of();
        verifyFlow(flow, expect());
    }

    @Test
    public void testFromArray() {
        Flow<Integer> flow = Flow.from(new Integer[] { 1, 2, 3 });
        verifyFlow(flow, expect(1, 2, 3));

        flow = Flow.from(new Integer[] {});
        verifyFlow(flow, expect());
    }

    @Test
    public void testFromIterable() {
        Flow<Integer> flow = Flow.from(
                (Iterable<Integer>) Arrays.asList(1, 2, 3));
        verifyFlow(flow, expect(1, 2, 3));

        Iterator<Integer> infinite = new Iterator<Integer>() {
            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public Integer next() {
                return 42;
            }
        };
        flow = Flow.from(() -> infinite);
        verifyFlow(flow.take(3), expect(42, 42, 42));
    }

    @Test
    public void testFromStream() {
        Flow<Integer> flow = Flow
                .from(Arrays.stream(new int[] { 1, 2, 3 }));

        verifyFlow(flow, expect(1, 2, 3).get());
        // Stream can only be consumed once
        verifyFlow(flow, expect().get());

        flow = Flow.from(Arrays.stream(new int[] { 1, 2, 3 }));

        verifyFlow(flow, expectAndUnsubscribe(1).get());
        verifyFlow(flow, expectAndUnsubscribe().get());
    }

    @Test
    public void testFromFuture() {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        Flow<Integer> flow = Flow.from(future);
        future.complete(42);

        verifyFlow(flow, expect(42));
        verifyFlow(flow, expectAndUnsubscribe(42));

        future = new CompletableFuture<>();
        flow = Flow.from(future);
        Exception e = new Exception();
        future.completeExceptionally(e);

        verifyFlow(flow, expectError(e));
    }

    @Test
    public void testFromOptional() {
        verifyFlow(Flow.from(Optional.empty()), expect());
        verifyFlow(Flow.from(Optional.of(42)), expect(42));
    }

    @Test
    public void testGenerate() {
        int[] counter = new int[] { 0 };
        Flow<Integer> flow = Flow.generate(() -> counter[0]++);

        verifyFlow(flow.take(5), expect(0, 1, 2, 3, 4).get());
        verifyFlow(flow, expectAndUnsubscribe(5, 6, 7).get());
        verifyFlow(flow.take(1), expect(8).get());

        RuntimeException e = new RuntimeException();
        flow = Flow.generate(() -> {
            throw e;
        });
        verifyFlow(flow, expectError(e));
    }

    @Test
    public void testIterate() {
        Flow<Integer> flow = Flow.iterate(1, i -> i * 2);

        verifyFlow(flow.take(4), expect(1, 2, 4, 8));
        verifyFlow(flow, expectAndUnsubscribe(1, 2));

        RuntimeException e = new RuntimeException();
        flow = Flow.iterate(0, x -> {
            throw e;
        });
        verifyFlow(flow.skip(1), expectError(e));
    }

    @Override
    protected FlowTestHarness getHarness() {
        return harness;
    }

    private FlowTestHarness harness = new SyncFlowTestHarness();
}
