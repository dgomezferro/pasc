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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.yahoo.pasc.exceptions.AsymmetricalChangesException;
import com.yahoo.pasc.exceptions.CorruptionException;
import com.yahoo.pasc.exceptions.InputMessageException;
import com.yahoo.pasc.exceptions.MessagesGenerationException;
import com.yahoo.pasc.exceptions.VariableCorruptionException;

public class RuntimeTest {

    private class TestFailureHandler implements FailureHandler {
        @Override
        public void handleFailure(Exception e) {
            if (e instanceof CorruptionException) {
                throw (CorruptionException) e;
            }
            throw new RuntimeException(e);
        }
    }

    private static int SIZE = 100;
    private PascRuntime<State> runtime;

    @Before
    public void setUp() throws SecurityException, NoSuchFieldException {
        runtime = new PascRuntime<State>();
        runtime.setState(new State());
        runtime.addHandler(TMessage.class, new Handler());
        runtime.setFailureHandler(new TestFailureHandler());
    }
    
    @Test
    public void normalOperation() {
        Message m = new TMessage(5);
        m.storeReplica(m);
        State s = runtime.getState();
        int initial = s.getA();
        List<Message> messages = runtime.handleMessage(m);
        assertNotNull(messages);
        assertEquals(1, messages.size());
        Message response = messages.get(0);
        if (!(response instanceof TMessage)) {
            fail("response is not a TMessage");
            return;
        }
        TMessage tresp = (TMessage) response;
        assertEquals(5, tresp.a);
        assertEquals(5, s.getA() - initial);
    }

    @Test
    public void multipleOperations() {
        Message m = new TMessage(5);
        m.storeReplica(m);
        runtime.addHandler(TMessage.class, new Handler() {
            @Override
            public List<TMessage> processMessage(TMessage message, State state) {
                int a = state.getA() + message.a;

                state.setA(a);
                state.getC("foo");
                state.getC("bar");
                state.setC("foo", 1);
                state.setC("bar", 2);
                state.getC("foo");
                state.getC("bar");
                
                return Arrays.asList(new TMessage(a));
            }
        });
        runtime.handleMessage(m);
    }
    
    @Test
    public void inconsistentGetsSets() {
        Message m = new TMessage(5);
        m.storeReplica(m);
        runtime.addHandler(TMessage.class, new Handler() {
            @Override
            public List<TMessage> processMessage(TMessage message, State state) {
                state.setC("foo", 1);
                state.getC("bar");
                return null;
            }
        });
        runtime.handleMessage(m);
    }
    
    @Test
    public void getAfterSet() {
        TMessage m = new TMessage(5);
        m.storeReplica(m);
        runtime.addHandler(TMessage.class, new Handler() {
            @Override
            public List<TMessage> processMessage(TMessage message, State state) {
                state.setC("foo", message.a);
                int a = (int) state.getC("foo");
                return Arrays.asList(new TMessage(a));
            }
        });
        List<Message> messages = runtime.handleMessage(m);
        assertNotNull(messages);
        assertEquals(1, messages.size());
        Message response = messages.get(0);
        if (!(response instanceof TMessage)) {
            fail("response is not a TMessage");
            return;
        }
        TMessage tresp = (TMessage) response;
        assertEquals(m.a, tresp.a);
    }
    
    @Test
    public void indexedVariables() {
        Message m = new TMessage(5);
        m.storeReplica(m);
        State s = runtime.getState();
        runtime.addHandler(TMessage.class, new Handler() {
            @Override
            public List<TMessage> processMessage(TMessage message, State state) {
                long l = state.getC(Integer.toString(message.a));
                l += message.a;
                state.setC(Integer.toString(message.a + 1), l);
                return Arrays.asList(new TMessage((int) l));
            }
        });
        List<Message> messages = runtime.handleMessage(m);
        assertNotNull(messages);
        assertEquals(1, messages.size());
        Message response = messages.get(0);
        if (!(response instanceof TMessage)) {
            fail("response is not a TMessage");
            return;
        }
        TMessage tresp = (TMessage) response;
        assertEquals(5, tresp.a);
        for (int i = 0; i < 10; ++i) {
            if (i == 6) {
                assertEquals(5, s.getC(Integer.toString(i)));
            } else {
                assertEquals(0, s.getC(Integer.toString(i)));
            }
        }
    }

    @Test
    public void applyLastestChane() {
        Message m = new TMessage(5);
        m.storeReplica(m);
        State s = runtime.getState();
        runtime.addHandler(TMessage.class, new Handler() {
            @Override
            public List<TMessage> processMessage(TMessage message, State state) {
                state.setC(Integer.toString(message.a), 33);
                state.setC(Integer.toString(message.a + 1), 34);
                state.setC(Integer.toString(message.a), 35);
                return Arrays.asList(new TMessage(0));
            }
        });
        List<Message> messages = runtime.handleMessage(m);
        assertNotNull(messages);
        assertEquals(1, messages.size());
        for (int i = 0; i < 10; ++i) {
            if (i == 5) {
                assertEquals(35, s.getC(Integer.toString(i)));
            } else if (i == 6) {
                assertEquals(34, s.getC(Integer.toString(i)));
            } else {
                assertEquals(0, s.getC(Integer.toString(i)));
            }
        }
    }
    
    @Test
    public void ignoreCorruptInconmingMessage() {
        Message m = new TMessage(5);
        // Don't store replica == corrupt message
        State s = runtime.getState();
        int initial = s.getA();
        List<Message> messages = runtime.handleMessage(m);
        assertNotNull(messages);
        assertEquals(0, messages.size());
        assertEquals(0, s.getA() - initial);
    }

    @Test
    public void detectCorruptReplica() {
        Message m = new TMessage(5);
        m.storeReplica(m);
        State s = runtime.getState();
        State r = runtime.getReplica();
        r.setA(s.getA() + 2);
        try {
            runtime.handleMessage(m);
            fail("Should detect corrupt replica");
        } catch (VariableCorruptionException e) {
            //ignore
        }
    }
    
    @Test
    public void detectCorruptMessage() {
        Message m = new TMessage(5);
        m.storeReplica(m);
        runtime.addHandler(TMessage.class, new Handler() {
            @Override
            public List<TMessage> processMessage(TMessage message, State state) {
                message.a++;
                return super.processMessage(message, state);
            }
        });
        try {
            runtime.handleMessage(m);
            fail("Should detect corrupt message");
        } catch (InputMessageException e) {
            //ignore
        }
    }

    @Test
    public void detectCorruptState() {
        Message m = new TMessage(5);
        m.storeReplica(m);
        runtime.addHandler(TMessage.class, new Handler() {
            @Override
            public List<TMessage> processMessage(TMessage message, State state) {
                List<TMessage> result = super.processMessage(message, state);
                try {
                    Field f = state.getClass().getDeclaredField("state");
                    State s = (State) f.get(state);
                    s.a++;
                } catch (Exception e) {
                    fail("Couldn't get state field");
                }
                return result;
            }
        });
        try {
            runtime.handleMessage(m);
            fail("Should detect corrupt state");
        } catch (VariableCorruptionException e) {
            //ignore
        }
    }

    @Test
    public void detectInconsistentMessages() {
        Message m = new TMessage(5);
        m.storeReplica(m);
        runtime.addHandler(TMessage.class, new Handler() {
            private boolean firstRun = true;

            @Override
            public List<TMessage> processMessage(TMessage message, State state) {
                if (firstRun) {
                    state.setA(state.getA()+1);
                }
                firstRun = !firstRun;
                return super.processMessage(message, state);
            }
        });
        try {
            runtime.handleMessage(m);
            fail("Should detect inconsistent change");
        } catch (MessagesGenerationException e) {
            //ignore
        }
    }

    @Test
    public void detectInconsistentChange() {
        Message m = new TMessage(5);
        m.storeReplica(m);
        runtime.addHandler(TMessage.class, new Handler() {
            private boolean firstRun = true;

            @Override
            public List<TMessage> processMessage(TMessage message, State state) {
                if (firstRun) {
                    state.setA(state.getA()+1);
                } else {
                    state.setB(state.getB()+1);
                }
                firstRun = !firstRun;
                return null;
            }
        });
        try {
            runtime.handleMessage(m);
            fail("Should detect inconsistent change");
        } catch (AsymmetricalChangesException e) {
            //ignore
        }
    }

    private static class State implements ProcessState{
        int a;
        int b;
        long c[] = new long [SIZE];

        public int getA() {
            return a;
        }

        public void setA(int a) {
            this.a = a;
        }

        public int getB() {
            return b;
        }

        public void setB(int b) {
            this.b = b;
        }
        
        public long getC(String s) {
            return c[s.hashCode() % SIZE];
        }
        
        public void setC(String s, long l) {
            c[s.hashCode() % SIZE] = l;
        }
    }
    
    private static class TMessage extends Message implements MessageDescriptor, EqualsDeep<TMessage> {
        int a;
        int crc;
        
        public TMessage(int a) {
            this.a = a;
        }
        
        @Override
        protected boolean verify() {
            return a == crc;
        }

        @Override
        public void storeReplica(Message m) {
            if (!(m instanceof Message))
                return;
            crc = ((TMessage) m).a;
        }

        @Override
        public boolean equalsDeep(TMessage other) {
            return this.a == other.a;
        }
    }
    
    private static class Handler implements MessageHandler<TMessage, State, TMessage> {

        @Override
        public boolean guardPredicate(TMessage receivedMessage) {
            return true;
        }

        @Override
        public List<TMessage> processMessage(TMessage message, State state)
        {
            int a = state.getA() + message.a;
            state.setA(a);
            
            return Arrays.asList(new TMessage(a));
        }

        @Override
        public List<Message> getSendMessages(State state, List<TMessage> descriptors) {
            return descriptors == null ? null : Collections.<Message>unmodifiableList(descriptors);
        }
        
    }
}
