/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.tasks;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import groovy.lang.Closure;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.internal.NamedDomainObjectContainerConfigureDelegate;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.TaskReference;
import org.gradle.initialization.ProjectAccessListener;
import org.gradle.internal.Transformers;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.internal.core.NamedEntityInstantiator;
import org.gradle.util.ConfigureUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

public class DefaultTaskContainer extends DefaultTaskCollection<Task> implements TaskContainerInternal {
    private final ITaskFactory taskFactory;
    private final ProjectAccessListener projectAccessListener;
    private final NamedEntityInstantiator<Task> instantiator;
    private final Map<String, PlaceHolder> placeHolders = Maps.newHashMap();

    public DefaultTaskContainer(ProjectInternal project, Instantiator instantiator, ITaskFactory taskFactory, ProjectAccessListener projectAccessListener) {
        super(Task.class, instantiator, project);
        this.taskFactory = taskFactory;
        this.projectAccessListener = projectAccessListener;
        this.instantiator = new TaskInstantiator(taskFactory);
    }

    public Task create(Map<String, ?> options) {
        Map<String, ?> factoryOptions = options;
        boolean replace = false;
        if (options.containsKey(Task.TASK_OVERWRITE)) {
            factoryOptions = new HashMap<String, Object>(options);
            Object replaceStr = factoryOptions.remove(Task.TASK_OVERWRITE);
            replace = "true".equals(replaceStr.toString());
        }

        Task task = taskFactory.createTask(factoryOptions);
        return addTask(task, replace);
    }

    private <T extends Task> T addTask(T task, boolean replaceExisting) {
        String name = task.getName();

        placeHolders.remove(name);

        Task existing = findByNameWithoutRules(name);
        if (existing != null) {
            if (replaceExisting) {
                remove(existing);
            } else {
                throw new InvalidUserDataException(String.format(
                    "Cannot add %s as a task with that name already exists.", task));
            }
        }

        add(task);

        return task;
    }

    public <U extends Task> U maybeCreate(String name, Class<U> type) throws InvalidUserDataException {
        Task existing = findByName(name);
        if (existing != null) {
            return Transformers.cast(type).transform(existing);
        }
        return create(name, type);
    }

    public Task create(Map<String, ?> options, Closure configureClosure) throws InvalidUserDataException {
        return create(options).configure(configureClosure);
    }

    public <T extends Task> T create(String name, Class<T> type) {
        T task = instantiator.create(name, type);
        return addTask(task, false);
    }

    public Task create(String name) {
        return create(name, DefaultTask.class);
    }

    public Task create(String name, Action<? super Task> configureAction) throws InvalidUserDataException {
        Task task = create(name);
        configureAction.execute(task);
        return task;
    }

    public Task maybeCreate(String name) {
        Task task = findByName(name);
        if (task != null) {
            return task;
        }
        return create(name);
    }

    public Task replace(String name) {
        return replace(name, DefaultTask.class);
    }

    public Task create(String name, Closure configureClosure) {
        return create(name).configure(configureClosure);
    }

    public <T extends Task> T create(String name, Class<T> type, Action<? super T> configuration) throws InvalidUserDataException {
        T task = create(name, type);
        configuration.execute(task);
        return task;
    }

    public <T extends Task> T replace(String name, Class<T> type) {
        T task = instantiator.create(name, type);
        return addTask(task, true);
    }

    public Task findByPath(String path) {
        if (Strings.isNullOrEmpty(path)) {
            throw new InvalidUserDataException("A path must be specified!");
        }
        if (!path.contains(Project.PATH_SEPARATOR)) {
            return findByName(path);
        }

        String projectPath = StringUtils.substringBeforeLast(path, Project.PATH_SEPARATOR);
        ProjectInternal project = this.project.findProject(Strings.isNullOrEmpty(projectPath) ? Project.PATH_SEPARATOR : projectPath);
        if (project == null) {
            return null;
        }
        projectAccessListener.beforeRequestingTaskByPath(project);

        return project.getTasks().findByName(StringUtils.substringAfterLast(path, Project.PATH_SEPARATOR));
    }

    public Task resolveTask(String path) {
        if (Strings.isNullOrEmpty(path)) {
            throw new InvalidUserDataException("A path must be specified!");
        }
        return getByPath(path);
    }

    @Override
    public Task resolveTask(TaskReference reference) {
        for (TaskReferenceResolver taskResolver : project.getServices().getAll(TaskReferenceResolver.class)) {
            Task constructed = taskResolver.constructTask(reference, this);
            if (constructed != null) {
                return constructed;
            }
        }

        throw new UnknownTaskException(String.format("Task reference '%s' could not be resolved in %s.", reference.getName(), project));
    }

    public Task getByPath(String path) throws UnknownTaskException {
        Task task = findByPath(path);
        if (task == null) {
            throw new UnknownTaskException(String.format("Task with path '%s' not found in %s.", path, project));
        }
        return task;
    }

    public TaskContainerInternal configure(Closure configureClosure) {
        return ConfigureUtil.configureSelf(configureClosure, this, new NamedDomainObjectContainerConfigureDelegate(configureClosure, this));
    }

    @Override
    public NamedEntityInstantiator<Task> getEntityInstantiator() {
        return instantiator;
    }

    public DynamicObject getTasksAsDynamicObject() {
        return getElementsAsDynamicObject();
    }

    public void realize() {
    }

    @Override
    public void discoverTasks() {
        project.fireDeferredConfiguration();
    }

    @Override
    public void prepareForExecution(Task task) {
        assert task.getProject() == project;
    }

    /**
     * @return true if this method _may_ have done some work.
     */
    private boolean maybeCreateTasks(String name) {
        PlaceHolder placeHolder = placeHolders.remove(name);
        if (placeHolder == null) {
            return false;
        } else {
            placeHolder.create(this);
            return true;
        }
    }

    public Task findByName(String name) {
        Task task = super.findByName(name);
        if (task != null) {
            return task;
        }
        if (!maybeCreateTasks(name)) {
            return null;
        }
        return super.findByNameWithoutRules(name);
    }

    public SortedSet<String> getNames() {
        SortedSet<String> names = Sets.newTreeSet(super.getNames());
        names.addAll(placeHolders.keySet());
        return names;
    }

    public <T extends TaskInternal> void addPlaceholderAction(final String placeholderName, final Class<T> taskType, final Action<? super T> configure) {
        if (!placeHolders.containsKey(placeholderName)) {
            placeHolders.put(placeholderName, new PlaceHolder<T>(placeholderName, taskType, configure));
        }
    }

    public <U extends Task> NamedDomainObjectContainer<U> containerWithType(Class<U> type) {
        throw new UnsupportedOperationException();
    }

    public Set<? extends Class<? extends Task>> getCreateableTypes() {
        return Collections.singleton(getType());
    }

    private static class TaskInstantiator implements NamedEntityInstantiator<Task> {
        private final ITaskFactory taskFactory;

        public TaskInstantiator(ITaskFactory taskFactory) {
            this.taskFactory = taskFactory;
        }

        @Override
        public <S extends Task> S create(String name, Class<S> type) {
            if (type.isAssignableFrom(TaskInternal.class)) {
                return type.cast(taskFactory.create(name, TaskInternal.class));
            }
            return type.cast(taskFactory.create(name, type.asSubclass(TaskInternal.class)));
        }
    }

    @Override
    public <S extends Task> TaskCollection<S> withType(Class<S> type) {
        return new RealizableTaskCollection<S>(type, super.withType(type));
    }

    private static class PlaceHolder<T extends TaskInternal> {
        private String name;
        private Class<T> type;
        private Action<? super T> configureAction;

        private PlaceHolder(String name, Class<T> type, Action<? super T> configureAction) {
            this.name = name;
            this.type = type;
            this.configureAction = configureAction;
        }

        public T create(DefaultTaskContainer container) {
            T task = container.taskFactory.create(name, type);
            configureAction.execute(task);
            container.add(task);
            return task;
        }
    }
}
