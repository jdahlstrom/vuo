# Vuo - Composable push-based sequences for Java 8

## Introduction

Vuo is a library providing support for subscription-based sequences of values, or *flows*. Like futures, flows represent values that might not be available yet.  Unlike a future, however, a flow may represent a sequence of *several* values that need not become available all at once. This can model the result of a computation that completes in a stepwise manner, such as transfer of data over a network, or a series of discrete asynchronous events such as server requests or user actions in an interactive application.

## Flows

### Creating flows

### Subscribing to flows

### Flow combinators

#### map
```java
<U> Flow<U> map(Function<? super T, ? extends U> mapper)
```
Returns a new flow that constitutes the results of passing each value in this flow through the given transformation. Error and end signals are passed through as is.

#### filter
```java
Flow<T> filter(Predicate<? super T> predicate)`
```
Returns a new flow constituting the values in this flow that satisfy the given predicate. Error and end signals are passed through as is.

#### flatMap
```java
<U> Flow<U> flatMap(Function<? super T, Flow<? extends U>> mapper)`
```
Returns a new flow constructed by invoking the given flow-returning function for each value in this flow and concatenating the resulting flows into a single flow. Thus, the end signals from the comprising flows are not propagated to the new flow; any error, however, is.

Given some function `f : Function<T, Flow<U>>`, invoking map(f) would return a flow of type `Flow<Flow<U>>`. The flatMap method instead flattens the flow of flows into a single `Flow<U>`.

#### reduce
```java
Flow<Optional<T>> reduce(BiFunction<? super T, ? super T, T> reducer)`
```    
Returns a new flow containing at most a single Optional value: the result of passing each value in this flow through the given reduction function, or an empty optional if this flow is empty. The returned flow produces the result, and an onEnd signal, if and only if this flow also eventually signals onEnd.

```java
<U> Flow<U> reduce(U initial, BiFunction<? super U, ? super T, U> reducer)`
```
Returns a new flow containing at most a single value: the result of passing each value in this flow through the given reduction function. The returned flow produces the result, and an onEnd signal, if and only if this flow also eventually signals onEnd.

#### collect
```java
<U> Flow<U> collect(Collector<? super T, ?, U> collector)`
```
Returns a new flow containing at most a single value: the result of performing a mutable reduction to every value in this flow using the given Collector. The returned flow provides the result, and an onEnd signal, if and only if this flow also eventually signals onEnd.

#### take
```java
Flow<T> take(long n)
```
Returns a flow with the first n values in this flow. If this flow terminates before yielding n values, the new flow terminates as well.

#### skip
```java
Flow<T> skip(long n)
```
Returns a flow with all the values except the initial n values in this flow. If this flow terminates before yielding n values, the new flow terminates as well without producing any values.

#### takeWhile
```java
Flow<T> takeWhile(Predicate<? super T> predicate)
```
Returns a flow constituting all the values in this flow up to, but not including, the first value for which the given predicate returns false. At that point, the flow completes and the predicate is not applied to any subsequent values. If this flow terminates before the predicate fails, the returned flow terminates as well.

#### skipWhile
```java
Flow<T> skipWhile(Predicate<? super T> predicate)
```
Returns a flow constituting every value in this flow starting from, inclusive, the first value for which the given predicate returns false. The predicate is not applied to any subsequent values. If this flow terminates before the predicate fails, the returned flow terminates as well without producing any values.

#### anyMatch
```java
Flow<Boolean> anyMatch(Predicate<? super T> predicate)
```
Returns a flow that yields at most a single boolean value, indicating whether any of the values in this flow satisfy the given predicate or not. The flow is short-circuiting: the first value to pass the predicate yields true and the predicate is not applied to any subsequent values. The flow yields false if and only if this flow completes before the predicate returns true.

#### allMatch
```java
Flow<Boolean> allMatch(Predicate<? super T> predicate)
```
Returns a flow that yields at most a single boolean value, indicating whether every value in this flow satisfies the given predicate or not. The flow is short-circuiting: the first value to fail the predicate yields false and the predicate is not applied to any subsequent values. The flow yields true if and only if this flow completes before the predicate returns false.

#### noneMatch
```java
Flow<Boolean> noneMatch(Predicate<? super T> predicate)
```
Returns a flow that yields at most a single boolean value, indicating whether every value in this flow fails the given predicate or not. The flow is short-circuiting: the first value to match the predicate yields false and the predicate is not applied to any subsequent values. The flow yields true if and only if this flow completes before the predicate returns false.

#### count
```java
Flow<Long> count()
```

Returns a flow with at most a single value: the number of values in this flow. The returned flow provides a value, and completes, if and only if this flow eventually completes. If this flow terminates via an error, the error is propagated and a count is not provided.
