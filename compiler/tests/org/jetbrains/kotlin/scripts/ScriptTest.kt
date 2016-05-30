/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.scripts

import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.*
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoot
import org.jetbrains.kotlin.codegen.CompilationException
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.addKotlinSourceRoot
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.script.KotlinScriptDefinition
import org.jetbrains.kotlin.script.ScriptParameter
import org.jetbrains.kotlin.script.StandardScriptDefinition
import org.jetbrains.kotlin.test.ConfigurationKind
import org.jetbrains.kotlin.test.KotlinTestUtils
import org.jetbrains.kotlin.test.TestJdkKind
import org.jetbrains.kotlin.utils.PathUtil
import org.junit.Assert
import org.junit.Test
import java.io.File
import kotlin.reflect.defaultType

class ScriptTest {
    @Test
    @Throws(Exception::class)
    fun testScriptWithParam() {
        val aClass = compileScript("fib.kts", SimpleParamsTestScriptDefinition(".kts", numIntParam()))
        Assert.assertNotNull(aClass)
        aClass!!.getConstructor(Integer.TYPE).newInstance(4)
    }

    @Test
    @Throws(Exception::class)
    fun testStandardScript() {
        val aClass = compileScript("fib_std.kts", StandardScriptDefinition)
        Assert.assertNotNull(aClass)
        val anObj = KotlinToJVMBytecodeCompiler.tryConstructClassPub(aClass!!.kotlin, listOf("4", "comment"))
        Assert.assertNotNull(anObj)
    }

    @Test
    @Throws(Exception::class)
    fun testScriptWithParamConversion() {
        val aClass = compileScript("fib.kts", SimpleParamsTestScriptDefinition(".kts", numIntParam()))
        Assert.assertNotNull(aClass)
        val anObj = KotlinToJVMBytecodeCompiler.tryConstructClassPub(aClass!!.kotlin, listOf("4"))
        Assert.assertNotNull(anObj)
    }

    @Test
    @Throws(Exception::class)
    fun testScriptWithPackage() {
        val aClass = compileScript("fib.pkg.kts", SimpleParamsTestScriptDefinition(".kts", numIntParam()))
        Assert.assertNotNull(aClass)
        aClass!!.getConstructor(Integer.TYPE).newInstance(4)
    }

    @Test
    @Throws(Exception::class)
    fun testScriptWithScriptDefinition() {
        val aClass = compileScript("fib.fib.kt", SimpleParamsTestScriptDefinition(".fib.kt", numIntParam()))
        Assert.assertNotNull(aClass)
        aClass!!.getConstructor(Integer.TYPE).newInstance(4)
    }

    @Test
    fun testScriptWithClassParameter() {
        val aClass = compileScript("fib_cp.kts", ReflectedParamClassTestScriptDefinition(".kts", "param", TestParamClass::class), runIsolated = false)
        Assert.assertNotNull(aClass)
        aClass!!.getConstructor(TestParamClass::class.java).newInstance(TestParamClass(4))
    }

    @Test
    fun testScriptWithBaseClass() {
        val aClass = compileScript("fib_dsl.kts", ReflectedSuperclassTestScriptDefinition(".kts", numIntParam(), TestDSLClass::class), runIsolated = false)
        Assert.assertNotNull(aClass)
        aClass!!.getConstructor(Integer.TYPE).newInstance(4)
    }

    @Test
    fun testScriptWithBaseClassWithParam() {
        val aClass = compileScript("fib_dsl.kts", ReflectedSuperclassWithParamsTestScriptDefinition(".kts", numIntParam() + numIntParam("passthrough"), TestDSLClassWithParam::class, numIntParam("passthrough")), runIsolated = false)
        Assert.assertNotNull(aClass)
        aClass!!.getConstructor(Integer.TYPE, Integer.TYPE).newInstance(4, 1)
    }

    @Test
    fun testScriptWithInterface() {
        val aClass = compileScript("fib_dsl.kts", ReflectedSuperclassTestScriptDefinition(".kts", numIntParam(), TestDSLInterface::class), runIsolated = false)
        Assert.assertNotNull(aClass)
        aClass!!.getConstructor(Integer.TYPE).newInstance(4)
    }

    @Test
    fun testScriptWithClasspath() {
        val aClass1 = compileScript("fib_ext.kts", SimpleParamsWithClasspathTestScriptDefinition(".kts", numIntParam(), classpath = emptyList()), runIsolated = true, suppressOutput = true)
        Assert.assertNull(aClass1)

        val cp = classpathFromClassloader(ScriptTest::class.java.classLoader).filter { it.contains("kotlin-runtime") || it.contains("junit") }
        Assert.assertFalse(cp.isEmpty())

        val aClass2 = compileScript("fib_ext.kts", SimpleParamsWithClasspathTestScriptDefinition(".kts", numIntParam(), classpath = cp), runIsolated = true)
        Assert.assertNotNull(aClass2)
    }

    private fun compileScript(
            scriptPath: String,
            scriptDefinition: KotlinScriptDefinition,
            runIsolated: Boolean = true,
            suppressOutput: Boolean = false): Class<*>?
    {
        val paths = PathUtil.getKotlinPathsForDistDirectory()
        val messageCollector =
                if (suppressOutput) MessageCollector.NONE
                else PrintingMessageCollector.PLAIN_TEXT_TO_SYSTEM_ERR

        val rootDisposable = Disposer.newDisposable()
        try {
            val configuration = KotlinTestUtils.newConfiguration(ConfigurationKind.JDK_ONLY, TestJdkKind.FULL_JDK)
            configuration.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
            configuration.addKotlinSourceRoot("compiler/testData/script/" + scriptPath)
            configuration.add(JVMConfigurationKeys.SCRIPT_DEFINITIONS, scriptDefinition)
            scriptDefinition.getScriptDependenciesClasspath().forEach { configuration.addJvmClasspathRoot(File(it)) }

            val environment = KotlinCoreEnvironment.createForProduction(rootDisposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES)

            try {
                return if (runIsolated) KotlinToJVMBytecodeCompiler.compileScript(environment, paths)
                else KotlinToJVMBytecodeCompiler.compileScript(environment, this.javaClass.classLoader)
            }
            catch (e: CompilationException) {
                messageCollector.report(CompilerMessageSeverity.EXCEPTION, OutputMessageUtil.renderException(e),
                                        MessageUtil.psiElementToMessageLocation(e.element))
                return null
            }
            catch (t: Throwable) {
                MessageCollectorUtil.reportException(messageCollector, t)
                throw t
            }

        }
        finally {
            Disposer.dispose(rootDisposable)
        }
    }

    private fun numIntParam(name: String = "num"): List<ScriptParameter> {
        return listOf(ScriptParameter(Name.identifier(name), DefaultBuiltIns.Instance.intType))
    }
}

class TestParamClass(val memberNum: Int)