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

package com.yahoo.pasc.generation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.yahoo.pasc.CorruptionException;
import com.yahoo.pasc.ProcessState;
import com.yahoo.pasc.CorruptionException.Type;
import com.yahoo.pasc.generation.Encapsulator;
import com.yahoo.pasc.generation.EncapsulatorGenerator;
import com.yahoo.pasc.generation.LightEncapsulatorGenerator;

public class EncapsulatorTest {
    private static EncapsulatorGenerator generator;
    private static LightEncapsulatorGenerator lightGenerator;
    private State state;
    private State replica;
    private Encapsulator encapsulator;
    private Encapsulator lightEncapsulator;
    
    @BeforeClass
    public static void initClass() {
        State state = new State();
        lightGenerator = new LightEncapsulatorGenerator(state);
        generator = new EncapsulatorGenerator(state);
    }
    
    @Before
    public void setUp() {
        state = new State();
        replica = new State();
        encapsulator = generator.getEncapsulator(state, replica);
        lightEncapsulator = lightGenerator.getLightEncapsulator(replica, state);
    }

    private void checkOverride(Class<?> wrapper, Class<?> base, String name, Class<?>... params) 
            throws SecurityException, NoSuchMethodException {
        Method wrapperMethod = wrapper.getDeclaredMethod(name, params);
        Method baseMethod = base.getDeclaredMethod(name, params);
        assertNotSame(baseMethod, wrapperMethod);
    }

    @Test
    public void checkOverrides() throws SecurityException, NoSuchMethodException {
        for (Encapsulator enc : Arrays.asList(encapsulator, lightEncapsulator)) {
            assertEquals(State.class, enc.getClass().getSuperclass());
            checkOverride(enc.getClass(), State.class, "getA");
            checkOverride(enc.getClass(), State.class, "setA", int.class);
        }
    }
    
    @Test
    public void accessorsDetection() throws SecurityException, NoSuchMethodException {
        for (Encapsulator enc : Arrays.asList(encapsulator, lightEncapsulator)) {
            assertEquals(State.class, enc.getClass().getSuperclass());
            checkOverride(enc.getClass(), State.class, "getB");
            checkOverride(enc.getClass(), State.class, "setC", int[].class);
        }
    }
    
    @Test
    public void incrementalUpdates() {
        State wrappedState = (State) encapsulator;
        assertEquals(0, wrappedState.getA());
        int newA = 5;
        wrappedState.setA(newA);
        assertEquals(newA, wrappedState.getA());
        assertEquals(0, state.getA());
        
        State lightlyWrappedState = (State) lightEncapsulator;
        assertEquals(0, lightlyWrappedState.getA());
        lightlyWrappedState.setA(newA);
        assertEquals(newA, lightlyWrappedState.getA());
        assertEquals(newA, replica.getA());
        
        encapsulator.applyModifications(false, lightEncapsulator);
        assertEquals(newA, state.getA());
    }

    @Test
    public void consistentStates() {
        State wrappedState = (State) encapsulator;
        wrappedState.setA(5);
        
        try {
            encapsulator.applyModifications(false, lightEncapsulator);
            fail("Didn't raise exception");
        } catch (CorruptionException e) {
            assertEquals(Type.STATE, e.getType());
        }
    }

    @Test
    public void checkInconsistentReplica() {
        State wrappedState = (State) encapsulator;
        replica.setA(5);
        
        try {
            wrappedState.getA();
            fail("Didn't raise exception");
        } catch (CorruptionException e) {
            assertEquals(Type.STATE, e.getType());
        }
    }

    @Test
    public void onlyCheckOnFirstRead() {
        State wrappedState = (State) encapsulator;
        replica.setA(5);
        
        wrappedState.setA(8);
        wrappedState.getA();
    }

    @Test
    public void overrideIndexed() throws SecurityException, NoSuchMethodException {
        ProcessState state = new ProcessState() {
            @SuppressWarnings("unused")
            public int getIndexedA(int i) { return 5; }
            @SuppressWarnings("unused")
            public void setIndexedB(String s, long l) { }
        };
        Encapsulator lighEncapsulator = new LightEncapsulatorGenerator(state).getLightEncapsulator(state, state);
        Encapsulator encapsulator = new EncapsulatorGenerator(state).getEncapsulator(state, state);
        
        for (Encapsulator enc : Arrays.asList(encapsulator, lighEncapsulator)) {
            checkOverride(enc.getClass(), state.getClass(), "getIndexedA", int.class);
            checkOverride(enc.getClass(), state.getClass(), "setIndexedB", String.class, long.class);
        }
    }
 
    @SuppressWarnings("unused")
    private static class State implements ProcessState, Cloneable {
        int a;

        public int getA() {
            return a;
        }

        public void setA(int a) {
            this.a = a;
        }
        
        public Integer getB() {
            return 4;
        }
        
        public void setC(int[] c) {
        }
    }

}
