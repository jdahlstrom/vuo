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
package org.sharlin.vuo.impl;

import java.io.Serializable;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import org.sharlin.vuo.Flow;
import org.sharlin.vuo.Flow.Subscriber;

/**
 * Transforms a {@link Subscriber} into another. Given a subscriber, returns a
 * new subscriber that passes the sequence of signals it receives to the
 * original subscriber, but manipulated somehow. Depending on the operator, this
 * manipulation may entail discarding values, synthesizing new values,
 * transforming the values or more.
 * 
 * @author johannesd
 *
 * @param <T>
 *            the value type of the output subscriber
 * @param <U>
 *            the value type of the input subscriber
 */
public interface Operator<T, U> extends
        Function<Subscriber<? super U>, Subscriber<T>>, Serializable {

    /**
     * Returns an operator that transforms a subscriber into one that passes
     * each received value through a mapping function.
     * 
     * @param <T>
     *            the output value type
     * @param <U>
     *            the input value type
     * @param mapper
     *            the mapping function
     * @return a mapping operator
     */
    public static <T, U> Operator<T, U> map(
            Function<? super T, ? extends U> mapper) {

        return to -> new Sub<T, U>(to) {
            @Override
            public void onNext(T value) {
                to.onNext(mapper.apply(value));
            }
        };
    }

    /**
     * Returns an operator that transforms a subscriber into one that filters
     * out some subset of the received values based on a predicate.
     * 
     * @param <T>
     *            the value type
     * @param predicate
     *            the predicate used to test the values
     * @return a filtering operator
     */
    public static <T> Operator<T, T> filter(Predicate<? super T> predicate) {

        return to -> new Sub<T, T>(to) {
            @Override
            public void onNext(T value) {
                if (predicate.test(value)) {
                    to.onNext(value);
                }
            }
        };
    }

    /**
     * Returns an operator that transforms a subscriber into one that reduces
     * the values it receives into a single value.
     * 
     * @param <T>
     *            the output value type
     * @param <U>
     *            the input value type
     * @param reducer
     *            the reducing function
     * @param initial
     *            the initial value
     * @return a reduction operator
     */
    public static <T, U> Operator<T, U> reduce(BiFunction<U, T, U> reducer,
            U initial) {

        return to -> new Sub<T, U>(to) {
            private U accum = initial;

            @Override
            public void onNext(T value) {
                accum = reducer.apply(accum, value);
            }

            @Override
            public void onEnd() {
                to.onNext(accum);
                super.onEnd();
            }
        };
    }

    /**
     * Returns an operator that transforms a subscriber into one that maps
     * values into flows and concatenates the resulting sequence of flows.
     * 
     * @param <T>
     *            the output value type
     * @param <U>
     *            the input value type
     * @param mapper
     *            the mapping function
     * @return a map-and-flatten operator
     */
    public static <T, U> Operator<T, U> flatMap(
            Function<? super T, Flow<? extends U>> mapper) {
        return to -> new Sub<T, U>(to) {
            @Override
            public void onNext(T value) {
                mapper.apply(value).subscribe(//
                        to::onNext, //
                        to::onError, //
                        () -> {
                        });
            }
        };
    }

    /**
     * Returns an operator that transforms a subscriber into one that only
     * accepts an initial subsequence of values satisfying the given predicate.
     * 
     * @param <T>
     *            the value type
     * @param predicate
     *            the predicate used to pick the prefix sequence
     * @return a prefix sequence operator
     */
    public static <T> Operator<T, T> takeWhile(Predicate<? super T> predicate) {
        return to -> new Sub<T, T>(to) {
            private boolean taking = true;

            @Override
            public void onNext(T value) {
                if (!taking) {
                    return;
                } else if (predicate.test(value)) {
                    to.onNext(value);
                } else {
                    to.onEnd();
                    taking = false;
                }
            }

            @Override
            public void onEnd() {
                if (taking) {
                    to.onEnd();
                }
            }
        };
    }

    /**
     * Returns an operator that transforms a subscriber into one that drops an
     * initial subsequence of values satisfying the given predicate.
     * 
     * @param <T>
     *            the value type
     * @param predicate
     *            the predicate used to drop the prefix sequence
     * @return a prefix-dropping operator
     */
    public static <T> Operator<T, T> dropWhile(Predicate<? super T> predicate) {
        return to -> new Sub<T, T>(to) {
            private boolean dropping = true;

            @Override
            public void onNext(T value) {
                if (dropping) {
                    dropping = predicate.test(value);
                }
                if (!dropping) {
                    to.onNext(value);
                }
            }
        };
    }
}

/**
 * A helper subscriber wrapping another subscriber.
 * 
 * TODO consider moving elsewhere.
 * 
 * @author johannesd
 *
 * @param <T>
 *            the value type of this subscriber
 * @param <U>
 *            the value type of the wrapped subscriber
 */
abstract class Sub<T, U> implements Subscriber<T> {
    private Subscriber<? super U> wrapped;

    Sub(Subscriber<? super U> wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public void onError(Exception e) {
        wrapped.onError(e);
    }

    @Override
    public void onEnd() {
        wrapped.onEnd();
    }
}
