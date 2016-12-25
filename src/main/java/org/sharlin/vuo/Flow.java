/*
 * Copyright 2016 Johannes Dahlström
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.sharlin.vuo;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.BaseStream;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.sharlin.vuo.impl.FlowImpl;
import org.sharlin.vuo.impl.Operator;

/**
 * A subscribable, possibly asynchronous sequence of values.
 * <p>
 * Flows are the mathematical <i>dual</i> of {@link Stream streams} and
 * {@link java.util.Iterable iterables}. Whereas the latter constitute values
 * that are <i>pulled</i> (for instance, by means of an
 * {@link java.util.Iterator iterator}), flows instead <i>push</i> values to
 * their {@link Subscriber subscribers}. A flow thus implements inversion of
 * control compared to streams and iterables.
 * <p>
 * A subscriber is a callback that the flow invokes with three different
 * signals. A <i>next</i> signal is simply the next value in the flow; an
 * <i>error</i> signal denotes an error condition when computing the next value;
 * and an <i>end</i> signal signifies the completion of the flow. An end signal
 * always immediately follows an error signal, and no signal may follow the end
 * signal. A flow may produce an unbounded number of values and never signal
 * completion.
 * <p>
 * A flow may be asynchronous; that is, its subscribers may be invoked from a
 * thread different from the one in which the subscription occurred; the
 * subscribe method may return before the flow completes. If the flow is
 * asynchronous, it is the responsibility of the subscriber to synchronize its
 * access to any mutable shared state.
 * <p>
 * Flows can also be <i>hot</i> or <i>cold</i>. A new subscriber to a hot flow
 * will only receive the values that are computed after the subscription. A cold
 * flow, on the other hand, provides all its values to each of its subscribers
 * regardless of the time the subscription occurred.
 * <p>
 * Unless otherwise specified, the functional objects passed to any flow methods
 * should be <i>pure</i>: their output should only depend on their input.
 * Depending on mutable state may yield unexpected results.
 * 
 * @author johannesd
 *
 * @param <T>
 *            The type of the values in this flow.
 */
public interface Flow<T> extends Serializable {

    /**
     * A subscription token.
     * 
     * TODO: Figure out how this should work
     */
    public class Subscription implements Serializable {
        private volatile boolean subscribed = true;

        /**
         * Ends this subscription. After calling this method, the associated
         * subscriber will not receive any more notifications from the
         * respective flow. If already unsubscribed, does nothing.
         */
        public void unsubscribe() {
            subscribed = false;
        }

        /**
         * Returns whether this subscription is still active, that is,
         * {@code true} if {@link #unsubscribe()} has not been called,
         * {@code false} otherwise.
         * 
         * @return the status of this subscription.
         */
        public boolean isSubscribed() {
            return subscribed;
        }
    }

    /*
     * Flow creation.
     */

    /**
     * Returns a new cold flow that produces the given values in sequence and
     * completes.
     * 
     * @param <T>
     *            the value type of the flow
     * @param values
     *            the values to produce
     * @return a flow produing the values
     */
    @SafeVarargs
    public static <T> Flow<T> of(T... values) {
        return from(values);
    }

    /**
     * Returns a flow that completes without producing any values.
     * 
     * @param <T>
     *            the value type of the flow
     * @return an empty flow
     */
    public static <T> Flow<T> empty() {
        return of();
    }

    /**
     * Returns a new cold flow that produces the given sequence of values and
     * then completes.
     * 
     * @param <T>
     *            the value type of the flow
     * @param values
     *            the sequence of values
     * @return a flow producing the values
     */
    public static <T> Flow<T> from(T[] values) {
        return new FlowImpl<>(sub -> {
            for (T t : values) {
                if (!sub.isSubscribed()) {
                    return;
                }
                sub.onNext(t);
            }
            if (sub.isSubscribed()) {
                sub.onEnd();
            }
        });
    }

    /**
     * Returns a new cold flow that produces the given sequence of values and
     * then completes.
     * 
     * @param <T>
     *            the value type of the flow
     * @param values
     *            the sequence of values
     * @return a flow producing the values.
     */
    public static <T> Flow<T> from(Iterable<T> values) {
        return new FlowImpl<>(sub -> {
            for (T t : values) {
                if (!sub.isSubscribed()) {
                    return;
                }
                sub.onNext(t);
            }
            if (sub.isSubscribed()) {
                sub.onEnd();
            }
        });
    }

    /**
     * Returns a new hot flow, yielding values from the given {@code Stream}.
     * The first subscriber to subscribe will consume the stream; any subsequent
     * subscribers will get an empty (immediately completing) flow.
     * 
     * @param <T>
     *            the value type of the flow
     * @param <S>
     *            the type of the stream
     * @param stream
     *            the stream to consume
     * @return a flow yielding the values from the stream
     */
    public static <T, S extends BaseStream<T, S>> Flow<T> from(
            BaseStream<T, S> stream) {
        return new FlowImpl<>(new Consumer<Subscriber<? super T>>() {
            boolean done = false;

            @Override
            public void accept(Subscriber<? super T> sub) {
                try {
                    if (!done) {
                        Iterator<T> i = stream.iterator();
                        while (i.hasNext() && sub.isSubscribed()) {
                            sub.onNext(i.next());
                        }
                    }
                } catch (Exception e) {
                    if (sub.isSubscribed()) {
                        sub.onError(e);
                    }
                } finally {
                    if (sub.isSubscribed()) {
                        sub.onEnd();
                    }
                    done = true;
                }
            }
        });
    }

    /**
     * Returns a new flow that produces the value of the given future when it
     * completes, or the error if the future completes exceptionally.
     * 
     * @param <T>
     *            the value type of the flow
     * @param future
     *            the future whose result to produce
     * @return a flow yielding a future result
     */
    public static <T> Flow<T> from(CompletableFuture<T> future) {
        return new FlowImpl<>(sub -> {
            future.whenComplete((value, e) -> {
                if (!sub.isSubscribed()) {
                    return;
                }
                if (value != null) {
                    sub.onNext(value);
                } else if (e instanceof Exception) {
                    sub.onError((Exception) e);
                } else if (e instanceof Error) {
                    throw (Error) e;
                } else {
                    throw new Error(e);
                }
                if (sub.isSubscribed()) {
                    sub.onEnd();
                }
            });
        });
    }

    public static <T> Flow<T> from(Optional<T> optional) {
        return new FlowImpl<>(sub -> {
            optional.ifPresent(sub::onNext);
            if (sub.isSubscribed()) {
                sub.onEnd();
            }
        });
    }

    /**
     * Returns a new flow drawing values from the given supplier. The supplier
     * is invoked until it returns an empty {@code Optional}; at that point the
     * flow completes. Any exception thrown by the supplier terminates the flow
     * with an error signal.
     * 
     * @param <T>
     *            the value type of the flow
     * @param supplier
     *            the producer of values
     * @return a flow yielding values from the supplier
     */
    public static <T> Flow<T> generate(Supplier<Optional<T>> supplier) {
        return new FlowImpl<>(sub -> {
            Optional<T> opt = Optional.empty();
            Exception ex = null;
            do {
                if (!sub.isSubscribed()) {
                    return;
                }
                try {
                    opt = supplier.get();
                } catch (Exception e) {
                    ex = e;
                }
                opt.ifPresent(sub::onNext);
            } while (ex == null && opt.isPresent());

            if (ex != null && sub.isSubscribed()) {
                sub.onError(ex);
            }
            if (sub.isSubscribed()) {
                sub.onEnd();
            }
        });
    }

    /**
     * Returns a flow producing values by iteratively applying the given
     * function to the previous value.
     * 
     * @param <T>
     *            the value type of the flow
     * @param initial
     *            the initial value yielded, not null
     * @param generator
     *            the function to produce subsequent values
     * @return a flow yielding values from the function
     */
    public static <T> Flow<T> iterate(T initial,
            Function<T, Optional<T>> generator) {
        return new FlowImpl<>(sub -> {
            Optional<T> opt = Optional.of(initial);
            Exception ex = null;
            do {
                if (!sub.isSubscribed()) {
                    return;
                }
                T next = opt.get(); // provably safe
                sub.onNext(next);
                try {
                    opt = generator.apply(next);
                } catch (Exception e) {
                    ex = e;
                }
            } while (ex == null && opt.isPresent());

            if (ex != null && sub.isSubscribed()) {
                sub.onError(ex);
            }
            if (sub.isSubscribed()) {
                sub.onEnd();
            }
        });
    }

    /**
     * Creates a new flow with the given subscription callback.
     * 
     * @param <U>
     *            the value type of the new flow
     * @param onSubscribe
     *            the callback invoked on each subscription
     * @return a new flow
     */
    public abstract <U> Flow<U> createFlow(
            Consumer<Subscriber<? super U>> onSubscribe);

    /*
     * Subscribing.
     */

    /**
     * Subscribes the given {@code Subscriber} to this flow. Its {@code onNext},
     * {@code onError}, and {@code onEnd} methods are invoked whenever this flow
     * produces a value, throws an error, or completes, respectively. The
     * subscriber cannot be already subscribed.
     * 
     * @param subscriber
     *            the subscriber to subscribe
     * @return the subscription
     */
    public abstract Subscription subscribe(Subscriber<? super T> subscriber);

    /**
     * Subscribes the given {@code Consumer} to this flow. The function is
     * invoked for each value produced by this flow. Termination signals are
     * discarded.
     * 
     * @param onNext
     *            the function consuming the values
     * @return the subscription
     */
    public default Subscription subscribe(Consumer<? super T> onNext) {
        return subscribe(Subscriber.from(onNext));
    }

    /**
     * Subscribes a set of three functions to this flow. The appropriate
     * function is invoked whenever this flow produces a value, throws an error,
     * or completes.
     * 
     * @param onNext
     *            the function invoked for values
     * @param onError
     *            the function invoked for exceptions
     * @param onEnd
     *            the function invoked at completion
     * @return the subscription
     */
    public default Subscription subscribe(Consumer<? super T> onNext,
            Consumer<? super Exception> onError, Runnable onEnd) {
        return subscribe(Subscriber.from(onNext, onError, onEnd));
    }

    /*
     * Composition.
     */

    /**
     * Merges the given flows into a single flow. The resulting flow will emit
     * the values from the input flows in the order they are produced.
     * 
     * @param flows
     *            the flows to merge
     * @return the merged flow
     */
    @SafeVarargs
    public static <T> Flow<T> merge(Flow<? extends T>... flows) {
        return from(flows).lift(Operator.merge());
    }

    /*
     * Combinators.
     * 
     * Each of these methods returns a new flow based on this flow, with some
     * transformation applied. Most of the methods are higher-order functions
     * accepting an user-provided callback to guide the transformation.
     */

    /**
     * Invokes the given consumer for each value in this flow. Error and end
     * signals are ignored.
     * 
     * @param consumer
     *            the function to invoke
     */
    public default void forEach(Consumer<? super T> consumer) {
        subscribe(consumer);
    }

    /**
     * Returns a new flow that constitutes the results of passing each value in
     * this flow through the given transformation. Error and end signals are
     * passed through as is.
     * 
     * @param <U>
     *            the output value type
     * @param mapper
     *            the function to transform the values
     * @return a flow with the transformed values
     */
    public default <U> Flow<U> map(Function<? super T, ? extends U> mapper) {
        return lift(Operator.map(mapper));
    }

    /**
     * Returns a new flow constituting the values in this flow that satisfy the
     * given predicate. Error and end signals are passed through as is.
     * 
     * @param predicate
     *            the predicate to apply to the values
     * @return a flow with exactly the values for which the predicate returns
     *         true
     */
    public default Flow<T> filter(Predicate<? super T> predicate) {
        return lift(Operator.filter(predicate));
    }

    /**
     * Returns a new flow containing at most a single {@code Optional} value:
     * the result of passing each value in this flow through the given reduction
     * function, or an empty optional if this flow is empty. The returned flow
     * produces the result, and an {@link Subscriber#onEnd() onEnd} signal, if
     * and only if this flow also eventually signals {@code onEnd}.
     * 
     * @param reducer
     *            the reduction function
     * @return a flow yielding the result of the reduction
     */
    public default Flow<Optional<T>> reduce(
            BiFunction<? super T, ? super T, T> reducer) {
        return lift(Operator.reduce(reducer));
    }

    /**
     * Returns a new flow containing at most a single value: the result of
     * passing each value in this flow through the given reduction function. The
     * returned flow produces the result, and an {@link Subscriber#onEnd()
     * onEnd} signal, if and only if this flow also eventually signals
     * {@code onEnd}.
     * 
     * @param <U>
     *            the output value type
     * @param initial
     *            the initial value for reduction
     * @param reducer
     *            the reduction function
     * @return a flow yielding the result of the reduction
     */
    public default <U> Flow<U> reduce(U initial,
            BiFunction<? super U, ? super T, U> reducer) {
        return lift(Operator.reduce(initial, reducer));
    }

    /**
     * Returns a new flow containing at most a single value: the result of
     * performing a <i>mutable reduction</i> to every value in this flow using
     * the given {@link Collector}. The returned flow provides the result, and
     * an {@link Subscriber#onEnd() onEnd} signal, if and only if this flow also
     * eventually signals {@code onEnd}.
     * 
     * @see Collectors
     * 
     * @param <U>
     *            the output value type
     * @param collector
     *            the collector
     * @return a flow yielding the result of the collect operation
     */
    public default <U> Flow<U> collect(
            Collector<? super T, ?, U> collector) {
        return lift(Operator.collect(collector));
    }

    /**
     * Returns a new flow constructed by invoking the given flow-returning
     * function for each value in this flow and concatenating the resulting
     * flows into a single flow. Thus, the end signals from the comprising flows
     * are not propagated to the new flow; any error, however, is.
     * <p>
     * Given some function {@code f : Function<T, Flow<U>>}, invoking
     * {@link #map(Function) map(f)} would return a flow of type
     * {@code Flow<Flow<U>>}. The {@code flatMap} method instead flattens the
     * flow of flows into a single {@code Flow<U>}.
     * 
     * @param <U>
     *            the value type of the returned flow
     * @param mapper
     *            the mapping function
     * @return a flattened flow of results
     */
    public default <U> Flow<U> flatMap(
            Function<? super T, Flow<? extends U>> mapper) {
        return lift(Operator.flatMap(mapper));
    }

    /**
     * Returns a flow with the first {@code n} values in this flow. If this flow
     * terminates before yielding {@code n} values, the new flow terminates as
     * well.
     * 
     * @param n
     *            the number of initial values to pick
     * @return a flow with the initial values
     */
    public default Flow<T> take(long n) {
        return lift(Operator.take(n));
    }

    /**
     * Returns a flow with all the values except the initial {@code n} values in
     * this flow. If this flow terminates before yielding {@code n} values, the
     * new flow terminates as well without producing any values.
     * 
     * @param n
     *            the number of initial values to drop
     * @return a flow without the initial values
     */
    public default Flow<T> skip(long n) {
        return lift(Operator.skip(n));
    }

    /**
     * Returns a flow constituting all the values in this flow up to, but not
     * including, the first value for which the given predicate returns
     * {@code false}. At that point, the flow completes and the predicate is not
     * applied to any subsequent values. If this flow terminates before the
     * predicate fails, the returned flow terminates as well.
     * 
     * @param predicate
     *            the predicate to apply
     * @return a flow of the initial values that satisfy the predicate
     */
    public default Flow<T> takeWhile(Predicate<? super T> predicate) {
        return lift(Operator.takeWhile(predicate));
    }

    /**
     * Returns a flow constituting every value in this flow starting from,
     * inclusive, the first value for which the given predicate returns
     * {@code false}. The predicate is not applied to any subsequent values. If
     * this flow terminates before the predicate fails, the returned flow
     * terminates as well without producing any values.
     * 
     * @param predicate
     *            the predicate to apply
     * @return a flow excluding the initial values that satisfy the predicate
     */
    public default Flow<T> skipWhile(Predicate<? super T> predicate) {
        return lift(Operator.skipWhile(predicate));
    }

    /**
     * Returns a flow that yields at most a single boolean value, indicating
     * whether any of the values in this flow satisfy the given predicate or
     * not. The flow is short-circuiting: the first value to pass the predicate
     * yields {@code true} and the predicate is not applied to any subsequent
     * values. The flow yields {@code false} if and only if this flow completes
     * before the predicate returns {@code true}.
     * 
     * @param predicate
     *            the predicate to apply
     * @return a flow indicating whether any values pass the predicate
     */
    public default Flow<Boolean> anyMatch(Predicate<? super T> predicate) {
        return lift(Operator.anyMatch(predicate));
    }

    /**
     * Returns a flow that yields at most a single boolean value, indicating
     * whether every value in this flow satisfies the given predicate or not.
     * The flow is short-circuiting: the first value to fail the predicate
     * yields {@code false} and the predicate is not applied to any subsequent
     * values. The flow yields {@code true} if and only if this flow completes
     * before the predicate returns {@code false}.
     * 
     * @param predicate
     *            the predicate to apply
     * @return a flow indicating whether all values pass the predicate
     */
    public default Flow<Boolean> allMatch(Predicate<? super T> predicate) {
        return lift(Operator.allMatch(predicate));
    }

    /**
     * Returns a flow that yields at most a single boolean value, indicating
     * whether every value in this flow fails the given predicate or not. The
     * flow is short-circuiting: the first value to match the predicate yields
     * {@code false} and the predicate is not applied to any subsequent values.
     * The flow yields {@code true} if and only if this flow completes before
     * the predicate returns {@code false}.
     * 
     * @param predicate
     *            the predicate to apply
     * @return a flow indicating whether none of the values pass the predicate
     */
    public default Flow<Boolean> noneMatch(Predicate<? super T> predicate) {
        return lift(Operator.allMatch(t -> !predicate.test(t)));
    }

    /**
     * Returns a flow with at most a single value: the number of values in this
     * flow. The returned flow provides a value, and completes, if and only if
     * this flow eventually completes. If this flow terminates via an error, the
     * error is propagated and a count is not provided.
     * 
     * @return a flow with the number of values in this flow, if finite
     */
    public default Flow<Long> count() {
        return reduce(0L, (count, x) -> count + 1L);
    }

    /*
     * Helper methods.
     * 
     * TODO: Figure out whether these should be moved elsewhere.
     */

    /**
     * Returns a new flow, possibly of different value type, whose subscribers
     * are adapted to this flow using the given operator. That is, any
     * subscribers to the new flow are passed to {@link Operator#apply(Object)
     * op.apply()} and the result subscribed to this flow.
     * 
     * @param <U>
     *            the value type of the new flow
     * @param op
     *            the operator with which to adapt subscribers
     * @return a flow with adapted subscribers
     */
    public default <U> Flow<U> lift(Operator<? super T, ? extends U> op) {
        return createFlow(s -> subscribe(op.apply(s)));
    }
}
