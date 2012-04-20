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
 * MessageHandler specialization that doesn't use intermediate MessageDescriptors
 *
 * @param <M> Message type that this handler will receive
 * @param <S> User state type
 */
public abstract class DescriptorlessMessageHandler <M extends Message, S extends ProcessState> 
        implements MessageHandler<M, S, Message>{
    
    /**
     * Processes a received message.
     * 
     * This is the main handler method. It can access and mutate the state through the accessors and generate
     * the output messages.
     * 
     * @param message Received message
     * @param state Current state, can be mutated
     * @return List of message descriptors or null
     */
    public final List<Message> getSendMessages(S state, List<Message> descriptors) {
        return descriptors;
    };
}