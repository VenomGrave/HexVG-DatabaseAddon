package com.venomgrave.hexvg.database.util;

import java.math.BigDecimal;

public final class TypeNormalizer {

    private TypeNormalizer() {}

    public static Object normalize(Object value) {
        if (value instanceof BigDecimal) {
            BigDecimal bd = (BigDecimal) value;
            if (bd.scale() <= 0) return bd.longValue();
            return bd.doubleValue();
        }
        if (value instanceof Integer) return ((Integer) value).longValue();
        if (value instanceof Float) return ((Float) value).doubleValue();
        return value;
    }
}
