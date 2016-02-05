/**
 Copyright 2014 Evan Tatarka

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package me.tatarka

import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test

import static me.tatarka.RetrolambdaPlugin.checkIfExecutableExists

/**
 * Created with IntelliJ IDEA.
 * User: evan
 * Date: 8/4/13
 * Time: 1:36 PM
 * To change this template use File | Settings | File Templates.
 */
@CompileStatic
public class RetrolambdaPluginJava implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.afterEvaluate {
            def retrolambda = project.extensions.getByType(RetrolambdaExtension)
            def javaPlugin = project.convention.getPlugin(JavaPluginConvention)

            javaPlugin.sourceSets.all { SourceSet set ->
                if (retrolambda.isIncluded(set.name)) {
                    def name = set.name.capitalize()
                    def taskName = "compileRetrolambda$name"
                    def oldOutputDir = set.output.classesDir
                    def newOutputDir = project.file("$project.buildDir/retrolambda/$set.name")

                    def compileJavaTask = project.tasks.getByName(set.compileJavaTaskName) as JavaCompile
                    compileJavaTask.destinationDir = newOutputDir

                    def retrolambdaTask = project.task(taskName, dependsOn: compileJavaTask, type: RetrolambdaTask) { RetrolambdaTask task ->
                        task.inputDir = newOutputDir
                        task.outputDir = oldOutputDir
                        task.classpath = set.compileClasspath + project.files(newOutputDir)
                        task.javaVersion = retrolambda.javaVersion
                        task.jvmArgs = retrolambda.jvmArgs
                        task.enabled = !set.allJava.isEmpty()
                    }

                    // enable retrolambdaTask dynamically, based on up-to-date source set before running 
                    project.gradle.taskGraph.beforeTask { Task task ->
                        if (task == retrolambdaTask) {
                            retrolambdaTask.setEnabled(!set.allJava.isEmpty())
                        }
                    }

                    project.tasks.findByName(set.classesTaskName).dependsOn(retrolambdaTask)

                    if (!retrolambda.onJava8) {
                        // Set JDK 8 for compiler task
                        compileJavaTask.doFirst {
                            compileJavaTask.options.fork = true
                            compileJavaTask.options.forkOptions.executable = "${retrolambda.tryGetJdk()}/bin/javac"
                        }
                    }
                }
            }

            project.tasks.getByName("test").doFirst { Test task ->
                if (retrolambda.onJava8) {
                    //Ensure the tests run on java6/7
                    def oldJava = "${retrolambda.tryGetOldJdk()}/bin/java"
                    if (!checkIfExecutableExists(oldJava)) throw new ProjectConfigurationException("Cannot find executable: $oldJava", null)
                    task.executable oldJava
                }
            }
        }
    }
}
