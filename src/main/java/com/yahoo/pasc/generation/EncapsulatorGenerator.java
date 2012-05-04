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
import static com.yahoo.pasc.generation.GeneratorUtil.getEntrySetName;
import static com.yahoo.pasc.generation.GeneratorUtil.getIteratorName;
import static com.yahoo.pasc.generation.GeneratorUtil.getKeyName;
import static com.yahoo.pasc.generation.GeneratorUtil.getMapGet;
import static com.yahoo.pasc.generation.GeneratorUtil.getMapName;
import static com.yahoo.pasc.generation.GeneratorUtil.getNextCall;
import static com.yahoo.pasc.generation.GeneratorUtil.getObjectCast;
import static com.yahoo.pasc.generation.GeneratorUtil.getPrimitiveName;
import static com.yahoo.pasc.generation.GeneratorUtil.getSetName;
import static com.yahoo.pasc.generation.GeneratorUtil.getShortMapName;
import static com.yahoo.pasc.generation.GeneratorUtil.getValueName;
import static com.yahoo.pasc.generation.GeneratorUtil.obtainAccessibleFields;

import java.util.List;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;

import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;
import org.objenesis.instantiator.ObjectInstantiator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stringtemplate.v4.ST;

import com.yahoo.pasc.ProcessState;

public class EncapsulatorGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(EncapsulatorGenerator.class);

    private Objenesis objenesis = new ObjenesisStd();
    private ObjectInstantiator instantiator;

    private String className;
    private String facadeClassName;

    private Class<?> facadeClass = null;
    private static Object lock = new Object();

    public EncapsulatorGenerator(ProcessState state) {
        synchronized(lock) {
    
            Class<?> stateType = state.getClass();
            className = stateType.getName();
            facadeClassName = className + "Encapsulator";
            
            
            try {
                facadeClass = Class.forName(facadeClassName);
    
                instantiator = objenesis.getInstantiatorOf(facadeClass);
                return;
            } catch (ClassNotFoundException ignore) {
            }
            
            List<AccessibleField> fields = obtainAccessibleFields(stateType);
    
            ClassPool pool = ClassPool.getDefault();
            CtClass facadeCtClass = pool.makeClass(facadeClassName);
    
            try {
                facadeCtClass.setInterfaces(new CtClass[] { pool.get("com.yahoo.pasc.generation.Encapsulator") });
                facadeCtClass.setSuperclass(pool.get(className));

                generateFields(facadeCtClass, fields);

                generateEncapsulatorInterfaceMethods(facadeCtClass, fields);

                generateGettersAndSetters(facadeCtClass, fields);

                generateToString(facadeCtClass, fields);

                facadeClass = facadeCtClass.toClass();

                instantiator = objenesis.getInstantiatorOf(facadeClass);
            } catch (Exception ex) {
                throw new RuntimeException("Error constructing encapsulator class: " + facadeClassName, ex);
            }
        }
    }

    public <T extends ProcessState> Encapsulator getEncapsulator(T state, T replica) {
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
        CtField readOnly = CtField.make("private boolean readOnly;", facadeCtClass);
        facadeCtClass.addField(readOnly);
        CtField checkState = CtField.make("private boolean checkState;", facadeCtClass);
        facadeCtClass.addField(checkState);

        for (AccessibleField af : fields) {
            boolean indexed = af.isIndexed();
            String name = af.getName();
            Class<?> type = af.getType();
            if (indexed) {
                Class<?> keyType = af.getIndexType();
                CtField read = CtField.make(String.format("%s %sRead = new %s();", 
                        getSetName(keyType), name, getSetName(keyType), name), facadeCtClass);
                facadeCtClass.addField(read);
                CtField written = CtField.make(String.format("private %s %sWritten = new %s();", 
                        getMapName(keyType, type), name, getMapName(keyType, type)), facadeCtClass);
                facadeCtClass.addField(written);
                CtField latestValue = CtField.make(String.format("%s %sLatestValue;", getPrimitiveName(type), name), facadeCtClass);
                facadeCtClass.addField(latestValue);
                CtField latestKey = CtField.make(String.format("%s %sLatestKey;", getPrimitiveName(keyType), name), facadeCtClass);
                facadeCtClass.addField(latestKey);
                CtField cacheValid = CtField.make(String.format("boolean %sCacheValid;", name), facadeCtClass);
                facadeCtClass.addField(cacheValid);
            } else {
                CtField read = CtField.make(String.format("boolean %sRead;", name), facadeCtClass);
                facadeCtClass.addField(read);
                CtField written = CtField.make(String.format("private boolean %sWritten;", name), facadeCtClass);
                facadeCtClass.addField(written);
                CtField reference = CtField.make(String.format("private %s %sRef;", getPrimitiveName(type), name), facadeCtClass);
                facadeCtClass.addField(reference);
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
        return result;
    }
    
    private String getterSingle = addNewLines(
            "public final $type$ $getter$() {" +
            "   if(!$var$Written) {" +
            "       if(!$var$Read) {" +
            // If never read must be checked
            "           $var$Read = true;" +
            "           $if(primitive)$ " +
            "               if(state.$getter$() != replica.$getter$()) {" +
            "                   throw new com.yahoo.pasc.exceptions.VariableCorruptionException(\"$var$\", " +
            "                       state.$getter$(), replica.$getter$());" +
            "               }" +
            "           $endif$" +
            "           $if(!primitive)$" +
            "               if(!com.yahoo.pasc.PascRuntime.compare(state.$getter$(), replica.$getter$())) {" +
            "                   throw new com.yahoo.pasc.exceptions.VariableCorruptionException(\"$var$\", " +
            "                       state.$getter$(), replica.$getter$());" +
            "               }" +
            "           $endif$" +
            "       }" +
            // If never written must be cloned
            "       $var$Written = true;" +
            "       $if(primitive)$ $var$Ref = state.$getter$(); $endif$" +
            "       $if(!primitive)$ $var$Ref = $objectCast$ com.yahoo.pasc.PascRuntime.clone(state.$getter$()); $endif$" +
            "   }" +
            "   return $var$Ref;" +
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
            "       } else {" +
            //			Cache miss, clean it and move cached value to map
            "   		$var$Written.put($var$LatestKey, $var$LatestValue);" +
            "			if ($var$Written.containsKey(_key)) {" +	
            //          Written map hit (key has been read and cloned already). 
            //			Return value in map, but don't check it, and put it in cache.
            "				$type$ temp = $objectCast$ $var$Written.$mapGet$(_key);" +
            "       		$var$LatestValue = temp;" +
            "       		$var$LatestKey = _key;" +
            "       		return temp;" +
            "			}" +
            "       }" +
            "   }" +
            //	Cache and map miss. Overwrite cache (if it was dirty, it was cleaned already)
            //  Check replica
            "   $if(primitive)$ " +
            "       $type$ temp = state.$getter$(_key);" +
            "       if(temp != replica.$getter$(_key)) {" +
            "           throw new com.yahoo.pasc.exceptions.VariableCorruptionException(\"$var$\", temp, replica.$getter$(_key));" +
            "       }" +
            "   $endif$" +
            "   $if(!primitive)$" +
            "       $type$ temp = $objectCast$ com.yahoo.pasc.PascRuntime.clone(state.$getter$(_key));" +
            "       if(!com.yahoo.pasc.PascRuntime.compare(temp, replica.$getter$(_key))) {" +
            "           throw new com.yahoo.pasc.exceptions.VariableCorruptionException(\"$var$\", temp, replica.$getter$(_key));" +
            "       }" +
            "   $endif$" +
            //  Write in cache
            "   $var$CacheValid = true;" +
            "   $var$LatestValue = temp;" +
            "   $var$LatestKey = _key;" +
            "   return temp;" +
            "}"
            );

    private String setterSingle = addNewLines(
            "public final void $setter$($type$ _value) {" +
            "   $var$Written = true;" +
            "   $var$Read = true;" +
            "   $var$Ref = _value;" +
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
            "               ($var$LatestKey == null && _key != null) || ($var$LatestKey != null && !$var$LatestKey.equals(_key))" +
            "           $endif$" +
            "               ) {" +
            //			Overwrite map
            "           $var$Written.put($var$LatestKey, $var$LatestValue);" +
            "       }" +
            "   }" +
            //  Update cache
            "   $var$CacheValid = true;" +
            "   $var$LatestValue = _value;" +
            "   $var$LatestKey = _key;" +
            "}"
            );

    private String buildGetter(AccessibleField af) {
        Class<?> type = af.getType();

        boolean indexed = af.isIndexed();
        boolean primitive = af.getType().isPrimitive();
        boolean primitiveKey = false;

        String mapGet = null;
        String name = af.getName();
        String getter = af.getGetter();
        
        String typeName = getPrimitiveName(type);
        String typeKeyName = null;
        
        ST getterTemplate;

        if (indexed) {
            getterTemplate = new ST(getterMulti, '$', '$');
            Class<?> keyType = af.getIndexType();
            mapGet = getMapGet(keyType, type);
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
        getterTemplate.add("objectCast", getObjectCast(type));
        getterTemplate.add("mapGet", mapGet);
        
        String result =  getterTemplate.render();
        LOG.trace("Method: {}", result);
        return result;
    }

    private void generateToString(CtClass facadeCtClass, List<AccessibleField> fields) throws CannotCompileException {
        StringBuilder method = new StringBuilder();
        method.append("public String toString() {\n");
        method.append("StringBuilder sb = new StringBuilder(); \n");
        method.append(String.format("sb.append(\"Encapsulated class: %s\\n\"); \n", className));
        
        for (AccessibleField af : fields) {
            String name = af.getName();
            if (af.isIndexed()) {
                method.append(String.format("sb.append(\"%s writes: \" + %sWritten + \"\\n\"); \n", name, name));
                method.append(String.format("sb.append(\"%s reads: \" + %sRead + \"\\n\"); \n", name, name));
            } else {
                method.append(String.format("sb.append(\"%s written? \" + Boolean.valueOf(%sWritten).toString() + \" read? \" + Boolean.valueOf(%sRead).toString() + \"\\n\"); \n", name, name, name));
                method.append(String.format("sb.append(\"%s value: \" + %sRef + \"\\n\"); \n", name, name));
            }
        }
        method.append("return sb.toString(); \n }\n");

        CtMethod toString = CtNewMethod.make(method.toString(), facadeCtClass);
        facadeCtClass.addMethod(toString);
    }

    private String applyModifications = addNewLines(
            "public void applyModifications(boolean toReplica, com.yahoo.pasc.generation.Encapsulator lightEncapsulator) {" +
            "   $className$ temp = toReplica ? replica : state;" +
            "   $className$LightEncapsulator lightEncap = ($className$LightEncapsulator) lightEncapsulator;" +
            "" +
            "   $variableApplications$" +
            "" +
            "}");
    
    private String applySingle = addNewLines(
            "   if($var$Written) {" +
            "       if(!lightEncap.$var$Read)" +
            "           throw new com.yahoo.pasc.exceptions.AsymmetricalChangesException(\"$var$\", null, null);" +
            "       if(!$var$Read)" +
            "           throw new com.yahoo.pasc.exceptions.AsymmetricalChangesException(\"$var$\", null, null);" +
            "       temp.$setter$($var$Ref);" +
            "   }");
    
    private String applyMulti = addNewLines(
            "   if ($var$CacheValid) {" +
            "       $typeKey$ tempKey;" +
            "       $typeKey$ replicaKey;" +
            // First apply changes from the map
            "       if ($var$Written != null && !$var$Written.isEmpty()) {" +
            "           it.unimi.dsi.fastutil.objects.ObjectIterator it = $var$Written.$entrySetName$().fastIterator();" +
            "           $iteratorName$ itl = lightEncap.$var$Read.iterator();" +
            "           while(it.hasNext()) {" +
            "               $mapName$.Entry entry = ($mapName$.Entry) it.next();" +
            "               tempKey = entry.get$keyName$();" +
            "               replicaKey = $castKey$ itl.$nextName$();" +
            "               $if(primitiveKey)$" +
            "                   if(tempKey != replicaKey) " +
            "                       throw new com.yahoo.pasc.exceptions.AsymmetricalChangesException(\"$var$\", tempKey, replicaKey);" +
            "               $endif$" +
            "               $if(!primitiveKey)$" +
            "                   if(!((Object)tempKey).equals(replicaKey)) " +
            "                       throw new com.yahoo.pasc.exceptions.AsymmetricalChangesException(\"$var$\", tempKey, replicaKey);" +
            "               $endif$" +
            "               temp.$setter$($castKey$ tempKey, $castValue$ entry.get$valueName$());" +
            "           }" +
            "           if (itl.hasNext())  " +
            "               throw new com.yahoo.pasc.exceptions.AsymmetricalChangesException(\"$var$\", null, itl.next());" +
            "       } else if (($var$Read != null && !$var$Read.isEmpty()) || " +
            "               (lightEncap.$var$Read != null && !lightEncap.$var$Read.isEmpty()))  " +
            "           throw new com.yahoo.pasc.exceptions.AsymmetricalChangesException(\"$var$\", null, null);" +
            // then apply changes from the cache (modified last)
            "       tempKey = $var$LatestKey;" +
            "       replicaKey = lightEncap.$var$LatestKey;" +
            "       $if(primitiveKey)$" +
            "           if(tempKey != replicaKey) " +
            "               throw new com.yahoo.pasc.exceptions.AsymmetricalChangesException(\"$var$\", tempKey, replicaKey);" +
            "       $endif$" +
            "       $if(!primitiveKey)$" +
            "           if(!((Object)tempKey).equals(replicaKey)) " +
            "               throw new com.yahoo.pasc.exceptions.AsymmetricalChangesException(\"$var$\", tempKey, replicaKey);" +
            "       $endif$" +
            "       temp.$setter$($var$LatestKey, $var$LatestValue);" +
            "   }"
            );

    private String buildApplyModifications(List<AccessibleField> fields) {
        String applications = "";
        
        for (AccessibleField af : fields) {
            if (af.getSetter() == null) {
                continue;
            }

            String name = af.getName();
            Class<?> value = af.getType();
            boolean primitiveKey = false;
            
            ST application;
            if (af.isIndexed()) {
                application = new ST(applyMulti, '$', '$');
                Class<?> key = af.getIndexType();
                application.add("typeKey", getPrimitiveName(key));
                application.add("iteratorName", getIteratorName(key));
                application.add("mapName", getShortMapName(key, value));
                application.add("castKey", getObjectCast(key));
                application.add("castValue", getObjectCast(value));
                application.add("nextName", getNextCall(key));
                application.add("keyName", getKeyName(key));
                application.add("valueName", getValueName(value));
                application.add("entrySetName", getEntrySetName(key, value));
                primitiveKey = key.isPrimitive();
            } else {
                application = new ST(applySingle, '$', '$');
            }
            
            application.add("var", name);
            application.add("setter", af.getSetter());
            application.add("primitiveKey", primitiveKey);
            applications += application.render();
        }
        
        ST method = new ST(applyModifications, '$', '$');
        method.add("className", className);
        method.add("variableApplications", applications);
        
        return method.render();
    }

    private void generateEncapsulatorInterfaceMethods(CtClass facadeCtClass, List<AccessibleField> fields)
            throws CannotCompileException {
        CtMethod setReadOnly = CtNewMethod.make(
                "public void setReadOnly(boolean readOnly) { this.readOnly = readOnly; } \n", facadeCtClass);
        facadeCtClass.addMethod(setReadOnly);
        CtMethod setCheckState = CtNewMethod.make(
                "public void setCheckState(boolean checkState) { this.checkState = checkState; } \n", facadeCtClass);
        facadeCtClass.addMethod(setCheckState);
        CtMethod applyModifications = CtNewMethod.make(buildApplyModifications(fields), facadeCtClass);
        facadeCtClass.addMethod(applyModifications);
        CtMethod reset = CtNewMethod.make(buildReset(fields), facadeCtClass);
        facadeCtClass.addMethod(reset);
        CtMethod setState = CtNewMethod.make(buildSetState(fields), facadeCtClass);
        facadeCtClass.addMethod(setState);
    }

    private String buildReset(List<AccessibleField> fields) {
        StringBuilder method = new StringBuilder();
        method.append("public void reset() {\n");
        for (AccessibleField af : fields) {
            String name = af.getName();
            if (af.isIndexed()) {
                method.append(String.format("if (%sRead != null) %sRead.clear(); \n", name, name));
                method.append(String.format("if (%sWritten != null) %sWritten.clear(); \n", name, name));
                method.append(String.format("%sCacheValid = false; \n", name));
            } else {
                method.append(String.format("%sRead = false; \n", name));
                method.append(String.format("%sWritten = false; \n", name));
            }
        }
        method.append("}\n");
        return method.toString();
    }

    private String buildSetState(List<AccessibleField> fields) {
        StringBuilder method = new StringBuilder();
        method.append("public void setState(com.yahoo.pasc.ProcessState state, " + 
                "com.yahoo.pasc.ProcessState replica, org.objenesis.instantiator.ObjectInstantiator instantiator) {\n");
        method.append(String.format("this.state = (%s) state;\n", className));
        method.append(String.format("this.replica = (%s) replica;\n", className));
        method.append("this.instantiator = instantiator;\n");
        for (AccessibleField af : fields) {
            Class<?> type = af.getType();
            String name = af.getName();
            if (af.isIndexed()) {
                Class<?> typeKey = af.getIndexType();
                method.append(String.format("%sRead = new %s();", 
                        name, getSetName(typeKey), name));
                method.append(String.format("%sWritten = new %s();", 
                        name, getMapName(typeKey, type)));
            }
        }
        method.append("}\n");
        return method.toString();
    }
}