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

package org.gradle.api.internal.artifacts;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import groovy.lang.MissingPropertyException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.internal.typeconversion.TypeInfo;
import org.gradle.internal.typeconversion.TypedNotationConverter;

import java.util.Map;
import java.util.Set;

public class ExcludeRuleNotationConverter extends TypedNotationConverter<Map<String, String>, ExcludeRule> {

    private static final ImmutableSet<String> ALLOWED_PROPERTIES = ImmutableSet.of(ExcludeRule.GROUP_KEY, ExcludeRule.MODULE_KEY);

    private static final NotationParser<Object, ExcludeRule> PARSER =
        NotationParserBuilder.toType(ExcludeRule.class).converter(new ExcludeRuleNotationConverter()).toComposite();

    public static NotationParser<Object, ExcludeRule> parser() {
        return PARSER;
    }

    public ExcludeRuleNotationConverter() {
        super(new TypeInfo<Map<String, String>>(Map.class));
    }

    @Override
    public void describe(DiagnosticsVisitor visitor) {
        visitor.candidate("Maps with 'group' and/or 'module'").example("[group: 'com.google.collections', module: 'google-collections']");
    }

    @Override
    protected ExcludeRule parseType(Map<String, String> notation) {
        String group = notation.get(ExcludeRule.GROUP_KEY);
        String module = notation.get(ExcludeRule.MODULE_KEY);
        if (group == null && module == null) {
            throw new InvalidUserDataException("Dependency exclude rule requires 'group' and/or 'module' specified. For example: [group: 'com.google.collections']");
        }
        Set<String> invalidKeys = Sets.difference(notation.keySet(), ALLOWED_PROPERTIES);
        if (!invalidKeys.isEmpty()) {
            throw new MissingPropertyException(invalidKeys.iterator().next(), ExcludeRule.class);
        }
        return new DefaultExcludeRule(group, module);
    }
}
