package com.omervk.wartremover.spark.warts

import java.io.Serializable

import org.wartremover.{ WartTraverser, WartUniverse }

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object UnserializableCapture extends WartTraverser {

  def apply(u: WartUniverse): u.Traverser = {
    import u.universe._

    class ClosureTraverser(alreadyInScope: Seq[String]) extends u.Traverser {
      private val scopedIdentifiers: ListBuffer[String] = mutable.ListBuffer(alreadyInScope: _*)

      override def traverse(tree: Tree): Unit = {
        tree match {
          case ident: Ident if // The identifier doesn't refer to a type
          !ident.isType &&
            // The identifier doesn't refer to a package
            !ident.symbol.isPackage &&
            // The identifier hasn't been defined in this scope
            !scopedIdentifiers.contains(ident.name.decodedName.toString) &&
            // It is not a primitive (these are automatically, implicitly serializable)
            !isPrimitive(u)(ident.symbol.typeSignature) &&
            // It does not extend Serializable
            !(ident.symbol.typeSignature <:< typeOf[Serializable]) =>

            error(u)(ident.pos, s"Functions sent to Spark may not close over unserializable values ($ident)")
          //            error(u)(ident.pos, ident.symbol.typeSignature.typeSymbol.fullName)

          case block: Block =>
            val blockInClosureTraverser = new ClosureTraverser(scopedIdentifiers)
            (block.stats :+ block.expr).foreach(blockInClosureTraverser.traverse)

          case d: ValOrDefDef =>
            val name = d.name.decodedName.toString
            scopedIdentifiers += name
            //            error(u)(d.pos, s"added $name to scopedIdentifiers list")
            super.traverse(d)

          case tic =>
            //            val symbolFullName = Option(tic.symbol).map(_.fullName).getOrElse("** No Symbol **")
            //            error(u)(tic.pos, s"Traversing: ${tic.getClass.toString} $symbolFullName ${tic.toString}")
            super.traverse(tic)
        }
      }
    }

    // TODO: What else could ship closures?
    // TODO: Check whether an object marked as serializable really is (everything inside it is also serializable)
    new u.Traverser {
      private def isDatasetOrRDD(qualifier: u.universe.Tree): Boolean = {
        val typesToCheck: List[u.universe.Type] = qualifier match {
          case ident: Ident =>
            ident.symbol.typeSignature :: Nil

          case apply: Apply =>
            apply.symbol.typeSignature.finalResultType :: Nil

          case _ =>
            Nil
        }

        typesToCheck.exists {
          _.baseClasses
            .map(_.fullName)
            .exists(name => name == "org.apache.spark.sql.Dataset" || name == "org.apache.spark.rdd.RDD")
        }
      }

      private def traverseAllArgs(args: List[u.universe.Tree]): List[Unit] = {
        args.collect {
          case Function(params, body) =>
            new ClosureTraverser(params.map(_.name.decodedName.toString))
              .traverse(body)
        }
      }

      override def traverse(tree: Tree): Unit = {
        tree match {
          // Ignore trees marked by SuppressWarnings
          case t if hasWartAnnotation(u)(t) =>

          case t @ Apply(TypeApply(Select(qualifier, _), _), args) if isDatasetOrRDD(qualifier) =>
            // Used when calling a method with a type argument
            traverseAllArgs(args)

            // Continue down in case there's more inside
            super.traverse(t)

          case t @ Apply(Select(qualifier, _), args) if isDatasetOrRDD(qualifier) =>
            // Used when calling a method without a type argument
            traverseAllArgs(args)

            // Continue down in case there's more inside
            super.traverse(t)

          case t =>
            super.traverse(t)
        }
      }
    }
  }
}
