package org.jetbrains.plugins.scala.lang.autoImport

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportElementFix
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter.normalize
import org.junit.Assert.{assertEquals, fail}

import scala.reflect.ClassTag

abstract class ImportElementFixTestBase[Psi <: PsiElement : ClassTag]
  extends ScalaLightCodeInsightFixtureTestAdapter with ScalaFiles {

  def createFix(element: Psi): Option[ScalaImportElementFix]

  def checkElementsToImport(fileText: String, expectedQNames: String*): Unit = {
    val fix = configureAndCreateFix(fileText)
    assertEquals("Wrong elements to import found: ", expectedQNames, fix.elements.map(_.qualifiedName))
  }

  def checkNoImportFix(fileText: String): Unit = {
    val fix =
      try configureAndCreateFix(fileText)
      catch {
        case NoFixException(_) => return
      }
    fail(s"Some elements to import found ${fix.elements.map(_.qualifiedName)}")
  }

  def doTest(fileText: String, expectedText: String, selected: String): Unit = {
    val fix = configureAndCreateFix(fileText)
    val action = fix.createAddImportAction(getEditor)

    fix.elements.find(_.qualifiedName == selected) match {
      case None       => fail(s"No elements found with qualified name $selected")
      case Some(elem) => action.addImportTestOnly(elem)
    }
    assertEquals("Result doesn't match expected text", normalize(expectedText), normalize(getFile.getText))
  }

  private def configureAndCreateFix(fileText: String): ScalaImportElementFix = {
    val file = configureFromFileText(fileText, fileType)
    val clazz = implicitly[ClassTag[Psi]].runtimeClass.asInstanceOf[Class[Psi]]
    val element = PsiTreeUtil.findElementOfClassAtOffset(file, getEditorOffset, clazz, false)
    createFix(element).getOrElse(throw NoFixException(element))
  }

  private case class NoFixException(element: PsiElement)
    extends AssertionError(s"Import fix not found for ${element.getText}")
}

