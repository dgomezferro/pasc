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
 * Provide an efficient method to compare two objects.
 * 
 * Value types that belong to the state should implement this interface. Those classes that implement this interface 
 * will be compared using the {@linkplain equalsDeep()} method, which can be much
 * more faster than the fallback approach, serializing both objects and comparing the resulting byte arrays.
 *
 * @param <T> usually the class implementing the interface ( @code{public class Value implements CloneableDeep<Value>} )
 */
public interface EqualsDeep<T> {
    /**
     * Compare this object against other.
     * 
     * Compares this object against another, checking that all relevant fields are the same.
     * 
     * @return true if the two objects are equal.
     */
	public boolean equalsDeep (T other);
}
