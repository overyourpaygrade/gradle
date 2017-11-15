/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.internal.typeconversion;

import com.google.common.collect.ImmutableSet;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.util.GUtil;

import java.util.Collection;
import java.util.Set;

/**
 * Flattens or collectionizes input and passes the input notations to the delegates. Returns a set.
 */
public class FlatteningNotationParser<T> implements NotationParser<Object, Set<T>> {

    private final NotationParser<Object, T> delegate;

    public FlatteningNotationParser(NotationParser<Object, T> delegate) {
        assert delegate != null : "delegate cannot be null";
        this.delegate = delegate;
    }

    @Override
    public void describe(DiagnosticsVisitor visitor) {
        delegate.describe(visitor);
        visitor.candidate("Collections or arrays of any other supported format. Nested collections/arrays will be flattened.");
    }

    public Set<T> parseNotation(Object notation) {
        Collection notations = GUtil.collectionize(notation);
        if (notations.isEmpty()) {
            return ImmutableSet.of();
        }
        if (notations.size() == 1) {
            return ImmutableSet.of(delegate.parseNotation(notations.iterator().next()));
        }
        ImmutableSet.Builder<T> builder = ImmutableSet.builder();
        for (Object n : notations) {
            builder.add(delegate.parseNotation(n));
        }
        return builder.build();
    }
}
