/*
 * Copyright (c) Lightbend Inc. 2021
 *
 */

package com.lightbend.akkasls.codegen

import java.nio.file.Path
import javax.tools.ToolProvider
import java.nio.file.Files
import java.util.stream.Collectors

import scala.jdk.CollectionConverters._

/**
  * Builds a model of entities and their commands, events and state types
  * from compiled Java protobuf files.
  */
@SuppressWarnings(Array("org.wartremover.warts.Null"))
object ModelBuilder {

  private final val JAVA_SOURCE = ".java"
  private final val JAVA_CLASS  = ".class"

  /**
    * Given a source directory containing protobuf Java source files,
    * return a collection of their paths at any depth.
    *
    * @param protoSourceDirectory the directory to read .java files from
    * @return the collection of java protobuf source files
    */
  def collectProtobufSources(protoSourceDirectory: Path): Iterable[Path] =
    Files
      .walk(protoSourceDirectory)
      .filter(p => Files.isRegularFile(p) && p.toString().endsWith(JAVA_SOURCE))
      .collect(Collectors.toList())
      .asScala

  /**
    * Compile protobuf Java source files using the Java compiler
    *
    * @param protoSources the sources to compile
    * @param outputDirectory the directory to write .class files to
    * @return 0 for success, non-zero for failure (as per the Java compiler)
    */
  def compileProtobufSources(protoSources: Iterable[Path], outputDirectory: Path): Int = {
    val args = Array(
      "-d",
      outputDirectory.toString(),
      "-cp",
      s"${jarPath(classOf[com.google.protobuf.Descriptors.Descriptor])}:" +
      s"${jarPath(classOf[io.cloudstate.EntityKey])}"
    ) ++ protoSources.map(_.toString())

    val _ = outputDirectory.toFile().mkdir()

    val compiler = ToolProvider.getSystemJavaCompiler()
    compiler.run(null, null, null, args: _*)
  }

  /**
    * Given a collection of source files and a root from which they can be relativized,
    * return their corresponding class file paths in relation to an output file directory.
    * @param protoSourceDirectory the root directory of all protobuf java sources
    * @param protoSources the full paths of the protobuf java sources
    * @param outputDirectory the directory where the class files should exist
    * @return a collection of paths correlating class files with their source files
    */
  def mapProtobufClasses(
      protoSourceDirectory: Path,
      protoSources: Iterable[Path],
      outputDirectory: Path
  ): Iterable[Path] =
    protoSources
      .map(protoSourceDirectory.relativize)
      .map { entry =>
        val relativeClassEntry = entry
          .resolveSibling(entry.getFileName().toString().replace(JAVA_SOURCE, JAVA_CLASS))
        outputDirectory.resolve(relativeClassEntry)
      }
  /*
   * Given a class, return a String path to its containing Jar.
   */
  private def jarPath[A](aClass: Class[A]): String =
    aClass.getProtectionDomain().getCodeSource().getLocation().getPath().toString()
}
