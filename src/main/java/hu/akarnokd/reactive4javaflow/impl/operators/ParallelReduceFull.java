/*
 * Copyright 2017 David Karnok
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package hu.akarnokd.reactive4javaflow.impl.operators;

import hu.akarnokd.reactive4javaflow.*;
import hu.akarnokd.reactive4javaflow.functionals.CheckedBiFunction;
import hu.akarnokd.reactive4javaflow.impl.*;

import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.*;

/**
 * Reduces all 'rails' into a single value which then gets reduced into a single
 * Publisher sequence.
 *
 * @param <T> the value type
 */
public final class ParallelReduceFull<T> extends Esetleg<T> {

    final ParallelFolyam<? extends T> source;

    final CheckedBiFunction<T, T, T> reducer;

    public ParallelReduceFull(ParallelFolyam<? extends T> source, CheckedBiFunction<T, T, T> reducer) {
        this.source = source;
        this.reducer = reducer;
    }

    @Override
    protected void subscribeActual(FolyamSubscriber<? super T> s) {
        ParallelReduceFullMainSubscriber<T> parent = new ParallelReduceFullMainSubscriber<>(s, source.parallelism(), reducer);
        s.onSubscribe(parent);

        source.subscribe(parent.subscribers);
    }

    static final class ParallelReduceFullMainSubscriber<T> extends DeferredScalarSubscription<T> {


        private static final long serialVersionUID = -5370107872170712765L;

        final ParallelReduceFullInnerSubscriber<T>[] subscribers;

        final CheckedBiFunction<T, T, T> reducer;

        final AtomicReference<SlotPair<T>> current = new AtomicReference<>();

        final AtomicInteger remaining = new AtomicInteger();

        final AtomicReference<Throwable> error = new AtomicReference<>();

        ParallelReduceFullMainSubscriber(FolyamSubscriber<? super T> subscriber, int n, CheckedBiFunction<T, T, T> reducer) {
            super(subscriber);
            @SuppressWarnings("unchecked")
            ParallelReduceFullInnerSubscriber<T>[] a = new ParallelReduceFullInnerSubscriber[n];
            for (int i = 0; i < n; i++) {
                a[i] = new ParallelReduceFullInnerSubscriber<>(this, reducer);
            }
            this.subscribers = a;
            this.reducer = reducer;
            remaining.lazySet(n);
        }

        SlotPair<T> addValue(T value) {
            for (;;) {
                SlotPair<T> curr = current.get();

                if (curr == null) {
                    curr = new SlotPair<>();
                    if (!current.compareAndSet(null, curr)) {
                        continue;
                    }
                }

                int c = curr.tryAcquireSlot();
                if (c < 0) {
                    current.compareAndSet(curr, null);
                    continue;
                }
                if (c == 0) {
                    curr.first = value;
                } else {
                    curr.second = value;
                }

                if (curr.releaseSlot()) {
                    current.compareAndSet(curr, null);
                    return curr;
                }
                return null;
            }
        }

        @Override
        public void cancel() {
            for (ParallelReduceFullInnerSubscriber<T> inner : subscribers) {
                inner.cancel();
            }
        }

        void innerError(Throwable ex) {
            if (error.compareAndSet(null, ex)) {
                cancel();
                actual.onError(ex);
            } else {
                if (ex != error.get()) {
                    FolyamPlugins.onError(ex);
                }
            }
        }

        void innerComplete(T value) {
            if (value != null) {
                for (;;) {
                    SlotPair<T> sp = addValue(value);

                    if (sp != null) {

                        try {
                            value = Objects.requireNonNull(reducer.apply(sp.first, sp.second), "The reducer returned a null value");
                        } catch (Throwable ex) {
                            FolyamPlugins.handleFatal(ex);
                            innerError(ex);
                            return;
                        }

                    } else {
                        break;
                    }
                }
            }

            if (remaining.decrementAndGet() == 0) {
                SlotPair<T> sp = current.get();
                current.lazySet(null);

                if (sp != null) {
                    complete(sp.first);
                } else {
                    actual.onComplete();
                }
            }
        }
    }

    static final class ParallelReduceFullInnerSubscriber<T>
    extends AtomicReference<Flow.Subscription>
    implements FolyamSubscriber<T> {

        private static final long serialVersionUID = -7954444275102466525L;

        final ParallelReduceFullMainSubscriber<T> parent;

        final CheckedBiFunction<T, T, T> reducer;

        T value;

        boolean done;

        ParallelReduceFullInnerSubscriber(ParallelReduceFullMainSubscriber<T> parent, CheckedBiFunction<T, T, T> reducer) {
            this.parent = parent;
            this.reducer = reducer;
        }

        @Override
        public void onSubscribe(Flow.Subscription s) {
            if (SubscriptionHelper.replace(this, s)) {
                s.request(Long.MAX_VALUE);
            }
        }

        @Override
        public void onNext(T t) {
            if (!done) {
                T v = value;

                if (v == null) {
                    value = t;
                } else {

                    try {
                        v = Objects.requireNonNull(reducer.apply(v, t), "The reducer returned a null value");
                    } catch (Throwable ex) {
                        FolyamPlugins.handleFatal(ex);
                        get().cancel();
                        onError(ex);
                        return;
                    }

                    value = v;
                }
            }
        }

        @Override
        public void onError(Throwable t) {
            if (done) {
                FolyamPlugins.onError(t);
                return;
            }
            done = true;
            parent.innerError(t);
        }

        @Override
        public void onComplete() {
            if (!done) {
                done = true;
                parent.innerComplete(value);
            }
        }

        void cancel() {
            SubscriptionHelper.cancel(this);
        }
    }

    static final class SlotPair<T> extends AtomicInteger {

        private static final long serialVersionUID = 473971317683868662L;

        T first;

        T second;

        final AtomicInteger releaseIndex = new AtomicInteger();

        int tryAcquireSlot() {
            for (;;) {
                int acquired = get();
                if (acquired >= 2) {
                    return -1;
                }

                if (compareAndSet(acquired, acquired + 1)) {
                    return acquired;
                }
            }
        }

        boolean releaseSlot() {
            return releaseIndex.incrementAndGet() == 2;
        }
    }
}
