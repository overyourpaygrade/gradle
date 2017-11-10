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
package org.gradle.api.internal.file;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.util.GUtil;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public class UnionFileCollection extends CompositeFileCollection {
    private ImmutableSet<FileCollection> source;

    public UnionFileCollection(FileCollection... source) {
        this(Arrays.asList(source));
    }

    public UnionFileCollection(Iterable<? extends FileCollection> source) {
        this.source = ImmutableSet.copyOf(source);
    }

    public String getDisplayName() {
        return "file collection";
    }

    public Set<FileCollection> getSources() {
        return source;
    }

    @Override
    public FileCollection add(FileCollection collection) {
        source = ImmutableSet.<FileCollection>builder().addAll(source).add(collection).build();
        return this;
    }

    @Override
    public void visitContents(FileCollectionResolveContext context) {
        context.add(source);
    }
}
