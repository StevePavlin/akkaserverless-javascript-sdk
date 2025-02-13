/*
 * Copyright (c) Lightbend Inc. 2021
 *
 */

package com.lightbend.akkasls.codegen
package js

import com.google.common.base.Charsets
import org.bitbucket.inkytonik.kiama.output.PrettyPrinter
import org.bitbucket.inkytonik.kiama.output.PrettyPrinterTypes.Document

import java.nio.file.{ Files, Path }
import java.util.stream.Collectors
import scala.jdk.CollectionConverters._

/**
 * Responsible for generating JavaScript source from an entity model
 */
object SourceGenerator extends PrettyPrinter {

  override val defaultIndent = 2

  private val ProtoExt = ".proto"

  private val ProtoNs = "proto"

  /**
   * Generate JavaScript source from entities where the target source and test source directories have no existing
   * source. Note that we only generate tests for entities where we are successful in generating an entity. The user may
   * not want a test otherwise.
   *
   * Also generates a main source file if it does not already exist.
   *
   * Impure.
   *
   * @param protobufDescriptor
   *   The path to the protobuf descriptor file
   * @param entities
   *   The model of entity metadata to generate source file
   * @param protobufSourceDirectory
   *   A directory to read protobuf source files in.
   * @param sourceDirectory
   *   A directory to generate source files in, which can also containing existing source.
   * @param testSourceDirectory
   *   A directory to generate test source files in, which can also containing existing source.
   * @param indexFilename
   *   The name of the index file e.g. index.js
   * @return
   *   A collection of paths addressing source files generated by this function
   */
  def generate(
      protobufDescriptor: Path,
      model: ModelBuilder.Model,
      protobufSourceDirectory: Path,
      sourceDirectory: Path,
      testSourceDirectory: Path,
      generatedSourceDirectory: Path,
      integrationTestSourceDirectory: Option[Path],
      indexFilename: String): Iterable[Path] = {
    val generatedIndexSourceFilename = "index.js"
    val allProtoSources = Files
      .walk(protobufSourceDirectory)
      .filter(p => Files.isRegularFile(p) && p.toString.endsWith(ProtoExt))
      .collect(Collectors.toList())
      .asScala
      .map(p => protobufSourceDirectory.toAbsolutePath.relativize(p.toAbsolutePath))
    model.services.values.flatMap {
      case service: ModelBuilder.EntityService =>
        model.entities
          .get(service.componentFullName)
          .toSeq
          .flatMap(entity =>
            EntityServiceSourceGenerator.generate(
              entity,
              service,
              protobufSourceDirectory,
              sourceDirectory,
              testSourceDirectory,
              generatedSourceDirectory,
              integrationTestSourceDirectory,
              indexFilename,
              allProtoSources))
      case service: ModelBuilder.ViewService if service.transformedUpdates.nonEmpty =>
        ViewServiceSourceGenerator.generate(
          service,
          protobufSourceDirectory,
          sourceDirectory,
          testSourceDirectory,
          generatedSourceDirectory,
          integrationTestSourceDirectory,
          indexFilename,
          allProtoSources)
      case service: ModelBuilder.ActionService =>
        ActionServiceSourceGenerator.generate(
          service,
          protobufSourceDirectory,
          sourceDirectory,
          testSourceDirectory,
          generatedSourceDirectory,
          integrationTestSourceDirectory,
          indexFilename,
          allProtoSources)
      case _ => Seq.empty
    } ++ {
      if (model.services.nonEmpty) {

        val generatedComponentIndexPath =
          generatedSourceDirectory.resolve(generatedIndexSourceFilename)
        val _ = generatedComponentIndexPath.getParent.toFile.mkdirs()
        val _ = Files.write(
          generatedComponentIndexPath,
          generatedComponentIndex(model, generatedSourceDirectory, sourceDirectory).layout.getBytes(Charsets.UTF_8))

        // Generate a main source file is it is not there already
        val indexPath =
          sourceDirectory.resolve(indexFilename)
        if (!indexPath.toFile.exists()) {
          val _ = indexPath.getParent.toFile.mkdirs()
          val _ = Files.write(
            indexPath,
            indexSource(sourceDirectory, generatedComponentIndexPath).layout.getBytes(Charsets.UTF_8))
          List(indexPath, generatedComponentIndexPath)
        } else {
          List(generatedComponentIndexPath)
        }
      } else {
        List.empty
      }
    }
  }

  private[codegen] def indexSource(sourceDirectory: Path, generatedComponentIndexPath: Path): Document = {
    val generatedComponentIndexFilename =
      sourceDirectory.toAbsolutePath
        .relativize(generatedComponentIndexPath.toAbsolutePath)
        .toString

    val generatedComponentArray = "generatedComponents"

    pretty(
      initialisedCodeComment <> line <> line <>
      "import" <+> braces(" AkkaServerless ") <+> "from" <+> dquotes(
        "@lightbend/akkaserverless-javascript-sdk") <> semi <> line <>
      "import" <+> generatedComponentArray <+> "from" <+> dquotes(generatedComponentIndexFilename) <> semi
      <> line <> line <>
      "const" <+> "server" <+> equal <+> "new" <+> "AkkaServerless" <> parens(emptyDoc) <> semi <> line <> line <>
      "// This generatedComponentArray array contains all generated Actions, Views or Entities," <> line <>
      "// and is kept up-to-date with any changes in your protobuf definitions." <> line <>
      "// If you prefer, you may remove this line and manually register these components." <> line <>
      generatedComponentArray <> dot <> "forEach" <> parens(
        arrowFn(Seq("component"), "server" <> dot <> "addComponent" <> parens("component") <> semi)) <> semi <> line <>
      line <>
      "server" <> dot <> "start" <> parens(emptyDoc) <> semi)
  }

  private[codegen] def generatedComponentIndex(
      model: ModelBuilder.Model,
      generatedSourceDirectory: Path,
      sourceDirectory: Path): Document = {
    val components = model.services.values.flatMap {
      case ModelBuilder.EntityService(_, _, componentFullName) =>
        model.entities.get(componentFullName).map { entity: ModelBuilder.Entity =>
          val entityName = entity.fqn.name.toLowerCase
          (
            entityName,
            generatedSourceDirectory.toAbsolutePath
              .relativize(sourceDirectory.toAbsolutePath)
              .resolve(s"$entityName.js")
              .toString)
        }
      case ModelBuilder.ViewService(fqn, _, _, transformedUpdates, _) if transformedUpdates.nonEmpty =>
        val serviceName = fqn.name.toLowerCase
        Some(
          (
            serviceName,
            generatedSourceDirectory.toAbsolutePath
              .relativize(sourceDirectory.toAbsolutePath)
              .resolve(s"$serviceName.js")
              .toString))
      case ModelBuilder.ActionService(fqn, _, _) =>
        val serviceName = fqn.name.toLowerCase
        Some(
          (
            serviceName,
            generatedSourceDirectory.toAbsolutePath
              .relativize(sourceDirectory.toAbsolutePath)
              .resolve(s"$serviceName.js")
              .toString))
      case _ => None
    }
    pretty(
      managedCodeComment <> line <> line <>
      ssep(
        components.map { case (name, path) =>
          "import" <+> name <+> "from" <+> dquotes(path) <> semi
        }.toSeq,
        line) <> line <>
      line <>
      "export" <+> braces(
        space <>
        ssep(
          components.map { case (name, _) =>
            text(name)
          }.toSeq,
          comma <> " ") <> space) <> semi
      <> line <>
      line <>
      "export" <+> "default" <+> brackets(
        ssep(
          components.map { case (name, _) =>
            text(name)
          }.toSeq,
          comma <> " ")) <> semi)
  }

  private[js] def lowerFirst(text: String): String =
    text.headOption match {
      case Some(c) => c.toLower.toString + text.drop(1)
      case None    => ""
    }

  private[js] def arrowFn(args: Seq[String], body: Doc) =
    parens(ssep(args.map(text), comma)) <+> "=>" <+> braces(nest(line <> body) <> line)

  private[js] def typeReference(fqn: FullyQualifiedName): Doc = fqn match {
    case FullyQualifiedName("Empty", parent) if parent.pkg == "google.protobuf" =>
      "void"
    case FullyQualifiedName(name, parent) =>
      ProtoNs <> dot <> parent.pkg <> dot <> "I" <> name
  }

  private[js] def typeUnion(fqns: Seq[FullyQualifiedName]): Doc = fqns match {
    case Nil        => " " <> "unknown"
    case fqn :: Nil => " " <> typeReference(fqn)
    case _ =>
      nest(line <> ssep(fqns.map(fqn => "|" <+> typeReference(fqn)), line))
  }

  private[js] def typedef(source: Doc, name: Doc): Doc =
    "@typedef" <+> braces(" " <> source <> " ") <+> name

  private[js] def blockComment(lines: Doc*) = "/**" <> line <> ssep(lines.map(" *" <+> _), line) <> line <> " */"

  private[js] val initialisedCodeComment: Doc =
    "/*" <+> "This code was initialised by Akka Serverless tooling." <> line <>
    " *" <+> "As long as this file exists it will not be re-generated." <> line <>
    " *" <+> "You are free to make changes to this file." <> line <>
    " */"

  private[js] val managedCodeComment: Doc =
    "/*" <+> "This code is managed by Akka Serverless tooling." <> line <>
    " *" <+> "It will be re-generated to reflect any changes to your protobuf definitions." <> line <>
    " *" <+> "DO NOT EDIT" <> line <>
    " */"
}
