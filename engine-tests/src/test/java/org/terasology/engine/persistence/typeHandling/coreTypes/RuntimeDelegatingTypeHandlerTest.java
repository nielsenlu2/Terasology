// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.engine.persistence.typeHandling.coreTypes;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.terasology.engine.core.module.ModuleManager;
import org.terasology.engine.persistence.typeHandling.PersistedData;
import org.terasology.engine.persistence.typeHandling.PersistedDataSerializer;
import org.terasology.engine.persistence.typeHandling.TypeHandler;
import org.terasology.engine.persistence.typeHandling.TypeHandlerContext;
import org.terasology.engine.persistence.typeHandling.TypeHandlerLibrary;
import org.terasology.engine.persistence.typeHandling.inMemory.AbstractPersistedData;
import org.terasology.engine.persistence.typeHandling.inMemory.PersistedMap;
import org.terasology.engine.persistence.typeHandling.inMemory.PersistedString;
import org.terasology.engine.persistence.typeHandling.reflection.ReflectionsSandbox;
import org.terasology.gestalt.naming.Name;
import org.terasology.nui.reflection.TypeInfo;
import org.terasology.testUtil.ModuleManagerFactory;
import org.terasology.unittest.typeHandling.runtimeDelegate.Base;
import org.terasology.unittest.typeHandling.runtimeDelegate.Sub;
import org.terasology.unittest.typeHandling.runtimeDelegate.SubHandler;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RuntimeDelegatingTypeHandlerTest {
    private final TypeHandlerLibrary typeHandlerLibrary = mock(TypeHandlerLibrary.class);
    ModuleManager moduleManager = ModuleManagerFactory.create();
    private final TypeHandlerContext context =
            new TypeHandlerContext(typeHandlerLibrary,
                    new ReflectionsSandbox(moduleManager.getEnvironment().get(new Name("unittest")).getModuleManifest()));

    private TypeHandler baseTypeHandler;
    private TypeHandler subTypeHandler;
    private Class<Sub> subType;
    private Type baseType;
    private RuntimeDelegatingTypeHandler<Base> runtimeDelegatingTypeHandler;

    private void setupHandlers() {
        subType = Sub.class;
        baseType = TypeInfo.of(Base.class).getType();

        baseTypeHandler = mockTypeHandler();
        subTypeHandler = mockTypeHandler(SubHandler.class);

        when(typeHandlerLibrary.getTypeHandler(eq(baseType)))
            .thenReturn(Optional.of(baseTypeHandler));

        when(typeHandlerLibrary.getTypeHandler(eq((Type) subType)))
            .thenReturn(Optional.of(subTypeHandler));

        runtimeDelegatingTypeHandler = new RuntimeDelegatingTypeHandler<Base>(baseTypeHandler, TypeInfo.of(Base.class), context);
    }

    private static TypeHandler<?> mockTypeHandler(Class<? extends TypeHandler> subHandlerClass) {
        TypeHandler<?> mocked = mock(subHandlerClass);

        when(mocked.serialize(any(), any())).thenReturn(new AbstractPersistedData() {
            @Override
            public boolean isNull() {
                return true;
            }
        });

        return mocked;
    }

    private static TypeHandler<?> mockTypeHandler() {
        return mockTypeHandler(TypeHandler.class);
    }

    @Test
    public void testSerializeBase() {
        PersistedDataSerializer serializer = mock(PersistedDataSerializer.class);
        when(serializer.serialize(any(String.class)))
            .then(invocation -> new PersistedString((String) invocation.getArguments()[0]));

        setupHandlers();

        Base base = new Base();
        runtimeDelegatingTypeHandler.serialize(base, serializer);

        verify(typeHandlerLibrary, never()).getTypeHandler(eq((Type) subType));

        verify(baseTypeHandler).serialize(any(), any());
        verify(subTypeHandler, never()).serialize(any(), any());

        verify(serializer, never()).serialize(
            argThat((ArgumentMatcher<Map<String, PersistedData>>) argument -> {
                return argument.containsKey(RuntimeDelegatingTypeHandler.TYPE_FIELD);
            })
        );
    }

    @Test
    public void testSerializeSub() {
        PersistedDataSerializer serializer = mock(PersistedDataSerializer.class);
        when(serializer.serialize(any(String.class)))
            .then(invocation -> new PersistedString((String) invocation.getArguments()[0]));

        setupHandlers();

        Base sub = new Sub();
        runtimeDelegatingTypeHandler.serialize(sub, serializer);

        verify(typeHandlerLibrary, never()).getTypeHandler(eq(baseType));
        verify(typeHandlerLibrary).getTypeHandler(eq((Type) subType));

        verify(baseTypeHandler, never()).serialize(any(), any());
        verify(subTypeHandler).serialize(any(), any());

        verify(serializer).serialize(
            argThat((ArgumentMatcher<Map<String, PersistedData>>) argument -> {
                return argument.get(RuntimeDelegatingTypeHandler.TYPE_FIELD)
                           .getAsString()
                           .equals(subType.getName()) &&
                           argument.containsKey(RuntimeDelegatingTypeHandler.VALUE_FIELD);
            })
        );
    }

    @Test
    public void testDeserializeBase() {
        setupHandlers();

        PersistedData persistedBase = new PersistedMap(ImmutableMap.of());

        runtimeDelegatingTypeHandler.deserialize(persistedBase);

        verify(typeHandlerLibrary, never()).getTypeHandler(eq((Type) subType));

        verify(baseTypeHandler).deserialize(any());
        verify(subTypeHandler, never()).deserialize(any());
    }

    @Test
    public void testDeserializeSub() {
        setupHandlers();

        PersistedData persistedSub = new PersistedMap(
            ImmutableMap.of(
                RuntimeDelegatingTypeHandler.TYPE_FIELD,
                new PersistedString(subType.getName()),
                RuntimeDelegatingTypeHandler.VALUE_FIELD,
                new PersistedMap(ImmutableMap.of())
            )
        );

        runtimeDelegatingTypeHandler.deserialize(persistedSub);

        verify(typeHandlerLibrary, never()).getTypeHandler(eq(baseType));
        verify(typeHandlerLibrary).getTypeHandler(eq((Type) subType));

        verify(baseTypeHandler, never()).deserialize(any());
        verify(subTypeHandler).deserialize(any());
    }

    @Test
    public void testDeserializeNonSub() {
        setupHandlers();

        PersistedData persistedData = new PersistedMap(
            ImmutableMap.of(
                RuntimeDelegatingTypeHandler.TYPE_FIELD,
                new PersistedString(Integer.class.getName()),
                RuntimeDelegatingTypeHandler.VALUE_FIELD,
                new PersistedMap(ImmutableMap.of())
            )
        );

        Optional<Base> deserialized = runtimeDelegatingTypeHandler.deserialize(persistedData);

        assertFalse(deserialized.isPresent());

        verify(typeHandlerLibrary, never()).getTypeHandler(eq(baseType));
        verify(typeHandlerLibrary, never()).getTypeHandler(eq((Type) subType));
        verify(typeHandlerLibrary, never()).getTypeHandler(eq((Type) Integer.class));

        verify(subTypeHandler, never()).deserialize(any());
        // Serializes using base type handler
        verify(baseTypeHandler).deserialize(any());
    }
}
