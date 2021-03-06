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
import org.junit.Test;

import java.io.IOException;

public class EsetlegOnErrorCompleteTest {

    @Test
    public void standard() {
        TestHelper.assertResult(Esetleg.just(1).onErrorComplete(), 1);
    }

    @Test
    public void standardHidden() {
        TestHelper.assertResult(Esetleg.just(1).hide().onErrorComplete(), 1);
    }

    @Test
    public void error() {
        Esetleg.error(new IOException())
                .onErrorComplete()
                .test()
                .assertResult();
    }


    @Test
    public void errorConditional() {
        Esetleg.error(new IOException())
                .onErrorComplete()
                .filter(v -> true)
                .test()
                .assertResult();
    }
}
