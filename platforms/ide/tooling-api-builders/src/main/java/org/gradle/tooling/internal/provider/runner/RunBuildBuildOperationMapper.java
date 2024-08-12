/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.api.problems.internal.Problem;
import org.gradle.internal.build.event.BuildEventSubscriptions;
import org.gradle.internal.build.event.types.AbstractOperationResult;
import org.gradle.internal.build.event.types.DefaultBuildBuildDescriptor;
import org.gradle.internal.build.event.types.DefaultFailure;
import org.gradle.internal.build.event.types.DefaultFailureWithProblemResult;
import org.gradle.internal.build.event.types.DefaultOperationFinishedProgressEvent;
import org.gradle.internal.build.event.types.DefaultOperationStartedProgressEvent;
import org.gradle.internal.build.event.types.DefaultSuccessResult;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.launcher.exec.RunBuildBuildOperationType;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.internal.protocol.InternalBasicProblemDetailsVersion3;
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

class RunBuildBuildOperationMapper implements BuildOperationMapper<RunBuildBuildOperationType.Details, DefaultBuildBuildDescriptor> {

    @Nullable
    private final ProblemsProgressEventConsumer problemConsumer;

    public RunBuildBuildOperationMapper(@Nullable ProblemsProgressEventConsumer problemConsumer) {this.problemConsumer = problemConsumer;}

    @Override
    public boolean isEnabled(BuildEventSubscriptions subscriptions) {
        return subscriptions.isRequested(OperationType.PROBLEMS);
    }

    @Override
    public Class<RunBuildBuildOperationType.Details> getDetailsType() {
        return RunBuildBuildOperationType.Details.class;
    }

    @Override
    public DefaultBuildBuildDescriptor createDescriptor(RunBuildBuildOperationType.Details details, BuildOperationDescriptor buildOperation, @Nullable OperationIdentifier parent) {
        OperationIdentifier id = buildOperation.getId();
        String displayName = buildOperation.getDisplayName();
        return new DefaultBuildBuildDescriptor(id, displayName, displayName, parent);
    }

    @Override
    public InternalOperationStartedProgressEvent createStartedEvent(DefaultBuildBuildDescriptor descriptor, RunBuildBuildOperationType.Details details, OperationStartEvent startEvent) {
        return new DefaultOperationStartedProgressEvent(startEvent.getStartTime(), descriptor);
    }

    @Override
    public InternalOperationFinishedProgressEvent createFinishedEvent(DefaultBuildBuildDescriptor descriptor, RunBuildBuildOperationType.Details details, OperationFinishEvent finishEvent) {
        return new DefaultOperationFinishedProgressEvent(finishEvent.getEndTime(), descriptor, toOperationResultWithProblems(finishEvent));
    }

    private AbstractOperationResult toOperationResultWithProblems(OperationFinishEvent result) {
        Throwable failure = result.getFailure();
        long startTime = result.getStartTime();
        long endTime = result.getEndTime();
        if (failure != null) {
            List<Problem> problems = new ArrayList<>(problemConsumer.getProblemsForThrowable().values());
            List<InternalBasicProblemDetailsVersion3> protocolProblems = problems.stream().map(ProblemsProgressEventConsumer::createDefaultProblemDetails).collect(Collectors.toList());
            return new DefaultFailureWithProblemResult(startTime, endTime, Collections.singletonList(DefaultFailure.fromThrowable(failure)), protocolProblems);
        }
        return new DefaultSuccessResult(startTime, endTime);
    }
}
