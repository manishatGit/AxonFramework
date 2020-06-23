/*
 * Copyright (c) 2010-2020. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling;

import org.axonframework.eventhandling.replay.GenericResetMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.AnnotatedHandlerInspector;
import org.axonframework.messaging.annotation.ClasspathHandlerDefinition;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.ParameterResolverFactory;

import java.util.Optional;

/**
 * Adapter that turns any bean with {@link EventHandler} annotated methods into an {@link EventMessageHandler}.
 *
 * @author Allard Buijze
 * @see EventMessageHandler
 * @since 0.1
 */
public class AnnotationEventHandlerAdapter implements EventMessageHandler {

    private final AnnotatedHandlerInspector<Object> inspector;
    private final Class<?> listenerType;
    private final Object annotatedEventListener;

    /**
     * Wraps the given {@code annotatedEventListener}, allowing it to be subscribed to an Event Bus.
     *
     * @param annotatedEventListener the annotated event listener
     */
    public AnnotationEventHandlerAdapter(Object annotatedEventListener) {
        this(annotatedEventListener, ClasspathParameterResolverFactory.forClass(annotatedEventListener.getClass()));
    }

    /**
     * Wraps the given {@code annotatedEventListener}, allowing it to be subscribed to an Event Bus. The given {@code
     * parameterResolverFactory} is used to resolve parameter values for handler methods.
     *
     * @param annotatedEventListener   the annotated event listener
     * @param parameterResolverFactory the strategy for resolving handler method parameter values
     */
    public AnnotationEventHandlerAdapter(Object annotatedEventListener,
                                         ParameterResolverFactory parameterResolverFactory) {
        this(annotatedEventListener,
             parameterResolverFactory,
             ClasspathHandlerDefinition.forClass(annotatedEventListener.getClass()));
    }

    /**
     * Wraps the given {@code annotatedEventListener}, allowing it to be subscribed to an Event Bus. The given {@code
     * parameterResolverFactory} is used to resolve parameter values for handler methods. Handler definition is used to
     * create concrete handlers.
     *
     * @param annotatedEventListener   the annotated event listener
     * @param parameterResolverFactory the strategy for resolving handler method parameter values
     * @param handlerDefinition        the handler definition used to create concrete handlers
     */
    public AnnotationEventHandlerAdapter(Object annotatedEventListener,
                                         ParameterResolverFactory parameterResolverFactory,
                                         HandlerDefinition handlerDefinition) {
        this.annotatedEventListener = annotatedEventListener;
        this.listenerType = annotatedEventListener.getClass();
        this.inspector = AnnotatedHandlerInspector.inspectType(annotatedEventListener.getClass(),
                                                               parameterResolverFactory,
                                                               handlerDefinition);
    }

    @Override
    public Object handle(EventMessage<?> event) throws Exception {
        return findAndHandle(event);
    }

    @Override
    public boolean canHandle(EventMessage<?> event) {
        return inspector.getHandlers(listenerType)
                        .anyMatch(h -> h.canHandle(event));
    }

    @Override
    public boolean canHandleType(Class<?> payloadType) {
        return inspector.getHandlers(listenerType)
                        .anyMatch(handler -> handler.canHandleType(payloadType));
    }

    @Override
    public Class<?> getTargetType() {
        return listenerType;
    }

    @Override
    public void prepareReset() {
        prepareReset(null);
    }

    @Override
    public <R> void prepareReset(R resetContext) {
        try {
            findAndHandle(GenericResetMessage.asResetMessage(resetContext));
        } catch (Exception e) {
            throw new ResetNotSupportedException("An Error occurred while notifying handlers of the reset", e);
        }
    }

    private Object findAndHandle(Message<?> message) throws Exception {
        Optional<MessageHandlingMember<? super Object>> optionalHandler = inspector.getHandlers(listenerType)
                                                                                   .filter(h -> h.canHandle(message))
                                                                                   .findFirst();
        return optionalHandler.isPresent() ? optionalHandler.get().handle(message, annotatedEventListener) : null;
    }

    @Override
    public boolean supportsReset() {
        return true;
    }
}
