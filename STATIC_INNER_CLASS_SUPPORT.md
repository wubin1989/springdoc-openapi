# Static Inner Class Support in SpringDoc OpenAPI

## Problem Description

Prior to this fix, SpringDoc OpenAPI had an issue with static inner classes in DTO objects. When different DTOs contained static inner classes with the same simple name (e.g., `Request`, `Response`, `Data`), these classes would overwrite each other in the generated OpenAPI schema definitions, leading to incorrect API documentation.

### Example of the Problem

```java
public class UserDTO {
    public static class Request {
        private String username;
        private String email;
    }
    
    public static class Response {
        private String userId;
        private String message;
    }
}

public class ProductDTO {
    public static class Request {
        private String productName;
        private Double price;
    }
    
    public static class Response {
        private String productId;
        private String message;
    }
}
```

In the above example, both `UserDTO` and `ProductDTO` have inner classes named `Request` and `Response`. Before the fix, the OpenAPI schema would only contain one definition for `Request` and one for `Response`, causing the schemas to overwrite each other.

## Solution

The fix introduces a new `StaticInnerClassModelConverter` that:

1. Detects static inner classes (nested classes with the `static` modifier)
2. Generates qualified schema names for these classes to avoid naming conflicts
3. Uses the format `OuterClassName.InnerClassName` (e.g., `UserDTO.Request`, `ProductDTO.Request`)

### Key Components

#### StaticInnerClassModelConverter

Located at: `springdoc-openapi-common/src/main/java/org/springdoc/core/converters/StaticInnerClassModelConverter.java`

This converter:
- Intercepts schema resolution for all types
- Identifies static inner classes by checking for an enclosing class and the `static` modifier
- Tracks registered inner class names to detect potential conflicts
- Generates qualified names by traversing the class hierarchy
- Updates schema names and references in the model context

#### Configuration

The converter is automatically registered as a Spring bean in `SpringDocConfiguration`:

```java
@Bean
@ConditionalOnMissingBean
@Lazy(false)
StaticInnerClassModelConverter staticInnerClassModelConverter(ObjectMapperProvider objectMapperProvider) {
    return new StaticInnerClassModelConverter(objectMapperProvider);
}
```

## Result

After the fix, the OpenAPI schema correctly distinguishes between inner classes from different outer classes:

```json
{
  "components": {
    "schemas": {
      "UserDTO.Request": {
        "type": "object",
        "properties": {
          "username": { "type": "string" },
          "email": { "type": "string" }
        }
      },
      "UserDTO.Response": {
        "type": "object",
        "properties": {
          "userId": { "type": "string" },
          "message": { "type": "string" }
        }
      },
      "ProductDTO.Request": {
        "type": "object",
        "properties": {
          "productName": { "type": "string" },
          "price": { "type": "number" }
        }
      },
      "ProductDTO.Response": {
        "type": "object",
        "properties": {
          "productId": { "type": "string" },
          "message": { "type": "string" }
        }
      }
    }
  }
}
```

## Testing

A comprehensive test suite has been added to verify the fix:

- **Unit Test**: `StaticInnerClassModelConverterTest` in `springdoc-openapi-common`
- **Integration Test**: `app201` test suite in `springdoc-openapi-webmvc-core`

The integration test includes:
- `UserDTO` and `ProductDTO` with same-named static inner classes
- Controller methods using these DTOs
- Expected OpenAPI schema with properly qualified names

## Backward Compatibility

This fix is backward compatible and does not affect:
- Regular classes (non-inner classes)
- Non-static inner classes
- Existing schemas without naming conflicts

The converter only activates when:
1. A class is a static inner class (has an enclosing class and is static)
2. There's a potential for naming conflict with other static inner classes

## Disabling the Feature

If needed, the converter can be disabled by providing a custom bean:

```java
@Configuration
public class CustomConfig {
    @Bean
    StaticInnerClassModelConverter staticInnerClassModelConverter(ObjectMapperProvider objectMapperProvider) {
        // Return a no-op implementation or null
        return null;
    }
}
```

Or by using the `@ConditionalOnMissingBean` mechanism to provide your own implementation.

