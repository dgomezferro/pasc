/**
 * Copyright (c) 2011 Yahoo! Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. See accompanying LICENSE file.
 */

package com.yahoo.pasc;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rits.cloning.Cloner;
import com.yahoo.pasc.exceptions.ControlFlowException;
import com.yahoo.pasc.exceptions.GuardException;
import com.yahoo.pasc.exceptions.InputMessageException;
import com.yahoo.pasc.exceptions.MessagesGenerationException;
import com.yahoo.pasc.exceptions.VariableCorruptionException;
import com.yahoo.pasc.generation.Encapsulator;
import com.yahoo.pasc.generation.EncapsulatorGenerator;
import com.yahoo.pasc.generation.LightEncapsulatorGenerator;

/**
 * The runtime is in charge of isolating and detecting state corruptions or failures.
 * 
 * There should be one runtime per application. After being configured, the runtime can handle messages and
 * produce outputs, based on the user's protocol.
 *
 * @param <S> state class used by this application
 */
public final class PascRuntime<S extends ProcessState> {
    
    private static final Logger LOG = LoggerFactory.getLogger(PascRuntime.class);

    private Map<Class<? extends Message>, MessageHandler<Message, S, ?>> handlers = 
        new HashMap<Class<? extends Message>, MessageHandler<Message, S, ?>>();

    private S state;
    private S replica;

    private FailureHandler failureHandler = new CrashFailureHandler();

    private static Cloner cloner = new Cloner();

    /**
     * Helper method to deep clone an object.
     * 
     * If the object implements CloneableDeep, this method will use that interface.
     * 
     * @param object Object to be cloned
     * @return a fresh copy of the received object
     */
    @SuppressWarnings("unchecked")
    public static <T> T clone(T object) {
        if (object instanceof ReadOnly) {
            return object;
        }
        if (object instanceof CloneableDeep) {
            return (T) ((CloneableDeep<T>)object).cloneDeep();
        }
        if (object == null) return null;
        return cloner.deepClone(object);
    }

    /**
     * Helper method to compare two objects for equality.
     * 
     * If the objects implement EqualsDeep this method will use that interface.
     * 
     * @param o1 first object to compare
     * @param o2 second object to compare
     * @return true if both objects are equal
     */
    @SuppressWarnings("unchecked")
    public static <T> boolean compare(T o1, T o2) {
        if (o1 == null || o2 == null) return o1 == o2;
        if (o1 instanceof EqualsDeep) {
            return ((EqualsDeep<T>)o1).equalsDeep(o2);
        }
        
        return EqualsBuilder.reflectionEquals(o1, o2, false);
    }

    /**
     * Stablishes the state object for this application.
     * 
     * @param state The state used by this application
     */
    public void setState(S state) {
        this.state = state;
        this.replica = clone(state);
        this.lightEncapsulatorGenerator = new LightEncapsulatorGenerator(state);
        this.encapsulatorGenerator = new EncapsulatorGenerator(state);

        stateEncapsulator = generateEncapsulator(state, replica);
        replicaEncapsulator = generateLightEncapsulator(replica, state);
    }

    /**
     * Registers a new handler for the specified message type.
     * 
     * @param messageType Type of message the handler will handle
     * @param handler Implementation of the handler
     */
    @SuppressWarnings("unchecked")
    public void addHandler(Class<? extends Message> messageType, 
            MessageHandler<? extends Message, S, ?> handler) 
    {
        handlers.put(messageType, (MessageHandler<Message, S, ?>) handler);
    }

    private Encapsulator stateEncapsulator, replicaEncapsulator;
    private EncapsulatorGenerator encapsulatorGenerator;
    private LightEncapsulatorGenerator lightEncapsulatorGenerator;
    private final boolean protection;
    private final boolean protectionReplica;

    /**
     * Create a new runtime with protection against failures
     */
    public PascRuntime() {
        this(true);
    }

    /**
     * Create a new runtime
     * 
     * @param protection Whether to use protection against corruptions/failures or not
     */
    public PascRuntime(boolean protection) {
        this.protectionReplica = this.protection = protection;
    }

    private final List<Message> emptyMessages = Collections.emptyList();

    /**
     * Handle a new message and produce output messages.
     * 
     * This method will dispatch the received message to the appropriate handler, and return the
     * output messages generated by the handler.
     * 
     * @param receivedMessage Received message requiring handling
     * @return List of output messages generated by the handler 
     */
    public List<Message> handleMessage(Message receivedMessage) {
        ControlObject control = new ControlObject();
        MessageHandler<Message, S, ?> handler = handlers.get(receivedMessage.getClass());
        if (handler == null) {
            LOG.warn("No handler found for message {} ", receivedMessage);
            return emptyMessages;
        } else if (!handler.guardPredicate(receivedMessage)) {
            LOG.warn("Handler's guard predicate doesn't hold: {} {}", handler, receivedMessage);
            return emptyMessages;
        }
        try {
            if (protection != protectionReplica) {
                throw new VariableCorruptionException("protection", protection, protectionReplica);
            }
            if (protection) {
                return invoke(handler, receivedMessage, control);
            } else {
                return unsafeInvoke(handler, receivedMessage);
            }
        } catch (Exception e) {
            failureHandler.handleFailure(e);
            return emptyMessages;
        }
    }

    private <D> List<Message> unsafeInvoke(final MessageHandler<Message, S, D> handler, 
            final Message receivedMessage) {
          receivedMessage.verify();
          List<Message> result;
          synchronized (this) {
              List<D> descriptors = handler.processMessage(receivedMessage, state);
              result = handler.getOutputMessages(state, descriptors);
          }
          if (result != null) {
              for (Message m : result) {
                  if (m != null)
                      m.storeReplica(m);
              }
          }
          return result;
    }

    private Encapsulator generateEncapsulator(S state, S replica) {
        return encapsulatorGenerator.getEncapsulator(state, replica);
    }

    private Encapsulator generateLightEncapsulator(S state, S replica) {
        return lightEncapsulatorGenerator.getLightEncapsulator(state, replica);
    }
    
    private class Result<D> {
        public MessageHandler<Message, S, D> handler; 
        public Message receivedMessage;
        public Message clonedMessage;
        public List<Message> responses;
        public List<Message> replicas;
    }
    
    private class ControlObject {
        ControlFlow cfs, cfr;
        ControlFlow cf_s, cf_r;
        ControlFlow cf__s, cf__r;
        
        public ControlObject() {
            cfs = cfr = cf_s = cf_r = cf__s = cf__r = ControlFlow.RESET;
        }
    }

    private enum ControlFlow {
        SET, RESET
    }

    private <D> void criticalSection(Result<D> result, ControlObject control) {
        ControlFlow cfl, cfl_;
        ControlFlow cf_l, cf_l_;
        
        MessageHandler<Message, S, D> handler = result.handler;
        Message receivedMessage = result.receivedMessage;
        Message clonedMessage = result.clonedMessage;

        stateEncapsulator.reset();
        replicaEncapsulator.reset();

        // compute N
        stateEncapsulator.setCheckState(true);
        @SuppressWarnings("unchecked")
        List<D> descriptors = handler.processMessage(receivedMessage, (S) stateEncapsulator);
        
        // check control flow
        cfl = cfl_ = ControlFlow.SET;
        if (control.cfs != control.cfr || control.cfs != ControlFlow.RESET) {
            throw new ControlFlowException("cf =/= cfr or cf =/= RESET");
        }
        control.cfs = control.cfr = ControlFlow.SET;

        // update R
        @SuppressWarnings("unchecked")
        List<D> replicaDescriptors = handler.processMessage(clonedMessage, (S) replicaEncapsulator);

        if (control.cf_s != control.cf_r || control.cf_s != ControlFlow.RESET) {
            throw new ControlFlowException("cf_s =/= cf_r or cf_s =/= RESET");
        }
        control.cf_s = control.cf_r = ControlFlow.SET;
        cf_l = cf_l_ = ControlFlow.SET;

        // apply changes to process state
        stateEncapsulator.applyModifications(false, replicaEncapsulator);

        // check control flow
        if (cfl != cfl_ || cfl != ControlFlow.SET) {
            throw new ControlFlowException("cfl =/= cfl_ or cfl =/= SET");
        }
        if (cf_l != cf_l_ || cf_l != ControlFlow.SET) {
            throw new ControlFlowException("cf_l =/= cf_l_ or cf_l =/= SET");
        }
        if (control.cf__s != control.cf__r || control.cf__s != ControlFlow.RESET) {
            throw new ControlFlowException("cf__s =/= cf__r or cf__s =/= RESET");
        }
        control.cf__s = control.cf__r = ControlFlow.SET;
        
        if (descriptors != null && replicaDescriptors != null) {
            if (descriptors.size() != replicaDescriptors.size()) {
                throw new MessagesGenerationException(descriptors, replicaDescriptors);
            }
            Iterator<?> it = descriptors.iterator();
            Iterator<?> itr = replicaDescriptors.iterator();
            while(it.hasNext()) {
                if (!compare(it.next(), itr.next())) {
                    throw new MessagesGenerationException(descriptors, replicaDescriptors);
                }
            }
        } else if (descriptors != null || replicaDescriptors != null) {
            throw new MessagesGenerationException(descriptors, replicaDescriptors);
        }

        // generate messages
        result.responses = handler.getOutputMessages(state, descriptors);
        result.replicas = handler.getOutputMessages(replica, replicaDescriptors);
    }

    private <D> List<Message> invoke(final MessageHandler<Message, S, D> handler, 
            final Message receivedMessage, ControlObject control) {

        List<Message> responses;
        List<Message> replicas;
        Message clonedMessage;

        // Clone and verify input message
        Message cloned = receivedMessage.getCloned();
        if (cloned != null) {
            clonedMessage = receivedMessage.getCloned();
        } else {
            clonedMessage = clone(receivedMessage);
            if (!receivedMessage.verify())
                return emptyMessages;
        }
        
        Result<D> result = new Result<D>();
        result.receivedMessage = receivedMessage;
        result.clonedMessage = clonedMessage;
        result.handler = handler;

        criticalSection(result, control);
        
        // verify input message again
        if (!compare(receivedMessage, result.clonedMessage)) {
            throw new InputMessageException("Not equal", receivedMessage, result.clonedMessage);
        }
        if (!receivedMessage.verify()) {
            throw new InputMessageException("Verification failed", receivedMessage, result.clonedMessage);
        }

        // verify guard
        if (!handler.guardPredicate(receivedMessage)) {
            throw new GuardException("Guard doesn't hold", handler, receivedMessage);
        }
        
        responses = result.responses;
        replicas = result.replicas;

        if (responses == null || replicas == null) {
            return emptyMessages;
        }
        if (responses.size() != replicas.size()) {
            return emptyMessages;
        }

        // set CRCs
        Iterator<Message> it1 = responses.iterator();
        Iterator<Message> it2 = replicas.iterator();
        while (it1.hasNext()) {
            Message response = it1.next();
            response.storeReplica(it2.next());
        }

        return responses;
    }
    
    S getState() {
        return state;
    }
    
    S getReplica() {
        return replica;
    }

    public FailureHandler getFailureHandler() {
        return failureHandler;
    }

    public void setFailureHandler(FailureHandler failureHandler) {
        this.failureHandler = failureHandler;
    }
}