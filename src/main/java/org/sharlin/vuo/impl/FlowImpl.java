package org.sharlin.vuo.impl;

import java.util.function.Consumer;

import org.sharlin.vuo.Flow;

public class FlowImpl<T> implements Flow<T> {

    private Consumer<Subscriber<? super T>> onSubscribe;

    public FlowImpl(Consumer<Subscriber<? super T>> onSubscribe) {
        this.onSubscribe = onSubscribe;
    }

    @Override
    public Subscription subscribe(Subscriber<? super T> subscriber) {
        onSubscribe.accept(subscriber);
        return null;
    }
}
