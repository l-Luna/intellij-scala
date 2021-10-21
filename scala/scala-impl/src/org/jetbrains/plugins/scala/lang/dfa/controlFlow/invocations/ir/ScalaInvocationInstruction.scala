package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.ir

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter
import com.intellij.codeInspection.dataFlow.java.JavaDfaHelpers
import com.intellij.codeInspection.dataFlow.lang.ir.{DfaInstructionState, ExpressionPushingInstruction}
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.{DfaControlTransferValue, DfaValue, DfaValueFactory}
import org.jetbrains.plugins.scala.lang.dfa.analysis.ScalaDfaAnchor
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.InvocationInfo
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.Argument
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.ir.InterproceduralAnalysis.tryInterpretExternalMethod

import scala.jdk.CollectionConverters._
import scala.language.postfixOps

/**
 * Intermediate Representation instruction for Scala invocations.
 *
 * Assumes all arguments that the invoked function needs have already been evaluated in a correct order
 * and are present on the top of the stack. It consumes all of those arguments and produces one value
 * on the stack that is the return value of this invocation.
 */
class ScalaInvocationInstruction(invocationInfo: InvocationInfo, invocationAnchor: ScalaDfaAnchor,
                                 exceptionTransfer: Option[DfaControlTransferValue])
  extends ExpressionPushingInstruction(invocationAnchor) {

  override def toString: String = {
    val invokedElementString = invocationInfo.invokedElement
      .map(_.toString)
      .getOrElse("<unknown>")
    s"CALL $invokedElementString"
  }

  override def accept(interpreter: DataFlowInterpreter, stateBefore: DfaMemoryState): Array[DfaInstructionState] = {
    implicit val factory: DfaValueFactory = interpreter.getFactory
    val argumentValues = collectArgumentValuesFromStack(stateBefore)

    val finder = MethodEffectFinder(invocationInfo)
    val methodEffect = finder.findMethodEffect(interpreter, stateBefore, argumentValues)

    if (!methodEffect.isPure) {
      argumentValues.values.foreach(JavaDfaHelpers.dropLocality(_, stateBefore))
      stateBefore.flushFields()
    }

    val returnValue = if (!methodEffect.handledSpecially) {
      tryInterpretExternalMethod(invocationInfo, argumentValues) match {
        case Some(returnValue) => returnValue
        case _ => methodEffect.returnValue.getDfType
      }
    } else methodEffect.returnValue.getDfType

    returnFromInvocation(returnValue, stateBefore, interpreter)
  }

  private def returnFromInvocation(returnValue: DfType, stateBefore: DfaMemoryState,
                                   interpreter: DataFlowInterpreter): Array[DfaInstructionState] = {
    val exceptionalState = stateBefore.createCopy()
    val exceptionalResult = exceptionTransfer.map(_.dispatch(exceptionalState, interpreter).asScala)
      .getOrElse(Nil)

    val normalResult = returnValue match {
      case DfType.BOTTOM => None
      case _ => pushResult(interpreter, stateBefore, returnValue)
        Some(nextState(interpreter, stateBefore))
    }

    (exceptionalResult ++ normalResult).toArray
  }

  private def collectArgumentValuesFromStack(stateBefore: DfaMemoryState): Map[Argument, DfaValue] = {
    invocationInfo.argListsInEvaluationOrder.flatten
      .reverseIterator
      .map((_, stateBefore.pop()))
      .toMap
  }
}
