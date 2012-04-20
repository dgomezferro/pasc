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


/**
 * Base class for user defined messages.
 * 
 * All user defined messages must extend this class and implement the relevant methods. The messages must
 * have a redundant copy of themselves or a CRC code so the framework can verify them.
 *
 */
public abstract class Message {

    transient Message cloned;

    private transient boolean hasCloned = false;
    
    public final void setCloned(Message m) {
        if (!hasCloned)
            cloned = m;
        hasCloned = true;
    }
    
    public final Message getCloned() {
        return cloned;
    }

    /**
     * Verifies this message.
     * 
     * @return true if this message is correct according to the redundant copy or CRC code.
     */
    protected abstract boolean verify();
    
    /**
     * Stores a redundant copy of the passed message or a CRC code. The information to be stored must be retrieved
     * from the passed message and stored on this one.
     * 
     * @param m A copy of this message to be stored here
     */
    public abstract void storeReplica(Message m);
}
