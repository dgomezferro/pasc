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
 */
public class VariableCorruptionException extends CorruptionException {

    private static final long serialVersionUID = 5336446438577061165L;

    public VariableCorruptionException(String variable, Object value, Object replica) {
        super(variable + " value: " + value + " replica: " + replica);
    }

    public VariableCorruptionException(String variable, boolean value, boolean replica) {
        this(variable, (Object) value, replica);
    }

    public VariableCorruptionException(String variable, int value, int replica) {
        this(variable, (Object) value, replica);
    }

    public VariableCorruptionException(String variable, byte value, byte replica) {
        this(variable, (Object) value, replica);
    }

    public VariableCorruptionException(String variable, char value, char replica) {
        this(variable, (Object) value, replica);
    }

    public VariableCorruptionException(String variable, long value, long replica) {
        this(variable, (Object) value, replica);
    }

    public VariableCorruptionException(String variable, float value, float replica) {
        this(variable, (Object) value, replica);
    }

    public VariableCorruptionException(String variable, double value, double replica) {
        this(variable, (Object) value, replica);
    }
}
