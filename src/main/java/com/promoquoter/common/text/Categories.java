package com.promoquoter.common.text;

import java.util.Locale;

public final class Categories {

    private Categories() {
    }

    public static String normalize(String category) {
        return category.trim().toUpperCase(Locale.ROOT);
    }
}
