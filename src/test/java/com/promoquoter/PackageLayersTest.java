package com.promoquoter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class PackageLayersTest {

    private static final Path MAIN_SOURCES = Path.of("src/main/java");
    private static final Pattern PACKAGE = Pattern.compile("^package\\s+([\\w.]+);", Pattern.MULTILINE);
    private static final Pattern IMPORT = Pattern
            .compile("^import\\s+(?:static\\s+)?(com\\.promoquoter\\.[\\w.]+);", Pattern.MULTILINE);

    private static final Map<String, Set<String>> FEATURE_DEPENDENCIES = Map.of(
            "product", Set.of("common"),
            "pricing", Set.of("common"),
            "promotion", Set.of("common", "pricing", "product"),
            "cart", Set.of("common", "pricing", "product", "promotion"),
            "order", Set.of("common", "pricing", "product", "cart"));

    private static final Map<String, Set<String>> PRICING_LAYER_DEPENDENCIES = Map.of(
            "model", Set.of(),
            "condition", Set.of("model"),
            "engine", Set.of("model", "condition"),
            "rules", Set.of("model", "condition", "engine"));

    private record Location(String feature, String layer) {

        static Location of(String packageName) {
            String[] parts = packageName.split("\\.");
            String feature = parts.length > 2 ? parts[2] : "";
            String layer = parts.length > 3 ? parts[3] : "";
            return new Location(feature, layer);
        }
    }

    static String violation(String importerPackage, String importedPackage) {
        Location from = Location.of(importerPackage);
        Location to = Location.of(importedPackage);
        if (from.feature().isEmpty() || from.feature().equals("common") || to.feature().isEmpty()) {
            return null;
        }
        if (from.feature().equals(to.feature())) {
            return sameFeatureViolation(from, to);
        }
        if (!FEATURE_DEPENDENCIES.getOrDefault(from.feature(), Set.of()).contains(to.feature())) {
            return from.feature() + " must not depend on " + to.feature();
        }
        if (to.feature().equals("common")) {
            return null;
        }
        return crossFeatureViolation(from, to);
    }

    private static String sameFeatureViolation(Location from, Location to) {
        if (from.feature().equals("pricing")) {
            Set<String> allowed = PRICING_LAYER_DEPENDENCIES.get(from.layer());
            if (allowed != null && !from.layer().equals(to.layer()) && !allowed.contains(to.layer())) {
                return "pricing." + from.layer() + " must not depend on pricing." + to.layer();
            }
            return null;
        }
        if (from.layer().equals("dto") && !to.layer().equals("dto")) {
            return from.feature() + ".dto must only depend on dto types, not " + to.layer();
        }
        if (from.layer().equals("domain") && to.layer().equals("api")) {
            return from.feature() + ".domain must not depend on api";
        }
        return null;
    }

    private static String crossFeatureViolation(Location from, Location to) {
        if (to.feature().equals("pricing")) {
            if (from.layer().equals("dto") || from.layer().equals("api")) {
                return to.layer().equals("model") ? null
                        : from.feature() + "." + from.layer() + " may only use pricing.model, not pricing." + to.layer();
            }
            return null;
        }
        if (to.layer().equals("api")) {
            return from.feature() + " must not use " + to.feature() + ".api";
        }
        if (from.layer().equals("dto") && !to.layer().equals("dto")) {
            return from.feature() + ".dto may only use other features' dto types, not " + to.feature() + "." + to.layer();
        }
        if (from.layer().equals("api") && !to.layer().equals("dto")) {
            return from.feature() + ".api may only use other features' dto types, not " + to.feature() + "." + to.layer();
        }
        return null;
    }

    @Test
    void everySourceFileFollowsTheLayerRules() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file);
                Matcher packageMatch = PACKAGE.matcher(text);
                assertThat(packageMatch.find()).as(file.toString()).isTrue();
                String importer = packageMatch.group(1);
                Matcher imports = IMPORT.matcher(text);
                while (imports.find()) {
                    String imported = imports.group(1);
                    String importedPackage = imported.substring(0, imported.lastIndexOf('.'));
                    if (Character.isUpperCase(importedPackage.charAt(importedPackage.lastIndexOf('.') + 1))) {
                        importedPackage = importedPackage.substring(0, importedPackage.lastIndexOf('.'));
                    }
                    String reason = violation(importer, importedPackage);
                    if (reason != null) {
                        violations.add(file.getFileName() + " imports " + imported + ": " + reason);
                    }
                }
            }
        }

        assertThat(violations).isEmpty();
    }

    @Test
    void featuresMustFollowTheDependencyChain() {
        assertThat(violation("com.promoquoter.product.domain", "com.promoquoter.order.domain"))
                .contains("product must not depend on order");
        assertThat(violation("com.promoquoter.promotion.domain", "com.promoquoter.cart.domain"))
                .contains("promotion must not depend on cart");
        assertThat(violation("com.promoquoter.pricing.engine", "com.promoquoter.promotion.dto"))
                .contains("pricing must not depend on promotion");
        assertThat(violation("com.promoquoter.order.domain", "com.promoquoter.cart.domain")).isNull();
        assertThat(violation("com.promoquoter.cart.domain", "com.promoquoter.product.domain")).isNull();
        assertThat(violation("com.promoquoter.order.domain", "com.promoquoter.common.error")).isNull();
    }

    @Test
    void dtoTypesDoNotDependOnDomainOrApi() {
        assertThat(violation("com.promoquoter.product.dto", "com.promoquoter.product.domain"))
                .contains("dto must only depend on dto");
        assertThat(violation("com.promoquoter.order.dto", "com.promoquoter.cart.domain"))
                .contains("may only use other features' dto");
        assertThat(violation("com.promoquoter.order.dto", "com.promoquoter.cart.dto")).isNull();
        assertThat(violation("com.promoquoter.promotion.dto", "com.promoquoter.pricing.model")).isNull();
        assertThat(violation("com.promoquoter.promotion.dto", "com.promoquoter.pricing.engine"))
                .contains("may only use pricing.model");
    }

    @Test
    void domainNeverDependsOnApiAndApiOnlyUsesOtherFeaturesDtos() {
        assertThat(violation("com.promoquoter.product.domain", "com.promoquoter.product.api"))
                .contains("must not depend on api");
        assertThat(violation("com.promoquoter.cart.domain", "com.promoquoter.promotion.api"))
                .contains("must not use promotion.api");
        assertThat(violation("com.promoquoter.order.api", "com.promoquoter.cart.domain"))
                .contains("api may only use other features' dto");
        assertThat(violation("com.promoquoter.order.api", "com.promoquoter.order.domain")).isNull();
        assertThat(violation("com.promoquoter.order.api", "com.promoquoter.cart.dto")).isNull();
    }

    @Test
    void pricingLayersFormAOneWayChain() {
        assertThat(violation("com.promoquoter.pricing.model", "com.promoquoter.pricing.engine"))
                .contains("pricing.model must not depend on pricing.engine");
        assertThat(violation("com.promoquoter.pricing.condition", "com.promoquoter.pricing.engine"))
                .contains("pricing.condition must not depend on pricing.engine");
        assertThat(violation("com.promoquoter.pricing.engine", "com.promoquoter.pricing.rules"))
                .contains("pricing.engine must not depend on pricing.rules");
        assertThat(violation("com.promoquoter.pricing.rules", "com.promoquoter.pricing.engine")).isNull();
        assertThat(violation("com.promoquoter.pricing.engine", "com.promoquoter.pricing.condition")).isNull();
        assertThat(violation("com.promoquoter.pricing.condition", "com.promoquoter.pricing.model")).isNull();
    }
}
