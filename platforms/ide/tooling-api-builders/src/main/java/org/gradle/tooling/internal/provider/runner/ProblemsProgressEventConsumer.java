/*
 * Copyright 2023 the original author or authors.
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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.io.Files;
import org.gradle.api.NonNullApi;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.internal.AdditionalData;
import org.gradle.api.problems.internal.DefaultProblemProgressDetails;
import org.gradle.api.problems.internal.DeprecationData;
import org.gradle.api.problems.internal.DocLink;
import org.gradle.api.problems.internal.FileLocation;
import org.gradle.api.problems.internal.GeneralData;
import org.gradle.api.problems.internal.InternalProblemBuilder;
import org.gradle.api.problems.internal.LineInFileLocation;
import org.gradle.api.problems.internal.OffsetInFileLocation;
import org.gradle.api.problems.internal.PluginIdLocation;
import org.gradle.api.problems.internal.Problem;
import org.gradle.api.problems.internal.ProblemAwareFailure;
import org.gradle.api.problems.internal.ProblemDefinition;
import org.gradle.api.problems.internal.ProblemLocation;
import org.gradle.api.problems.internal.TaskPathLocation;
import org.gradle.api.problems.internal.TypeValidationData;
import org.gradle.internal.build.event.types.DefaultAdditionalData;
import org.gradle.internal.build.event.types.DefaultContextualLabel;
import org.gradle.internal.build.event.types.DefaultDetails;
import org.gradle.internal.build.event.types.DefaultDocumentationLink;
import org.gradle.internal.build.event.types.DefaultFailure;
import org.gradle.internal.build.event.types.DefaultProblemDefinition;
import org.gradle.internal.build.event.types.DefaultProblemDescriptor;
import org.gradle.internal.build.event.types.DefaultProblemDetails;
import org.gradle.internal.build.event.types.DefaultProblemEvent;
import org.gradle.internal.build.event.types.DefaultProblemGroup;
import org.gradle.internal.build.event.types.DefaultProblemId;
import org.gradle.internal.build.event.types.DefaultSeverity;
import org.gradle.internal.build.event.types.DefaultSolution;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.tooling.internal.protocol.InternalFailure;
import org.gradle.tooling.internal.protocol.InternalProblemDefinition;
import org.gradle.tooling.internal.protocol.InternalProblemEventVersion2;
import org.gradle.tooling.internal.protocol.InternalProblemGroup;
import org.gradle.tooling.internal.protocol.InternalProblemId;
import org.gradle.tooling.internal.protocol.events.InternalProblemDescriptor;
import org.gradle.tooling.internal.protocol.problem.InternalAdditionalData;
import org.gradle.tooling.internal.protocol.problem.InternalContextualLabel;
import org.gradle.tooling.internal.protocol.problem.InternalDetails;
import org.gradle.tooling.internal.protocol.problem.InternalDocumentationLink;
import org.gradle.tooling.internal.protocol.problem.InternalLocation;
import org.gradle.tooling.internal.protocol.problem.InternalSeverity;
import org.gradle.tooling.internal.protocol.problem.InternalSolution;

import javax.annotation.Nullable;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Optional.empty;
import static java.util.stream.Collectors.toMap;

@NonNullApi
public class ProblemsProgressEventConsumer extends ClientForwardingBuildOperationListener {

    private static final InternalSeverity ADVICE = new DefaultSeverity(0);
    private static final InternalSeverity WARNING = new DefaultSeverity(1);
    private static final InternalSeverity ERROR = new DefaultSeverity(2);

    private final Supplier<OperationIdentifier> operationIdentifierSupplier;
    private final AggregatingProblemConsumer aggregator;

    private final Multimap<Throwable, Problem> problemsForThrowables = Multimaps.synchronizedMultimap(HashMultimap.<Throwable, Problem>create());


    ProblemsProgressEventConsumer(ProgressEventConsumer progressEventConsumer, Supplier<OperationIdentifier> operationIdentifierSupplier, AggregatingProblemConsumer aggregator) {
        super(progressEventConsumer);
        this.operationIdentifierSupplier = operationIdentifierSupplier;
        this.aggregator = aggregator;
    }

    @Override
    public void progress(OperationIdentifier buildOperationId, OperationProgressEvent progressEvent) {
        Object details = progressEvent.getDetails();
        createProblemEvent(buildOperationId, details)
            .ifPresent(aggregator::emit);
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent result) {
        OperationIdentifier parentId = buildOperation.getParentId();
        if (parentId == null) { // root build operation
            try {
                Throwable failure = result.getFailure();
                if (failure != null) { // root build operation failed; assumption; this is the build failed exception
                    StringWriter sw = new StringWriter();
                    failure.printStackTrace(new PrintWriter(sw));
                    Files.asCharSink(new java.io.File("/tmp/out"), java.nio.charset.StandardCharsets.UTF_8)
                        .write("buildOperation: " + sw);


                    // I can send an extra progress event here
                    Throwable failureCauseCause = failure.getCause().getCause();

                    Collection<Problem> buildFailureProblems = new ArrayList<>(problemsForThrowables.get(failureCauseCause));

                    if (failureCauseCause instanceof ProblemAwareFailure) {
                        ProblemAwareFailure paf = (ProblemAwareFailure) failureCauseCause;
                        buildFailureProblems.addAll(paf.getProblems());
                    }

                    InternalFailure fcc = DefaultFailure.fromThrowable(failure).getCauses().get(0).getCauses().get(0);



                    final String cccontextualLabel = "XXX contextual label \n" +
                        "fcc message " + fcc.getMessage() + "\n" +
                        "failureCauseCause " + failureCauseCause.getMessage() + "\n" +
                        "problemsForThrowables.size(): " + problemsForThrowables.size() + "\n" +
                        "build failure problems: " + buildFailureProblems;

                    aggregator.emit(createProblemEvent(buildOperation.getId(), new Problem() {
                        @Override
                        public ProblemDefinition getDefinition() {
                            return new org.gradle.api.problems.internal.DefaultProblemDefinition(
                                new org.gradle.api.problems.internal.DefaultProblemId(
                                    "id", "displayName",
                                    new org.gradle.api.problems.internal.DefaultProblemGroup(
                                        "name",
                                        "displayName",
                                        null
                                    )
                                ),
                                Severity.ERROR,
                                null);
                        }

                        @Nullable
                        @Override
                        public String getContextualLabel() {
                            return cccontextualLabel;

                        }

                        @Override
                        public List<String> getSolutions() {
                            return new ArrayList<>();
                        }

                        @Nullable
                        @Override
                        public String getDetails() {
                            return "details ";
                        }

                        @Override
                        public List<ProblemLocation> getLocations() {
                            return new ArrayList<>();
                        }

                        @Nullable
                        @Override
                        public RuntimeException getException() {
                            return null;
                        }

                        @Nullable
                        @Override
                        public AdditionalData getAdditionalData() {
                            return new GeneralData() {
                                @Override
                                public Map<String, String> getAsMap() {
                                    return new HashMap<>();
                                }
                            };
                        }

                        @Override
                        public InternalProblemBuilder toBuilder() {
                            return null;
                        }
                    }));
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        super.finished(buildOperation, result);
    }

    private Optional<InternalProblemEventVersion2> createProblemEvent(OperationIdentifier buildOperationId, @Nullable Object details) {
        if (details instanceof DefaultProblemProgressDetails) {
            Problem problem = ((DefaultProblemProgressDetails) details).getProblem();
            Throwable exception = problem.getException();
            if (exception != null) {
                problemsForThrowables.put(exception, problem);
            }
            return Optional.of(createProblemEvent(buildOperationId, problem));
        }
        return empty();
    }

    private InternalProblemEventVersion2 createProblemEvent(OperationIdentifier buildOperationId, Problem problem) {
        return new DefaultProblemEvent(
            createDefaultProblemDescriptor(buildOperationId),
            new DefaultProblemDetails(
                toInternalDefinition(problem.getDefinition()),
                toInternalDetails(problem.getDetails()),
                toInternalContextualLabel(problem.getContextualLabel()),
                toInternalLocations(problem.getLocations()),
                toInternalSolutions(problem.getSolutions()),
                toInternalAdditionalData(problem.getAdditionalData()),
                toInternalFailure(problem.getException())
            )
        );
    }

    @Nullable
    private static InternalFailure toInternalFailure(@Nullable RuntimeException ex) {
        if (ex == null) {
            return null;
        }
        return DefaultFailure.fromThrowable(ex);
    }

    private InternalProblemDescriptor createDefaultProblemDescriptor(OperationIdentifier parentBuildOperationId) {
        return new DefaultProblemDescriptor(
            operationIdentifierSupplier.get(),
            parentBuildOperationId);
    }

    private static InternalProblemDefinition toInternalDefinition(ProblemDefinition definition) {
        return new DefaultProblemDefinition(
            toInternalId(definition.getId()),
            toInternalSeverity(definition.getSeverity()),
            toInternalDocumentationLink(definition.getDocumentationLink())
        );
    }

    private static InternalProblemId toInternalId(ProblemId problemId) {
        return new DefaultProblemId(problemId.getName(), problemId.getDisplayName(), toInternalGroup(problemId.getGroup()));
    }

    private static InternalProblemGroup toInternalGroup(ProblemGroup group) {
        return new DefaultProblemGroup(group.getName(), group.getDisplayName(), group.getParent() == null ? null : toInternalGroup(group.getParent()));
    }

    private static @Nullable InternalContextualLabel toInternalContextualLabel(@Nullable String contextualLabel) {
        return contextualLabel == null ? null : new DefaultContextualLabel(contextualLabel);
    }

    private static @Nullable InternalDetails toInternalDetails(@Nullable String details) {
        return details == null ? null : new DefaultDetails(details);
    }

    private static InternalSeverity toInternalSeverity(Severity severity) {
        switch (severity) {
            case ADVICE:
                return ADVICE;
            case WARNING:
                return WARNING;
            case ERROR:
                return ERROR;
            default:
                throw new RuntimeException("No mapping defined for severity level " + severity);
        }
    }

    private static List<InternalLocation> toInternalLocations(List<ProblemLocation> locations) {
        return locations.stream().map(location -> {
            if (location instanceof LineInFileLocation) {
                LineInFileLocation fileLocation = (LineInFileLocation) location;
                return new org.gradle.internal.build.event.types.DefaultLineInFileLocation(fileLocation.getPath(), fileLocation.getLine(), fileLocation.getColumn(), fileLocation.getLength());
            } else if (location instanceof OffsetInFileLocation) {
                OffsetInFileLocation fileLocation = (OffsetInFileLocation) location;
                return new org.gradle.internal.build.event.types.DefaultOffsetInFileLocation(fileLocation.getPath(), fileLocation.getOffset(), fileLocation.getLength());
            } else if (location instanceof FileLocation) { // generic class must be after the subclasses in the if-elseif chain.
                FileLocation fileLocation = (FileLocation) location;
                return new org.gradle.internal.build.event.types.DefaultFileLocation(fileLocation.getPath());
            } else if (location instanceof PluginIdLocation) {
                PluginIdLocation pluginLocation = (PluginIdLocation) location;
                return new org.gradle.internal.build.event.types.DefaultPluginIdLocation(pluginLocation.getPluginId());
            } else if (location instanceof TaskPathLocation) {
                TaskPathLocation taskLocation = (TaskPathLocation) location;
                return new org.gradle.internal.build.event.types.DefaultTaskPathLocation(taskLocation.getBuildTreePath());
            } else {
                throw new RuntimeException("No mapping defined for " + location.getClass().getName());
            }
        }).collect(toImmutableList());
    }

    @Nullable
    private static InternalDocumentationLink toInternalDocumentationLink(@Nullable DocLink link) {
        return (link == null || link.getUrl() == null) ? null : new DefaultDocumentationLink(link.getUrl());
    }

    private static List<InternalSolution> toInternalSolutions(List<String> solutions) {
        return solutions.stream()
            .map(DefaultSolution::new)
            .collect(toImmutableList());
    }


    @SuppressWarnings("unchecked")
    private static InternalAdditionalData toInternalAdditionalData(@Nullable AdditionalData additionalData) {
        if (additionalData instanceof DeprecationData) {
            // For now, we only expose deprecation data to the tooling API with generic additional data
            DeprecationData data = (DeprecationData) additionalData;
            return new DefaultAdditionalData(ImmutableMap.of("type", data.getType().name()));
        } else if (additionalData instanceof TypeValidationData) {
            TypeValidationData data = (TypeValidationData) additionalData;
            ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
            Optional.ofNullable(data.getPluginId()).ifPresent(pluginId -> builder.put("pluginId", pluginId));
            Optional.ofNullable(data.getPropertyName()).ifPresent(propertyName -> builder.put("propertyName", propertyName));
            Optional.ofNullable(data.getParentPropertyName()).ifPresent(parentPropertyName -> builder.put("parentPropertyName", parentPropertyName));
            Optional.ofNullable(data.getTypeName()).ifPresent(typeName -> builder.put("typeName", typeName));
            return new DefaultAdditionalData(builder.build());
        } else if (additionalData instanceof GeneralData) {
            GeneralData data = (GeneralData) additionalData;
            return new DefaultAdditionalData(
                data.getAsMap().entrySet().stream()
                    .filter(entry -> isSupportedType(entry.getValue()))
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue))
            );
        } else {
            return new DefaultAdditionalData(Collections.emptyMap());
        }
    }

    private static boolean isSupportedType(Object type) {
        return type instanceof String;
    }
}
