/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.build.event.types;

import org.gradle.tooling.internal.protocol.InternalBasicProblemDetailsVersion3;
import org.gradle.tooling.internal.protocol.InternalFailure;
import org.gradle.tooling.internal.protocol.events.InternalFailureWithProblemsResult;

import java.util.List;

// TODO (donat) Find a better name for this class
public class DefaultFailureWithProblemResult extends DefaultFailureResult implements InternalFailureWithProblemsResult {

    final List<InternalBasicProblemDetailsVersion3> problems; // TODO (donat) Collection or list?

    public DefaultFailureWithProblemResult(long startTime, long endTime, List<InternalFailure> failures, List<InternalBasicProblemDetailsVersion3> problems) {
        super(startTime, endTime, failures);
        this.problems = problems;
    }

    @Override
    public List<InternalBasicProblemDetailsVersion3> getProblems() {
        return problems;
    }
}
