/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.kotlin.dsl.support

import com.google.common.annotations.VisibleForTesting
import org.gradle.initialization.EnvironmentChangeTracker

/**
 * Marker interface for the scope where it is safe to configure and run the Kotlin Compiler.
 */
sealed interface KotlinCompilerInitContext


private object KotlinCompilerInitContextImpl : KotlinCompilerInitContext


fun <T> EnvironmentChangeTracker.withKotlinCompilerInitContext(action: KotlinCompilerInitContext.() -> T): T = withTrackingSystemPropertyChanges {
    KotlinCompilerInitContextImpl.run(action)
}


@VisibleForTesting
fun <T> withKotlinCompilerInitContextForTesting(action: KotlinCompilerInitContext.() -> T): T = KotlinCompilerInitContextImpl.run(action)
