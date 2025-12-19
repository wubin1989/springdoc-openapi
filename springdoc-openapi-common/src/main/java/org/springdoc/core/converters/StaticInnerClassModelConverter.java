/*
 *
 *  *
 *  *  *
 *  *  *  * Copyright 2019-2022 the original author or authors.
 *  *  *  *
 *  *  *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  *  *  * you may not use this file except in compliance with the License.
 *  *  *  * You may obtain a copy of the License at
 *  *  *  *
 *  *  *  *      https://www.apache.org/licenses/LICENSE-2.0
 *  *  *  *
 *  *  *  * Unless required by applicable law or agreed to in writing, software
 *  *  *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  *  *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  *  * See the License for the specific language governing permissions and
 *  *  *  * limitations under the License.
 *  *  *
 *  *
 *
 */

package org.springdoc.core.converters;

import java.lang.reflect.Modifier;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JavaType;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.jackson.TypeNameResolver;
import io.swagger.v3.oas.models.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.providers.ObjectMapperProvider;

/**
 * The type Static inner class model converter.
 * This converter handles static inner classes to avoid naming conflicts
 * when different outer classes have static inner classes with the same simple name.
 *
 * @author bnasslahsen
 */
public class StaticInnerClassModelConverter implements ModelConverter {

    /**
     * The constant LOGGER.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(StaticInnerClassModelConverter.class);

    /**
     * The Spring doc object mapper.
     */
    private final ObjectMapperProvider springDocObjectMapper;

    /**
     * Cache to store mapping from simple name to full qualified name for static inner classes.
     * This helps track which inner classes have been registered and their qualified names.
     */
    private static final ConcurrentHashMap<String, String> REGISTERED_INNER_CLASS_NAMES = new ConcurrentHashMap<>();

    /**
     * Cache to store class to schema name mapping.
     */
    private static final ConcurrentHashMap<Class<?>, String> CLASS_TO_SCHEMA_NAME = new ConcurrentHashMap<>();

    /**
     * Instantiates a new Static inner class model converter.
     *
     * @param springDocObjectMapper the spring doc object mapper
     */
    public StaticInnerClassModelConverter(ObjectMapperProvider springDocObjectMapper) {
        this.springDocObjectMapper = springDocObjectMapper;
    }

    @Override
    public Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        JavaType javaType = springDocObjectMapper.jsonMapper().constructType(type.getType());
        if (javaType != null) {
            Class<?> cls = javaType.getRawClass();

            // Check if this is a static inner class
            if (isStaticInnerClass(cls)) {
                String qualifiedName = getQualifiedSchemaName(cls);
                String simpleName = cls.getSimpleName();

                LOGGER.debug("Processing static inner class: {} with qualified name: {}", cls.getName(), qualifiedName);

                // Store the mapping from class to qualified schema name
                CLASS_TO_SCHEMA_NAME.put(cls, qualifiedName);

                // Check if already resolved with qualified name
                if (context.getDefinedModels().containsKey(qualifiedName)) {
                    return new Schema().$ref("#/components/schemas/" + qualifiedName);
                }

                // Create annotated type with modified name hint
                AnnotatedType modifiedType = new AnnotatedType(type.getType())
                    .jsonViewAnnotation(type.getJsonViewAnnotation())
                    .ctxAnnotations(type.getCtxAnnotations())
                    .parent(type.getParent())
                    .schemaProperty(type.isSchemaProperty())
                    .name(qualifiedName)
                    .resolveAsRef(type.isResolveAsRef());

                // Copy property name if available
                if (type.getPropertyName() != null) {
                    modifiedType.propertyName(type.getPropertyName());
                }

                // Resolve with chain
                Schema<?> resolvedSchema = (chain.hasNext()) ? chain.next().resolve(modifiedType, context, chain) : null;

                if (resolvedSchema != null) {
                    // Update schema name and reference
                    if (resolvedSchema.get$ref() != null) {
                        String ref = resolvedSchema.get$ref();
                        if (ref.endsWith("/" + simpleName)) {
                            resolvedSchema.set$ref("#/components/schemas/" + qualifiedName);
                        }
                    }

                    // Update the schema name
                    if (simpleName.equals(resolvedSchema.getName())) {
                        resolvedSchema.setName(qualifiedName);
                    }

                    // Check and update in context
                    updateContextModels(context, simpleName, qualifiedName, cls);
                }

                return resolvedSchema;
            }
        }

        // Not a static inner class or no conflict, proceed with normal processing
        return (chain.hasNext()) ? chain.next().resolve(type, context, chain) : null;
    }

    /**
     * Update context models to replace simple name with qualified name for inner classes.
     *
     * @param context       the model converter context
     * @param simpleName    the simple class name
     * @param qualifiedName the qualified schema name
     * @param cls           the class
     */
    private void updateContextModels(ModelConverterContext context, String simpleName, String qualifiedName, Class<?> cls) {
        Map<String, Schema> definedModels = context.getDefinedModels();

        // If there's a model with simple name, we need to check if it should be renamed
        if (definedModels.containsKey(simpleName)) {
            Schema existingSchema = definedModels.get(simpleName);

            // Check if we should rename this schema
            // This is tricky because we need to ensure we're dealing with the right class
            // For safety, we'll only rename if the qualified name is not already present
            if (!definedModels.containsKey(qualifiedName)) {
                definedModels.remove(simpleName);
                existingSchema.setName(qualifiedName);
                definedModels.put(qualifiedName, existingSchema);
                LOGGER.debug("Renamed schema from {} to {} in context", simpleName, qualifiedName);
            }
        }
    }

    /**
     * Check if a class is a static inner class (static nested class).
     *
     * @param cls the class to check
     * @return true if the class is a static inner class
     */
    private boolean isStaticInnerClass(Class<?> cls) {
        // Check if it has an enclosing class
        Class<?> enclosingClass = cls.getEnclosingClass();
        if (enclosingClass == null) {
            return false;
        }

        // Check if it's static
        // Member classes (inner classes) are not static, but nested classes are static
        return Modifier.isStatic(cls.getModifiers());
    }

    /**
     * Generate a qualified schema name for a static inner class.
     * The name format is "OuterClassName.InnerClassName" to avoid conflicts.
     *
     * @param cls the static inner class
     * @return the qualified schema name
     */
    private String getQualifiedSchemaName(Class<?> cls) {
        StringBuilder nameBuilder = new StringBuilder();
        buildQualifiedSimpleName(cls, nameBuilder);
        return nameBuilder.toString();
    }

    /**
     * Build a qualified simple name by traversing the enclosing class hierarchy.
     * For example: OuterClass.MiddleClass.InnerClass
     *
     * @param cls         the class
     * @param nameBuilder the string builder to append the name to
     */
    private void buildQualifiedSimpleName(Class<?> cls, StringBuilder nameBuilder) {
        Class<?> enclosingClass = cls.getEnclosingClass();
        if (enclosingClass != null) {
            // Recursively build the outer class name
            buildQualifiedSimpleName(enclosingClass, nameBuilder);
            nameBuilder.append(".");
        }
        nameBuilder.append(cls.getSimpleName());
    }

    /**
     * Get the schema name for a class if it's been registered.
     *
     * @param cls the class
     * @return the schema name or the simple name if not registered
     */
    public static String getSchemaName(Class<?> cls) {
        return CLASS_TO_SCHEMA_NAME.getOrDefault(cls, cls.getSimpleName());
    }

    /**
     * Clear the caches. This is useful for testing.
     */
    public static void clearCache() {
        REGISTERED_INNER_CLASS_NAMES.clear();
        CLASS_TO_SCHEMA_NAME.clear();
    }
}

