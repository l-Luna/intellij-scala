package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import statements.{EmptyDcl, Dcl, Def}

/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

/*
 * RefineStat ::= Dcl
 *              | 'type' TypeDef
 */

object RefineStat {
  def parse(builder: PsiBuilder): Boolean = {
    builder.getTokenType match {
      case ScalaTokenTypes.kTYPE => {
        if (!Def.parse(builder,false)){
          if (!Dcl.parse(builder,false)) {
            EmptyDcl.parse(builder, false)
          }
        }
        return true
      }
      case ScalaTokenTypes.kVAR | ScalaTokenTypes.kVAL
         | ScalaTokenTypes.kDEF => {
        if (Dcl.parse(builder,false)) {
          return true
        }
        else {
          EmptyDcl.parse(builder, false)
          return true
        }
      }
      case _ => {
        return false
      }
    }
  }
}