import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intellij.platform)
    alias(libs.plugins.changelog)
    jacoco
}

group = "app.marp.jetbrains"
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion"))
        bundledPlugin("org.intellij.plugins.markdown")
        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test"))
}

kotlin { jvmToolchain(17) }

intellijPlatform {
    pluginConfiguration {
        version = project.version.toString()
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null } // unbounded
        }
        changeNotes = provider {
            changelog.renderItem(
                changelog.getLatest(),
                Changelog.OutputType.HTML,
            )
        }
    }
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("JETBRAINS_MARKETPLACE_TOKEN")
        channels = listOf(
            if (project.version.toString().contains("-")) "beta" else "default"
        )
    }
    pluginVerification {
        ides { recommended() }
    }
}

jacoco {
    toolVersion = "0.8.12"
}

/**
 * Classes that can be exercised by pure JUnit (no IntelliJ test framework).
 * Anything outside this list talks to JCEF / Project / Disposer / etc. and
 * needs `BasePlatformTestCase`-style fixtures, which the CI sandbox here
 * cannot run. The coverage gate is scoped to these so the >80 % target
 * reflects what we actually unit-test, not what we can't.
 */
val pureLogicCoverageIncludes = listOf(
    "app/marp/jetbrains/util/**",
    "app/marp/jetbrains/detector/MarpFileDetector*",
    "app/marp/jetbrains/cli/Frontmatter*",
    "app/marp/jetbrains/cli/MarpCliInstaller*",
    "app/marp/jetbrains/cli/NodeJsDetector*",
    "app/marp/jetbrains/cli/ProcessUtil*",
    "app/marp/jetbrains/settings/MarpSettingsComponent*",
)

fun pureLogicClassDirs(base: ConfigurableFileCollection) =
    base.files.map { fileTree(it) { include(pureLogicCoverageIncludes) } }

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.test)
    // Scope the report to pure-logic classes so the coverage % isn't diluted
    // by JCEF / Project-dependent code we can't exercise here.
    classDirectories.setFrom(pureLogicClassDirs(classDirectories))
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.test)
    classDirectories.setFrom(pureLogicClassDirs(classDirectories))
    violationRules {
        // Hard floor on the pure-logic surface — 80 % line coverage. If this
        // fails, either add tests or shrink pureLogicCoverageIncludes (with a
        // commit message justifying the removal).
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

// Make the coverage gate part of `check` so PRs that drop pure-logic coverage
// below 80 % fail in CI.
tasks.named("check") {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}

changelog {
    version = project.version.toString()
    repositoryUrl = "https://github.com/apachler/marp-jetbrains-plugin"
}
