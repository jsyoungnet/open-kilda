/* Copyright 2022 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */


package org.openkilda.wfm.topology.flowhs.mapper;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.openkilda.messaging.BaseMessage;

import org.reflections.Reflections;

import java.lang.reflect.Field;
import java.util.*;
import java.lang.reflect.Modifier;
import java.io.Serializable;


@Slf4j
public class SerializebleTest {


    @Test
    public void serializebleBaseMessageTest() {

        Reflections reflections = new Reflections("org.openkilda");

        // 1 взять класс
        // 2 взять у него список полей -> добавить в список на иследование
        // 3 взять у него список наследников -> добавить в список на иследование
        // 4 добавить класс в список на проверку если его там нет
        // 5 запустить этот метод для полей и наследников

        Set<Class<?>> s1 = new HashSet<>();
        Map<Class<?>, String> desc = new HashMap<>();
        Stack<Class<?>> stack = new Stack<>();
        recursiveFind(reflections, s1, desc, stack, BaseMessage.class);
        for (Class<?> subType : s1) {
            if (Serializable.class.isAssignableFrom(subType)) {
                log.info(subType.getName());
            }
        }
        log.info("====================================================================================================");
        for (Class<?> subType : s1) {
            if (!Serializable.class.isAssignableFrom(subType)) {
                log.warn(subType.getName());
                log.warn(desc.get(subType));
            }
        }
    }

    //private void recursiveFind(Reflections reflections, Set<Class<?>> searchList, Class<?> clazz) {
    private <T> void recursiveFind(Reflections reflections,
                                   Set<Class<?>> searchList,
                                   Map<Class<?>, String> desc,
                                   Stack<Class<?>> stack,
                                   final Class<T> type) {
        stack.push(type);
        Set<Class<? extends T>> subTypes = reflections.getSubTypesOf(type);
        Field[] declaredFields = type.getDeclaredFields();
        Set<Class<?>> fields = new HashSet<>();
        for (Field f : declaredFields) {
            if (Modifier.isStatic(f.getModifiers())
                    || Modifier.isTransient(f.getModifiers())
                    || f.getType().isPrimitive())
                continue;
            fields.add(f.getType());
        }

        desc.put(type, stack.toString());
        searchList.add(type);

        for (Class<?> field : fields) {
            if (!searchList.contains(field)) {
                recursiveFind(reflections, searchList, desc, stack, field);
            }
        }

        for (Class<?> subType : subTypes) {
            if (!searchList.contains(subType)) {
                recursiveFind(reflections, searchList, desc, stack, subType);
            }
        }
        stack.pop();
    }
}
