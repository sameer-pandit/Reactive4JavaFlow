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
import hu.akarnokd.reactive4javaflow.impl.DeferredScalarSubscription;

import java.util.concurrent.Flow;

public final class FolyamTakeLastOneFolyam<T> extends Folyam<T> {

    final Folyam<T> source;

    public FolyamTakeLastOneFolyam(Folyam<T> source) {
        this.source = source;
    }

    @Override
    protected void subscribeActual(FolyamSubscriber<? super T> s) {
        source.subscribe(new TakeLastOneSubscriber<>(s));
    }

    static final class TakeLastOneSubscriber<T> extends DeferredScalarSubscription<T> implements FolyamSubscriber<T> {

        Flow.Subscription upstream;

        public TakeLastOneSubscriber(FolyamSubscriber<? super T> actual) {
            super(actual);
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            upstream = subscription;
            actual.onSubscribe(this);
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(T item) {
            value = item;
        }

        @Override
        public void onError(Throwable throwable) {
            value = null;
            error(throwable);
        }

        @Override
        public void onComplete() {
            T v = value;
            if (v == null) {
                complete();
            } else {
                complete(v);
            }
        }

        @Override
        public void cancel() {
            super.cancel();
            upstream.cancel();
        }
    }
}
