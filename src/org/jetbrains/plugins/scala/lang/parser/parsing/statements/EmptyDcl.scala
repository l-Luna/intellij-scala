package org.jetbrains.plugins.scala.lang.parser.parsing.statements

import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Annotation
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Modifier
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.ScalaBundle

/**
 * @author Alexander Podkhalyuzin
 */

object EmptyDcl {
  def parse(builder: PsiBuilder): Boolean = parse(builder,true)
  def parse(builder: PsiBuilder, isMod: Boolean): Boolean = {
    val dclMarker = builder.mark
    if (isMod) {
      while (Annotation.parse(builder)) {}
      while (Modifier.parse(builder)) {}
    }
    builder.getTokenType match {
      case ScalaTokenTypes.kDEF | ScalaTokenTypes.kVAL | ScalaTokenTypes.kVAR |
              ScalaTokenTypes.kTYPE => {
        builder.advanceLexer
        builder.error(ScalaBundle.message("identifier.expected"))
        dclMarker.drop
        return true
      }
      case _ => {
        dclMarker.rollbackTo
        return false
      }
    }
  }
}