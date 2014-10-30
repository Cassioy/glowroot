/*
 * Copyright 2011-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.collector;

import com.google.common.io.CharSource;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Interface for storing traces.
 * 
 * @author Trask Stalnaker
 * @since 0.5
 */
public interface TraceRepository {

    void store(Trace trace, CharSource entries, @Nullable CharSource profile,
            @Nullable CharSource outlierProfile);
}