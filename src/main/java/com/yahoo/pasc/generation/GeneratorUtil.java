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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.Type;

class GeneratorUtil {

    static String addNewLines(String string) {
        return string.replaceAll("([^ ])   ", "$1\n   ").replaceAll(";}",";\n}").replaceAll("}}","}\n}")
                .replaceAll("<<", "<<\n").replace(">>", "\n>>\n");
    }

    static String getMapGet(Class<?> keyType, Class<?> type) {
        if (!type.isPrimitive()) return "get";
        if (keyType.isPrimitive()) return "get";
        return "get" + getFastUtilsName(type);
    }

    static String getFastUtilsPackage(Class<?> type) {
        Type fieldType = Type.getType(type);
        switch (fieldType.getSort()) {
        case Type.INT: return "ints";
        case Type.BOOLEAN: return "booleans";
        case Type.BYTE: return "bytes";
        case Type.CHAR: return "chars";
        case Type.SHORT: return "short";
        case Type.LONG: return "longs";
        case Type.FLOAT: return "floats";
        case Type.DOUBLE: return "doubles";
        default: return "objects";
        }
    }

    static String getFastUtilsNameVoidObject(Class<?> type) {
        Type fieldType = Type.getType(type);
        switch (fieldType.getSort()) {
        case Type.INT: return "Int";
        case Type.BOOLEAN: return "Boolean";
        case Type.BYTE: return "Byte";
        case Type.CHAR: return "Char";
        case Type.SHORT: return "Short";
        case Type.LONG: return "Long";
        case Type.FLOAT: return "Float";
        case Type.DOUBLE: return "Double";
        default: return "";
        }
    }

    static String getFastUtilsName(Class<?> type) {
        Type fieldType = Type.getType(type);
        switch (fieldType.getSort()) {
        case Type.INT: return "Int";
        case Type.BOOLEAN: return "Boolean";
        case Type.BYTE: return "Byte";
        case Type.CHAR: return "Char";
        case Type.SHORT: return "Short";
        case Type.LONG: return "Long";
        case Type.FLOAT: return "Float";
        case Type.DOUBLE: return "Double";
        default: return "Object";
        }
    }

    static String getSetName(Class<?> keyType) {
        StringBuilder name = new StringBuilder("it.unimi.dsi.fastutil.");
        name.append(getFastUtilsPackage(keyType)).append('.');
        name.append(getFastUtilsName(keyType)).append("OpenHashSet");
        return name.toString();
    }

    static String getMapName(Class<?> keyType, Class<?> type) {
        StringBuilder name = new StringBuilder("it.unimi.dsi.fastutil.");
        name.append(getFastUtilsPackage(keyType)).append('.');
        name.append(getFastUtilsName(keyType)).append('2').append(getFastUtilsName(type)).append("OpenHashMap");
        return name.toString();
    }

    static String getShortMapName(Class<?> keyType, Class<?> type) {
        StringBuilder name = new StringBuilder("it.unimi.dsi.fastutil.");
        name.append(getFastUtilsPackage(keyType)).append('.');
        name.append(getFastUtilsName(keyType)).append('2').append(getFastUtilsName(type)).append("Map");
        return name.toString();
    }

    static String getIteratorName(Class<?> keyType) {
        StringBuilder name = new StringBuilder("it.unimi.dsi.fastutil.");
        name.append(getFastUtilsPackage(keyType)).append('.');
        name.append(getFastUtilsName(keyType)).append("Iterator");
        return name.toString();
    }

    static String getNextCall(Class<?> keyType) {
        StringBuilder name = new StringBuilder("next");
        name.append(getFastUtilsNameVoidObject(keyType));
        return name.toString();
    }

    static String getEntrySetName(Class<?> keyType, Class<?> type) {
        StringBuilder name = new StringBuilder();
        name.append(getFastUtilsName(keyType).toLowerCase()).append('2').append(getFastUtilsName(type));
        name.append("EntrySet");
        return name.toString();
    }

    static Object getValueName(Class<?> type) {
        StringBuilder name = new StringBuilder();
        name.append(getFastUtilsNameVoidObject(type)).append("Value");
        return name.toString();
    }

    static Object getKeyName(Class<?> keyType) {
        StringBuilder name = new StringBuilder();
        name.append(getFastUtilsNameVoidObject(keyType)).append("Key");
        return name.toString();
    }

    static String getObjectCast(Class<?> type) {
        if (type.isPrimitive()) return "";
        return "(" + type.getCanonicalName() + ")";
    }

    static String getPrimitiveName(Class<?> type) {
        Type fieldType = Type.getType(type);
        switch (fieldType.getSort()) {
        case Type.BOOLEAN:
            return "boolean";
        case Type.BYTE:
            return "byte";
        case Type.CHAR:
            return "char";
        case Type.SHORT:
            return "short";
        case Type.INT:
            return "int";
        case Type.LONG:
            return "long";
        case Type.FLOAT:
            return "float";
        case Type.DOUBLE:
            return "double";
        default:
            return type.getCanonicalName();
        }
    }

    static List<AccessibleField> obtainAccessibleFields(Class<?> type) {
        Map<String, AccessibleField> afs = new HashMap<String, AccessibleField>();
        for (Method method : type.getDeclaredMethods()) {
            String methodName = method.getName();
            boolean getter = methodName.startsWith("get");
            boolean setter = methodName.startsWith("set");
            boolean valid = methodName.length() > 3;
            if (valid && (getter || setter)) {
                String name = methodName.substring(3);
                name = name.substring(0, 1).toLowerCase() + name.substring(1);
                AccessibleField af = afs.get(name);
                if (af == null) {
                    af = new AccessibleField();
                    af.setName(name);
                    afs.put(name, af);
                }
                if (getter) {
                    af.setGetter(methodName);
                    af.setType(method.getReturnType());
                    if (method.getParameterTypes().length > 0) {
                        af.setIndexed(true);
                        af.setIndexType(method.getParameterTypes()[0]);
                    }
                }
                if (setter) {
                    af.setSetter(methodName);
                    if (method.getParameterTypes().length > 1) {
                        af.setIndexed(true);
                        af.setIndexType(method.getParameterTypes()[0]);
                        af.setType(method.getParameterTypes()[1]);
                    } else {
                        af.setType(method.getParameterTypes()[0]);
                    }
                }
            }
        }
        return new ArrayList<AccessibleField>(afs.values());
    }
}