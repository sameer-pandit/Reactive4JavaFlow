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
import hu.akarnokd.reactive4javaflow.fused.*;

import java.lang.invoke.*;
import java.util.concurrent.Flow;

public final class EsetlegOnTerminateDetach<T> extends Esetleg<T> {

    final Esetleg<T> source;

    public EsetlegOnTerminateDetach(Esetleg<T> source) {
        this.source = source;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void subscribeActual(FolyamSubscriber<? super T> s) {
        if (s instanceof ConditionalSubscriber) {
            source.subscribe(new FolyamOnTerminateDetach.OnTerminateDetachConditionalSubscriber<>((ConditionalSubscriber<? super T>)s));
        } else {
            source.subscribe(new FolyamOnTerminateDetach.OnTerminateDetachSubscriber<>(s));
        }
    }
}