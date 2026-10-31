package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.controller;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Strict JSON reader for productmanagement request DTOs only (interfaces "API conventions": DTOs
 * reject unknown, missing and wrong-type fields). Other features keep the application-wide Jackson
 * settings; this converter never writes responses.
 */
public class CatalogueStrictJsonConverter extends MappingJackson2HttpMessageConverter {

    private static final String MODULE_PACKAGE = "edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement";

    public CatalogueStrictJsonConverter(ObjectMapper applicationMapper) {
        super(strict(applicationMapper));
    }

    static ObjectMapper strict(ObjectMapper applicationMapper) {
        ObjectMapper mapper = applicationMapper.copy();
        mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
        mapper.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        mapper.coercionConfigFor(LogicalType.Integer)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        mapper.coercionConfigFor(LogicalType.Boolean)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
        mapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        return mapper;
    }

    @Override
    public boolean canRead(Class<?> clazz, MediaType mediaType) {
        return belongsToModule(clazz) && super.canRead(clazz, mediaType);
    }

    @Override
    public boolean canRead(Type type, Class<?> contextClass, MediaType mediaType) {
        return belongsToModule(type) && super.canRead(type, contextClass, mediaType);
    }

    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
        return false;
    }

    @Override
    public boolean canWrite(Type type, Class<?> clazz, MediaType mediaType) {
        return false;
    }

    private static boolean belongsToModule(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz.getName().startsWith(MODULE_PACKAGE + ".");
        }
        if (type instanceof ParameterizedType parameterized) {
            return belongsToModule(parameterized.getRawType());
        }
        return false;
    }
}
