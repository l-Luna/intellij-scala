package org.jetbrains.plugins.scala.codeInsight.intention.types

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup._
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.{Expression, ExpressionContext, Result, TextResult}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import org.jetbrains.plugins.scala.lang.psi.TypeAdjuster
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.ScTypeText
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil

/**
  * Author: Svyatoslav Ilinskiy
  * Date: 22.12.15.
  */

abstract class ChooseValueExpression[T](lookupItems: Seq[T], defaultItem: T) extends Expression {
  def lookupString(elem: T): String
  def result(element: T): String

  val lookupElements: Array[LookupElement] = calcLookupElements().toArray

  def calcLookupElements(): Seq[LookupElementBuilder] = lookupItems.map { elem =>
    LookupElementBuilder.create(elem, lookupString(elem)).withInsertHandler((context: InsertionContext, item: LookupElement) => {
      val topLevelEditor = InjectedLanguageEditorUtil.getTopLevelEditor(context.getEditor)
      val templateState = TemplateManagerImpl.getTemplateState(topLevelEditor)
      if (templateState != null) {
        val range = templateState.getCurrentVariableRange
        if (range != null) {
          //need to insert with FQNs
          val newText = result(item.getObject.asInstanceOf[T])
          val document = topLevelEditor.getDocument
          val startOffset = range.getStartOffset
          document.replaceString(startOffset, range.getEndOffset, newText)

          val file = context.getFile
          PsiDocumentManager.getInstance(file.getProject).commitDocument(document)
          val newRange = TextRange.create(startOffset, startOffset + newText.length)
          val elem = ScalaRefactoringUtil.commonParent(file, newRange)
          TypeAdjuster.markToAdjust(elem)
        }
      }
    })
  }

  override def calculateResult(context: ExpressionContext): Result = new TextResult(lookupString(defaultItem))

  override def calculateLookupItems(context: ExpressionContext): Array[LookupElement] =
    if (lookupElements.length > 1) lookupElements
    else null

  override def calculateQuickResult(context: ExpressionContext): Result = calculateResult(context)
}

class ChooseTypeTextExpression(lookupItems: Seq[ScTypeText], default: ScTypeText) extends
  ChooseValueExpression[ScTypeText](lookupItems, default) {
  def this(lookupItems: Seq[ScTypeText]) = {
    this(lookupItems, lookupItems.head)
  }

  override def lookupString(elem: ScTypeText): String = {
    val useCanonicalText: Boolean = lookupItems.count(_.presentableText == elem.presentableText) > 1
    if (useCanonicalText) elem.canonicalText.replace("_root_.", "")
    else elem.presentableText
  }


  override def calcLookupElements(): Seq[LookupElementBuilder] = {
    super.calcLookupElements().map { le =>
      val text = le.getObject.asInstanceOf[ScTypeText]
      //if we use canonical text we still want to be able to search search by presentable text
      le.withLookupString(text.presentableText)
    }
  }

  override def result(element: ScTypeText): String = element.canonicalText
}
