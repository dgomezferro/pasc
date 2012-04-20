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
 * Provide an efficient method to clone an object.
 * 
 * Value types that belong to the state should implement this interface. Those classes that implement this interface 
 * will be cloned using the {@linkplain cloneDeep()} method, which can be much
 * more faster than the fallback approach, serializing and deserializing the object.
 *
 * @param <T> usually the class implementing the interface ( @code{public class Value implements CloneableDeep<Value>} )
 */
public interface CloneableDeep<T> {
    /**
     * Creates a deep clone of this object.
     * 
     * Creates a fresh new copy of this object, recursively copying all its members.
     * 
     * @return a deep copy of this object
     */
	public T cloneDeep();
}
