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

package com.yahoo.pasc.exceptions;

/**
 * Exception thrown in case of message/state corruption or failure.
 *
 */
public class AsymmetricalChangesException extends CorruptionException {

    private static final long serialVersionUID = 5336446438577061165L;

    public AsymmetricalChangesException(String variable, Object key, Object replicaKey) {
        super(variable + " key: " + key + " replicaKey: " + replicaKey);
    }

    public AsymmetricalChangesException(String variable, boolean key, boolean replicaKey) {
        this(variable, (Object) key, replicaKey);
    }

    public AsymmetricalChangesException(String variable, int key, int replicaKey) {
        this(variable, (Object) key, replicaKey);
    }

    public AsymmetricalChangesException(String variable, byte key, byte replicaKey) {
        this(variable, (Object) key, replicaKey);
    }

    public AsymmetricalChangesException(String variable, char key, char replicaKey) {
        this(variable, (Object) key, replicaKey);
    }

    public AsymmetricalChangesException(String variable, long key, long replicaKey) {
        this(variable, (Object) key, replicaKey);
    }

    public AsymmetricalChangesException(String variable, float key, float replicaKey) {
        this(variable, (Object) key, replicaKey);
    }

    public AsymmetricalChangesException(String variable, double key, double replicaKey) {
        this(variable, (Object) key, replicaKey);
    }
}
