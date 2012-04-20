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

import static com.yahoo.pasc.generation.GeneratorUtil.addNewLines;
import static com.yahoo.pasc.generation.GeneratorUtil.getPrimitiveName;
import static com.yahoo.pasc.generation.GeneratorUtil.getSetName;
import static com.yahoo.pasc.generation.GeneratorUtil.obtainAccessibleFields;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;

import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;
import org.objenesis.instantiator.ObjectInstantiator;
import org.stringtemplate.v4.ST;

import com.yahoo.pasc.ProcessState;

public class LightEncapsulatorGenerator {

    private Objenesis objenesis = new ObjenesisStd();
    private ObjectInstantiator instantiator;

    private String className;
    private String facadeClassName;
    
    private Class<?> facadeClass = null;
    private static Object lock = new Object();

    public LightEncapsulatorGenerator(ProcessState state) {
        synchronized(lock) {
            Class<?> type = state.getClass();
    
            className = type.getName();
            facadeClassName = className + "LightEncapsulator";
            
            List<AccessibleField> fields = obtainAccessibleFields(type);
    
            try {
                facadeClass = Class.forName(facadeClassName);
    
                instantiator = objenesis.getInstantiatorOf(facadeClass);
                return;
            } catch (ClassNotFoundException ignore) {
            }
    
            ClassPool pool = ClassPool.getDefault();
            CtClass facadeCtClass = pool.makeClass(facadeClassName);
    
            try {
                facadeCtClass.setInterfaces(new CtClass[] { pool.get("com.yahoo.pasc.generation.Encapsulator") });
                facadeCtClass.setSuperclass(pool.get(className));
                
                generateFields(facadeCtClass, fields);
                
                generateEncapsulatorInterfaceMethods(facadeCtClass, fields);
        
                generateGettersAndSetters(facadeCtClass, fields);
        
                facadeClass = facadeCtClass.toClass();
                
                instantiator = objenesis.getInstantiatorOf(facadeClass);
            } catch (Exception ex) {
                throw new RuntimeException("Error constructing encapsulator class: " + facadeClassName, ex);
            }
        }
    }

    public <T extends ProcessState> Encapsulator getLightEncapsulator(T state, T replica) {
        Encapsulator encapsulator = (Encapsulator) instantiator.newInstance();
        encapsulator.setState(state, replica, instantiator);
        return encapsulator;
    }
    
    private void generateFields(CtClass facadeCtClass, List<AccessibleField> fields) throws CannotCompileException {
        CtField state = CtField.make(String.format("%s state;", className), facadeCtClass);
        facadeCtClass.addField(state);
        CtField replica = CtField.make(String.format("%s replica;", className), facadeCtClass);
        facadeCtClass.addField(replica);
        CtField objIns = CtField.make("org.objenesis.instantiator.ObjectInstantiator instantiator;", facadeCtClass);
        facadeCtClass.addField(objIns);
        
        for (AccessibleField af : fields) {
            boolean indexed = af.isIndexed();
            String name = af.getName();
            Class<?> type = af.getType();
            Set<Class<?>> defined = new HashSet<Class<?>>();
            if (Encapsulator.class.isAssignableFrom(type) && defined.add(type)) {
                CtField encapsulator = CtField.make(String.format("com.yahoo.pasc.generation.EncapsulatorGenerator %sEncapsulatorGenerator;", name), facadeCtClass);
                facadeCtClass.addField(encapsulator);
            }
            if (indexed) {
                Class<?> typeKey = af.getIndexType();
                CtField read = CtField.make(String.format("%s %sRead = new %s();", 
                        getSetName(typeKey), name, getSetName(typeKey), name), facadeCtClass);
                facadeCtClass.addField(read);
                CtField latestKey = CtField.make(String.format("%s %sLatestKey;", getPrimitiveName(typeKey), name), facadeCtClass);
                facadeCtClass.addField(latestKey);
                CtField cacheValid = CtField.make(String.format("boolean %sCacheValid;", name), facadeCtClass);
                facadeCtClass.addField(cacheValid);
                CtField latestValue = CtField.make(String.format("%s %sLatestValue;", getPrimitiveName(type), name), facadeCtClass);
                facadeCtClass.addField(latestValue);
            } else {
                CtField read = CtField.make(String.format("boolean %sRead;", name), facadeCtClass);
                facadeCtClass.addField(read);
            }
        }
    }
    


    private void generateGettersAndSetters(CtClass facadeCtClass, List<AccessibleField> fields)
            throws CannotCompileException {
        for (AccessibleField af : fields) {
            if (af.getGetter() != null) {
                CtMethod getter = CtNewMethod.make(buildGetter(af), facadeCtClass);
                facadeCtClass.addMethod(getter);
            }
            if (af.getSetter() != null) {
                CtMethod setter = CtNewMethod.make(buildSetter(af), facadeCtClass);
                facadeCtClass.addMethod(setter);
            }
        }
    }    
    private String getterSingle = addNewLines(
            "public final $type$ $getter$() {" +
            "   if(!$var$Read) {" +
            // If never read must be checked
            "       $var$Read = true;" +
            "       $if(primitive)$ " +
            "           if(state.$getter$() != replica.$getter$()) {" +
            "               throw new com.yahoo.pasc.CorruptionException(\"States differ\", " +
            "                   com.yahoo.pasc.CorruptionException.Type.STATE);" +
            "           }" +
            "       $endif$" +
            "       $if(!primitive)$" +
            "           if(!com.yahoo.pasc.PascRuntime.compare(state.$getter$(), replica.$getter$())) {" +
            "               throw new com.yahoo.pasc.CorruptionException(\"States differ\"," +
            "                   com.yahoo.pasc.CorruptionException.Type.STATE);" +
            "           }" +
            "       $endif$" +
            "   }" +
            "   return state.$getter$();" +
            "}"
            );

    private String getterMulti = addNewLines(
            "public final $type$ $getter$($typeKey$ _key) {" +
            "   if($var$CacheValid) {" +
            "       if (" +
            "       $if(primitiveKey)$ " +
            "           $var$LatestKey == _key" +
            "       $endif$" +
            "       $if(!primitiveKey)$" +
            "           $var$LatestKey.equals(_key)" +
            "       $endif$" +
            "           ) {" +
            //          Cache hit, return it
            "           return $var$LatestValue;" +
            "       } " +
            "       $var$Read.add($var$LatestKey);" +
            "       if ($var$Read.contains(_key)) {" +
            //          Written map hit (key has been read and cloned already). 
            //			Return value in map, but don't check it, and put it in cache.
            "			$var$LatestKey = _key;" +
            "           $var$LatestValue = state.$getter$(_key);" +
            "           return $var$LatestValue;" +
            "       }" +
            "   }	" +
            //  Check replica
            "   $type$ temp = state.$getter$(_key);" +
            "   $if(primitive)$ " +
            "       if(temp != replica.$getter$(_key)) {" +
            "           throw new com.yahoo.pasc.CorruptionException(\"States differ\", " +
            "               com.yahoo.pasc.CorruptionException.Type.STATE);" +
            "       }" +
            "   $endif$" +
            "   $if(!primitive)$" +
            "       if(!com.yahoo.pasc.PascRuntime.compare(temp, replica.$getter$(_key))) {" +
            "           throw new com.yahoo.pasc.CorruptionException(\"States differ\", " +
            "               com.yahoo.pasc.CorruptionException.Type.STATE);" +
            "       }" +
            "   $endif$" +
            "   $var$CacheValid = true;" +
            "   $var$LatestKey = _key;" +
            "   $var$LatestValue = temp;" +
            "   return temp;" +
            "}"
            );

    private String setterSingle = addNewLines(
            "public final void $setter$($type$ _value) {" +
            "   $var$Read = true;" +
            "   state.$setter$(_value);" +
            "}"
            );

    private String setterMulti = addNewLines(
            "public final void $setter$($typeKey$ _key, $type$ _value) {" +
            "   if ($var$CacheValid) {" +
            "       if (" +
            "           $if(primitiveKey)$" +
            "               $var$LatestKey != _key" +
            "           $endif$" +
            "           $if(!primitiveKey)$" +
            "               ($var$LatestKey == null && _key != null) || " +
            "               ($var$LatestKey != null && !$var$LatestKey.equals(_key))" +
            "           $endif$" +
            "               ) {" +
            "           $var$Read.add($var$LatestKey);" +
            "       }" +
            "   }" +
            "   $var$CacheValid = true;" +
            "   $var$LatestKey = _key;" +
            "   $var$LatestValue = _value;" +
            "   state.$setter$(_key, _value);" +
            "}"
            );

    private String buildSetter(AccessibleField af) {
        Class<?> type = af.getType();

        boolean indexed = af.isIndexed();

        String name = af.getName();
        String setter = af.getSetter();
        boolean primitiveKey = false;
        String typeKeyName = null;
        String typeName = getPrimitiveName(type);
        
        ST setterTemplate;

        if (indexed) {
            setterTemplate = new ST(setterMulti, '$', '$');
            Class<?> keyType = af.getIndexType(); 
            primitiveKey = keyType.isPrimitive();
            typeKeyName = getPrimitiveName(keyType);
        } else {
            setterTemplate = new ST(setterSingle, '$', '$');
        }

        setterTemplate.add("setter", setter);
        setterTemplate.add("type", typeName);
        setterTemplate.add("typeKey", typeKeyName);
        setterTemplate.add("var", name);
        setterTemplate.add("primitiveKey", primitiveKey);
        
        String result =  setterTemplate.render();
        System.out.println("Method: " + result);
        return result;
    }
    
    private String buildGetter(AccessibleField af) {
        Class<?> type = af.getType();

        boolean indexed = af.isIndexed();
        boolean primitive = af.getType().isPrimitive();
        boolean primitiveKey = false;

        String name = af.getName();
        String getter = af.getGetter();
        
        String typeName = getPrimitiveName(type);
        String typeKeyName = null;
        
        ST getterTemplate;
    
        if (indexed) {
            getterTemplate = new ST(getterMulti, '$', '$');
            Class<?> keyType = af.getIndexType();
            primitiveKey = keyType.isPrimitive();
            typeKeyName = getPrimitiveName(keyType);
        } else {
            getterTemplate = new ST(getterSingle, '$', '$');
        }
            
        getterTemplate.add("type", typeName);
        getterTemplate.add("getter", getter);
        getterTemplate.add("typeKey", typeKeyName);
        getterTemplate.add("var", name);
        getterTemplate.add("primitive", primitive);
        getterTemplate.add("primitiveKey", primitiveKey);
        
        String result =  getterTemplate.render();
        System.out.println("Method: " + result);
        return result;
    }

    private String buildReset(List<AccessibleField> fields) {
        StringBuilder method = new StringBuilder();
        method.append("public void reset() {\n");
        for (AccessibleField af : fields) {
            String name = af.getName();
            if (af.isIndexed()) {
                method.append(String.format("if (%sRead != null) %sRead.clear(); \n", name, name));
                method.append(String.format("%sCacheValid = false;; \n", name));
            } else {
                method.append(String.format("%sRead = false; \n", name));
            }
        }
        method.append("}\n");
        return method.toString();
    }

    private void generateEncapsulatorInterfaceMethods(CtClass facadeCtClass, List<AccessibleField> fields)
            throws CannotCompileException {
        CtMethod setReadOnly = CtNewMethod.make(
                "public void setReadOnly(boolean readOnly) { } \n", facadeCtClass);
        facadeCtClass.addMethod(setReadOnly);
        CtMethod setCheckState = CtNewMethod.make(
                "public void setCheckState(boolean checkState) { } \n", facadeCtClass);
        facadeCtClass.addMethod(setCheckState);
        CtMethod applyModifications = CtNewMethod.make(
                "public void applyModifications(boolean toReplica, com.yahoo.pasc.generation.Encapsulator encapsulator) {}\n", facadeCtClass);
        facadeCtClass.addMethod(applyModifications);
        CtMethod reset = CtNewMethod.make(buildReset(fields), facadeCtClass);
        facadeCtClass.addMethod(reset);
        CtMethod getClone = CtNewMethod.make(
                "public com.yahoo.pasc.generation.Encapsulator getClone() { return null; } \n", facadeCtClass);
        facadeCtClass.addMethod(getClone);
        CtMethod setState = CtNewMethod.make(buildSetState(fields), facadeCtClass);
        facadeCtClass.addMethod(setState);
    }

    private String buildSetState(List<AccessibleField> fields) {
        StringBuilder method = new StringBuilder();
        method.append("public void setState(com.yahoo.pasc.ProcessState state, " + 
                "com.yahoo.pasc.ProcessState replica, org.objenesis.instantiator.ObjectInstantiator instantiator) {\n");
        method.append(String.format("this.state = (%s) state;\n", className));
        method.append(String.format("this.replica = (%s) replica;\n", className));
        method.append("this.instantiator = instantiator;\n");
        for (AccessibleField af : fields) {
            String name = af.getName();
            if (af.isIndexed()) {
                Class<?> typeKey = af.getIndexType();
                method.append(String.format("%sRead = new %s();", 
                        name, getSetName(typeKey), name));
            }
        }
        method.append("}\n");
        return method.toString();
    }
}