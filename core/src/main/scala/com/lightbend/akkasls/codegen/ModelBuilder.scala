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
import scala.util.Using
import java.net.URLClassLoader
import scala.collection.mutable.ListBuffer
import scala.util.Try
import com.google.protobuf.Descriptors.Descriptor

/**
  * Builds a model of entities and their properties from compiled Java protobuf files.
  */
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

  /**
    * Given both protobuf sources and their classes, return a new collection of sources
    * that either have no corresponding class or they have been modified more recently.
    * Both the source and class collection items must correlate with each other and the
    * collections must therefore be of the same size.
    *
    * @param protoSources the collection of protobuf sources
    * @param protoClasses the corresponding target protobuf classes, which may or may not exist
    * @return a filtered down collection of sources more recent than any existing corresponding class
    */
  def filterNewProtobufSources(
      protoSources: Iterable[Path],
      protoClasses: Iterable[Path]
  ): Iterable[Path] = {
    assert(protoSources.size == protoClasses.size)
    val distinctProtoClasses = protoClasses.toArray
    protoSources.zipWithIndex
      .filter { case (source, i) =>
        val sourceFile = source.toFile()
        val classFile  = distinctProtoClasses(i).toFile()
        !classFile.exists() || sourceFile.lastModified() > classFile.lastModified()
      }
      .map(_._1)
  }

  /**
    * Compile protobuf Java source files using the Java compiler
    *
    * @param protoSources the sources to compile
    * @param outputDirectory the directory to write .class files to
    * @return 0 for success, non-zero for failure (as per the Java compiler)
    */
  @SuppressWarnings(Array("org.wartremover.warts.Null"))
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
    * An entity represents the primary model object and is conceptually equivalent to a class, or a type of state.
    * An entity will have multiple Entity instances of it which can handle commands. For example, a user function may
    * implement a chat room entity, encompassing the logic associated with chat rooms, and a particular chat room may
    * be an instance of that entity, containing a list of the users currently in the room and a history of the messages
    * sent to it. Each entity has a particular Entity type, which defines how the entity’s state is persisted, shared,
    * and what its capabilities are.
    */
  sealed abstract class Entity

  /**
    * A type of Entity that stores its state using a journal of events, and restores its state
    * by replaying that journal.
    */
  case object EventSourcedEntity extends Entity

  /**
    * Given a collection of classes representing protobuf declarations, and their root directory, discover
    * the Cloudstate entities and their properities.
    *
    * @param protobufClassesDirectory the root folder of where classes reside
    * @param protobufClasses the classes to inspect
    * @return
    */
  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf", "org.wartremover.warts.Null"))
  def introspectProtobufClasses(
      protobufClassesDirectory: Path,
      protobufClasses: Iterable[Path]
  ): Iterable[Entity] =
    Using(
      URLClassLoader.newInstance(
        protobufClasses.map(p => p.toUri().toURL()).toArray,
        getClass().getClassLoader()
      )
    ) { protobufClassLoader =>
      val entities = new ListBuffer[Entity]
      protobufClasses.foreach { p =>
        val relativePath = protobufClassesDirectory.relativize(p)
        val packageName  = relativePath.getParent().toString().replace("/", ".")
        val className    = relativePath.toString().drop(packageName.size + 1).takeWhile(_ != '.')
        val fqn          = packageName + "." + className
        Try(protobufClassLoader.loadClass(fqn).getMethod("getDescriptor"))
          .foreach { method =>
            val descriptor = method.invoke(null).asInstanceOf[Descriptor]
            // FIXME We are not getting this far as the class cannot be found
            println(descriptor.toProto().toString())
          }
      }
      entities.toList
    }.getOrElse(List.empty)

  /*
   * Given a class, return a String path to its containing Jar.
   */
  private def jarPath[A](aClass: Class[A]): String =
    aClass.getProtectionDomain().getCodeSource().getLocation().getPath().toString()
}
