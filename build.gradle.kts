// Minimum versions for libraries the build tools pull in transitively (see the
// security floors in gradle/libs.versions.toml). A constraint only raises a
// version: if a tool already needs a newer one, that one wins.
buildscript {
    dependencies {
        constraints {
            classpath(libs.floor.guava)
            classpath(libs.floor.bcprov)
            classpath(libs.floor.bcpkix)
            classpath(libs.floor.bcutil)
            classpath(libs.floor.jose4j)
            classpath(libs.floor.jdom2)
            classpath(libs.floor.commons.lang3)
            classpath(libs.floor.httpclient)
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// The same floors for Android lint's own classpath (AGP resolves it per project).
val securityFloors = listOf(
    libs.floor.guava, libs.floor.bcprov, libs.floor.bcpkix, libs.floor.bcutil,
    libs.floor.jose4j, libs.floor.jdom2, libs.floor.commons.lang3, libs.floor.httpclient,
)
subprojects {
    configurations.matching { it.name == "androidLintTool" }.configureEach {
        securityFloors.forEach { dependencyConstraints.add(project.dependencies.constraints.create(it.get())) }
    }
}
