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

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.openapi.util.Key
import com.intellij.psi.*
import org.jetbrains.kotlin.asJava.KtLightElement
import org.jetbrains.kotlin.asJava.KtLightModifierList
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.junit.Assert

object PsiElementChecker {
    val TEST_DATA_KEY = Key.create<Int>("Test Key")

    fun checkPsiElementStructure(lightClass: PsiClass) {
        checkPsiElement(lightClass)

        lightClass.innerClasses.forEach { checkPsiElementStructure(it) }

        lightClass.methods.forEach {
            it.parameterList.parameters.forEach { checkPsiElement(it) }
            checkPsiElement(it)
        }

        lightClass.fields.forEach { checkPsiElement(it) }
    }

    private fun checkPsiElement(element: PsiElement) {
        if (element !is KtLightElement<*, *> && element !is KtLightModifierList) return

        if (element is PsiModifierListOwner) {
            val modifierList = element.modifierList
            if (modifierList != null) {
                checkPsiElement(modifierList)
            }
        }

        if (element is PsiTypeParameterListOwner) {
            val typeParameterList = element.typeParameterList
            if (typeParameterList != null) {
                checkPsiElement(typeParameterList)
                typeParameterList.typeParameters.forEach { checkPsiElement(it) }
            }
        }

        with(element) {
            try {
                Assert.assertEquals("Number of methods has changed. Please update test.", 54, PsiElement::class.java.methods.size)

                project
                Assert.assertTrue(language == KotlinLanguage.INSTANCE)
                manager
                children
                parent
                firstChild
                lastChild
                nextSibling
                prevSibling
                containingFile
                textRange
                startOffsetInParent
                textLength
                findElementAt(0)
                findReferenceAt(0)
                textOffset
                text
                textToCharArray()
                navigationElement
                originalElement
                textMatches("")
                Assert.assertTrue(textMatches(this))
                textContains('a')
                accept(PsiElementVisitor.EMPTY_VISITOR)
                acceptChildren(PsiElementVisitor.EMPTY_VISITOR)

                val copy = copy()
                Assert.assertTrue(copy == null || copy.javaClass == this.javaClass)

                // Modify methods:
                // add(this)
                // addBefore(this, lastChild)
                // addAfter(firstChild, this)
                // checkAdd(this)
                // addRange(firstChild, lastChild)
                // addRangeBefore(firstChild, lastChild, lastChild)
                // addRangeAfter(firstChild, lastChild, firstChild)
                // delete()
                // checkDelete()
                // deleteChildRange(firstChild, lastChild)
                // replace(this)

                Assert.assertTrue(isValid)
                isWritable
                reference
                references
                putCopyableUserData(TEST_DATA_KEY, 12)

                Assert.assertTrue(getCopyableUserData(TEST_DATA_KEY) == 12)
                // Assert.assertTrue(copy().getCopyableUserData(TEST_DATA_KEY) == 12) { this } Doesn't work

                // processDeclarations(...)

                context
                isPhysical
                resolveScope
                useScope
                node
                toString()
                Assert.assertTrue(isEquivalentTo(this))
            }
            catch (t: Throwable) {
                throw AssertionErrorWithCause("Failed for ${this.javaClass} ${this}", t)
            }
        }
    }
}