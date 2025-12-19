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

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springdoc.core.SpringDocConfigProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.dto.RegularClass;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for StaticInnerClassModelConverter.
 */
class StaticInnerClassModelConverterTest {

    private StaticInnerClassModelConverter converter;

    private ObjectMapperProvider objectMapperProvider;

    @BeforeEach
    void setUp() {
        SpringDocConfigProperties springDocConfigProperties = new SpringDocConfigProperties();
        SpringDocConfigProperties.ApiDocs apiDocs = new SpringDocConfigProperties.ApiDocs();
        apiDocs.setVersion(SpringDocConfigProperties.ApiDocs.OpenApiVersion.OPENAPI_3_0);
        springDocConfigProperties.setApiDocs(apiDocs);
        objectMapperProvider = new ObjectMapperProvider(springDocConfigProperties);
        converter = new StaticInnerClassModelConverter(objectMapperProvider);
        StaticInnerClassModelConverter.clearCache();
    }

    @AfterEach
    void tearDown() {
        StaticInnerClassModelConverter.clearCache();
    }

    /**
     * Test that static inner classes from different outer classes with the same simple name
     * get unique schema names.
     */
    @Test
    void testStaticInnerClassNaming() {
        // Register the converter
        ModelConverters converters = ModelConverters.getInstance();
        converters.addConverter(converter);

        try {
            // Resolve schemas for both inner classes
            ResolvedSchema schema1 = converters.readAllAsResolvedSchema(
                new AnnotatedType(OuterClassA.InnerData.class).resolveAsRef(true)
            );
            ResolvedSchema schema2 = converters.readAllAsResolvedSchema(
                new AnnotatedType(OuterClassB.InnerData.class).resolveAsRef(true)
            );

            assertNotNull(schema1);
            assertNotNull(schema2);

            Map<String, Schema> allSchemas1 = schema1.referencedSchemas;
            Map<String, Schema> allSchemas2 = schema2.referencedSchemas;

            // Check that schemas are registered with qualified names
            // The first one might use simple name, but subsequent ones should use qualified names
            boolean hasQualifiedNameA = allSchemas1.containsKey("StaticInnerClassModelConverterTest.OuterClassA.InnerData") ||
                allSchemas2.containsKey("StaticInnerClassModelConverterTest.OuterClassA.InnerData");
            boolean hasQualifiedNameB = allSchemas1.containsKey("StaticInnerClassModelConverterTest.OuterClassB.InnerData") ||
                allSchemas2.containsKey("StaticInnerClassModelConverterTest.OuterClassB.InnerData");

            // At least one should have qualified names to avoid conflicts
            assertTrue(hasQualifiedNameA && hasQualifiedNameB,
                "Static inner classes should have qualified names to avoid conflicts");
        } finally {
            converters.removeConverter(converter);
        }
    }

    /**
     * Test that non-static inner classes or regular classes are not affected.
     */
    @Test
    void testRegularClassNotAffected() {
        ModelConverters converters = ModelConverters.getInstance();
        converters.addConverter(converter);

        try {
            ResolvedSchema schema = converters.readAllAsResolvedSchema(
                new AnnotatedType(RegularClass.class).resolveAsRef(true)
            );

            assertNotNull(schema);
            assertNotNull(schema.schema);

            // Regular class should use simple name
            Map<String, Schema> allSchemas = schema.referencedSchemas;
            assertTrue(allSchemas.containsKey("RegularClass"),
                "Regular class should use simple name");
        } finally {
            converters.removeConverter(converter);
        }
    }

    // Test classes
    static class OuterClassA {
        public static class InnerData {
            private String fieldA;

            public String getFieldA() {
                return fieldA;
            }

            public void setFieldA(String fieldA) {
                this.fieldA = fieldA;
            }
        }
    }

    static class OuterClassB {
        public static class InnerData {
            private String fieldB;

            public String getFieldB() {
                return fieldB;
            }

            public void setFieldB(String fieldB) {
                this.fieldB = fieldB;
            }
        }
    }
}

