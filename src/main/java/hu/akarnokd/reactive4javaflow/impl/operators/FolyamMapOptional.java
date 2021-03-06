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
import hu.akarnokd.reactive4javaflow.functionals.CheckedFunction;
import hu.akarnokd.reactive4javaflow.fused.*;

import java.util.*;
import java.util.concurrent.Flow;

public final class FolyamMapOptional<T, R> extends Folyam<R> {

    final Folyam<T> source;

    final CheckedFunction<? super T, ? extends Optional<? extends R>> mapper;

    public FolyamMapOptional(Folyam<T> source, CheckedFunction<? super T, ? extends Optional<? extends R>> mapper) {
        this.source = source;
        this.mapper = mapper;
    }

    @Override
    protected void subscribeActual(FolyamSubscriber<? super R> s) {
        if (s instanceof ConditionalSubscriber) {
            source.subscribe(new MapConditionalSubscriber<>((ConditionalSubscriber<? super R>) s, mapper));
        } else {
            source.subscribe(new MapSubscriber<>(s, mapper));
        }
    }

    static abstract class AbstractMapSubscriber<T, R> implements ConditionalSubscriber<T>, FusedSubscription<R> {

        final CheckedFunction<? super T, ? extends Optional<? extends R>> mapper;

        boolean done;

        Flow.Subscription upstream;

        FusedSubscription<T> qs;

        boolean asyncFused;

        protected AbstractMapSubscriber(CheckedFunction<? super T, ? extends Optional<? extends R>> mapper) {
            this.mapper = mapper;
        }

        @Override
        public final R poll() throws Throwable {
            FusedSubscription<T> fs = qs;
            for (;;) {
                T v = fs.poll();
                if (v == null) {
                    return null;
                }

                Optional<? extends R> r = Objects.requireNonNull(mapper.apply(v), "The mapper returned a null Optional");

                if (r.isPresent()) {
                    return r.get();
                }

                if (asyncFused) {
                    fs.request(1);
                }
            }
        }

        @Override
        public final boolean isEmpty() {
            return qs.isEmpty();
        }

        @Override
        public final void clear() {
            qs.clear();
        }

        @Override
        public final int requestFusion(int mode) {
            if (qs != null && (mode & BOUNDARY) == 0) {
                int m = qs.requestFusion(mode);
                asyncFused = (m & ASYNC) != 0;
                return m;
            }
            return NONE;
        }

        @Override
        public final void onSubscribe(Flow.Subscription subscription) {
            this.upstream = subscription;

            if (subscription instanceof FusedSubscription) {
                qs = (FusedSubscription) subscription;
            }

            start();
        }

        abstract void start();

        @Override
        public final void cancel() {
            upstream.cancel();
        }

        @Override
        public final void request(long n) {
            upstream.request(n);
        }

        @Override
        public final void onNext(T item) {
            if (!tryOnNext(item) && !done) {
                upstream.request(1);
            }
        }

    }

    static final class MapSubscriber<T, R> extends AbstractMapSubscriber<T, R> {

        final FolyamSubscriber<? super R> actual;

        protected MapSubscriber(FolyamSubscriber<? super R> actual, CheckedFunction<? super T, ? extends Optional<? extends R>> mapper) {
            super(mapper);
            this.actual = actual;
        }

        @Override
        void start() {
            actual.onSubscribe(this);
        }

        @Override
        public boolean tryOnNext(T item) {
            if (done) {
                return false;
            }
            if (item == null) {
                actual.onNext(null);
                return true;
            }
            Optional<? extends R> v;

            try {
                v = Objects.requireNonNull(mapper.apply(item), "The mapper returned a null Optional");
            } catch (Throwable ex) {
                upstream.cancel();
                onError(ex);
                return false;
            }

            if (v.isPresent()) {
                actual.onNext(v.get());
                return true;
            }
            return false;
        }

        @Override
        public void onError(Throwable throwable) {
            if (done) {
                FolyamPlugins.onError(throwable);
                return;
            }
            done = true;
            actual.onError(throwable);
        }

        @Override
        public void onComplete() {
            if (done) {
                return;
            }
            done = true;
            actual.onComplete();
        }
    }

    static final class MapConditionalSubscriber<T, R> extends AbstractMapSubscriber<T, R> implements ConditionalSubscriber<T> {

        final ConditionalSubscriber<? super R> actual;

        protected MapConditionalSubscriber(ConditionalSubscriber<? super R> actual, CheckedFunction<? super T, ? extends Optional<? extends R>> mapper) {
            super(mapper);
            this.actual = actual;
        }

        @Override
        void start() {
            actual.onSubscribe(this);
        }

        @Override
        public boolean tryOnNext(T item) {
            if (done) {
                return false;
            }
            if (item == null) {
                return actual.tryOnNext(null);
            }
            Optional<? extends R> v;

            try {
                v = Objects.requireNonNull(mapper.apply(item), "The mapper returned a null Optional");
            } catch (Throwable ex) {
                upstream.cancel();
                onError(ex);
                return false;
            }

            return v.isPresent() && actual.tryOnNext(v.get());
        }

        @Override
        public void onError(Throwable throwable) {
            if (done) {
                FolyamPlugins.onError(throwable);
                return;
            }
            done = true;
            actual.onError(throwable);
        }

        @Override
        public void onComplete() {
            if (done) {
                return;
            }
            done = true;
            actual.onComplete();
        }
    }
}
