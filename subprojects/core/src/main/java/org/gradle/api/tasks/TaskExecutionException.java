/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Task;
import org.gradle.api.problems.internal.DefaultProblems;
import org.gradle.api.problems.internal.Problem;
import org.gradle.api.problems.internal.ProblemAwareFailure;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.exceptions.DefaultMultiCauseException;

import java.util.Collection;
import java.util.List;

/**
 * <p>A {@code TaskExecutionException} is thrown when a task fails to execute successfully.</p>
 */
@Contextual
public class TaskExecutionException extends DefaultMultiCauseException implements ProblemAwareFailure {
    private final Task task;
    private final Collection<Problem> problems;

    public TaskExecutionException(Task task, Throwable cause) {
        super(String.format("Execution failed for %s.", task), cause);
        this.task = task;
        System.err.println("yProblems ");
        List<Problem> problems = DefaultProblems.problems.get();
        if (problems != null) {
            System.err.println(problems);
            this.problems = ImmutableList.copyOf(problems);
        } else {
            System.err.println("yNo problems");
            this.problems = ImmutableList.of();
        }
    }

    public Task getTask() {
        return task;
    }

    @Override
    public Collection<Problem> getProblems() {
        return this.problems;
    }
}
