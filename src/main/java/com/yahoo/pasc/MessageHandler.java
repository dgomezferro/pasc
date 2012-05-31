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

import java.util.List;

/**
 * Defines a handler for a given type of message.
 * 
 * This handler will be executed by the runtime when a message of the appropriate type is received. This handler
 * might update the state, produce a list of output messages, both or neither.
 *
 * @param <M> Message type that this handler will receive
 * @param <S> User state type
 * @param <D> Intermediate message descriptors type that this handler generates
 */
public interface MessageHandler<M extends Message, S extends ProcessState, D> {
	
    /**
     * Evaluates to true when the given message has the correct type for this message handler
     * 
     * @param receivedMessage Message to be checked
     * @return true if this handler is the one that should be executed
     */
    public boolean guardPredicate(M receivedMessage);

    /**
     * Processes a received message.
     * 
     * This is the main handler method. It can access and mutate the state through the accessors and generate
     * a list of intermediate message descriptors, that later will be transformed into messages.
     * 
     * @param message Received message
     * @param state Current state, can be mutated
     * @return List of message descriptors or null
     */
    public List<D> processMessage(M message, S state);
	
    /**
     * Produces the final output messages from the message descriptors.
     * 
     * It may access the state, but just read only.
     *  
     * @param state Current state
     * @param descriptors Generated descriptors by processMessage()
     * @return List of output messages, or null
     */
    public List<Message> getSendMessages(S state, List<D> descriptors);
	
}
