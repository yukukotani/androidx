/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.baselineprofiles.gradle.apkprovider

import androidx.baselineprofiles.gradle.utils.GRADLE_CODE_PRINT_TASK
import androidx.testutils.gradle.ProjectSetupRule
import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class BaselineProfilesApkProviderPluginTest {

    @get:Rule
    val projectSetup = ProjectSetupRule()

    private lateinit var gradleRunner: GradleRunner

    @Before
    fun setUp() {
        gradleRunner = GradleRunner.create()
            .withProjectDir(projectSetup.rootDir)
            .withPluginClasspath()
    }

    @Test
    fun verifyBuildTypes() {
        projectSetup.writeDefaultBuildGradle(
            prefix = """
                plugins {
                    id("com.android.application")
                    id("androidx.baselineprofiles.apkprovider")
                }
                android {
                    namespace 'com.example.namespace'
                    buildTypes {
                        anotherRelease { initWith(release) }
                    }
                }

                $GRADLE_CODE_PRINT_TASK

                def registerTask(buildTypeName, taskName) {
                    tasks.register(taskName, PrintTask) { t ->
                        def buildType = android.buildTypes[buildTypeName]
                        def text = "minifyEnabled=" + buildType.minifyEnabled.toString() + "\n"
                        text += "testCoverageEnabled=" + buildType.testCoverageEnabled.toString() + "\n"
                        text += "debuggable=" + buildType.debuggable.toString() + "\n"
                        text += "profileable=" + buildType.profileable.toString() + "\n"
                        t.text.set(text)
                    }
                }
                registerTask("nonMinifiedRelease", "printNonMinifiedReleaseBuildType")
                registerTask("nonMinifiedAnotherRelease", "printNonMinifiedAnotherReleaseBuildType")
            """.trimIndent(),
            suffix = ""
        )

        val runGradleAndAssertOutput: (String, (String) -> (Unit)) -> (Unit) =
            { taskName, assertBlock ->
                gradleRunner
                    .withArguments(taskName, "--stacktrace")
                    .build()
                    .output
                    .let(assertBlock)
            }

        arrayOf("printNonMinifiedReleaseBuildType", "printNonMinifiedAnotherReleaseBuildType")
            .forEach { taskName ->
                runGradleAndAssertOutput(taskName) {
                    assertThat(it).contains("minifyEnabled=false")
                    assertThat(it).contains("testCoverageEnabled=false")
                    assertThat(it).contains("debuggable=false")
                    assertThat(it).contains("profileable=true")
                }
            }
    }
}
