/*
 * Copyright (c) Lightbend Inc. 2021
 *
 */

package com.lightbend.akkasls.codegen

import com.google.protobuf.DescriptorProtos.FileDescriptorSet
import com.google.protobuf.Descriptors

import java.io.FileInputStream
import java.nio.file.Paths
import scala.jdk.CollectionConverters._
import scala.util.Using
import com.google.protobuf.ExtensionRegistry

class ModelBuilderSuite extends munit.FunSuite {
  test("EventSourcedEntity introspection") {
    val testFilesPath = Paths.get(getClass.getClassLoader.getResource("test-files").toURI)
    val descriptorFilePath =
      testFilesPath.resolve("descriptor-sets/event-sourced-shoppingcart.desc")

    val registry = ExtensionRegistry.newInstance()
    registry.add(com.akkaserverless.Annotations.service)
    registry.add(com.akkaserverless.Annotations.file)

    Using(new FileInputStream(descriptorFilePath.toFile)) { fis =>
      val fileDescSet = FileDescriptorSet.parseFrom(fis, registry)
      val fileList    = fileDescSet.getFileList.asScala

      val descriptors = fileList.map(Descriptors.FileDescriptor.buildFrom(_, Array.empty, true))

      val model = ModelBuilder.introspectProtobufClasses(
        descriptors
      )

      val shoppingCartProto =
        PackageNaming(
          "Shoppingcart",
          "com.example.shoppingcart",
          Some(
            "github.com/lightbend/akkaserverless-go-sdk/example/shoppingcart;shoppingcart"
          ),
          None,
          Some("ShoppingCart"),
          false
        )

      val domainProto =
        PackageNaming(
          "Domain",
          "com.example.shoppingcart.persistence",
          Some(
            "github.com/lightbend/akkaserverless-go-sdk/example/shoppingcart/persistence;persistence"
          ),
          None,
          None,
          false
        )

      val googleEmptyProto =
        PackageNaming(
          "Empty",
          "google.protobuf",
          Some("google.golang.org/protobuf/types/known/emptypb"),
          Some("com.google.protobuf"),
          Some("EmptyProto"),
          true
        )

      val entity =
        ModelBuilder.EventSourcedEntity(
          FullyQualifiedName("ShoppingCart", domainProto),
          "ShoppingCart",
          Some(ModelBuilder.State(FullyQualifiedName("Cart", domainProto))),
          List(
            ModelBuilder.Event(FullyQualifiedName("ItemAdded", domainProto)),
            ModelBuilder.Event(FullyQualifiedName("ItemRemoved", domainProto))
          )
        )

      assertEquals(
        model.entities,
        Map(entity.fqn.fullName -> entity)
      )

      assertEquals(
        model.services,
        Map(
          "com.example.shoppingcart.ShoppingCartService" ->
          ModelBuilder.Service(
            FullyQualifiedName("ShoppingCartService", shoppingCartProto),
            entity.fqn.fullName,
            List(
              ModelBuilder.Command(
                FullyQualifiedName("AddItem", shoppingCartProto),
                FullyQualifiedName("AddLineItem", shoppingCartProto),
                FullyQualifiedName("Empty", googleEmptyProto)
              ),
              ModelBuilder.Command(
                FullyQualifiedName("RemoveItem", shoppingCartProto),
                FullyQualifiedName("RemoveLineItem", shoppingCartProto),
                FullyQualifiedName("Empty", googleEmptyProto)
              ),
              ModelBuilder.Command(
                FullyQualifiedName("GetCart", shoppingCartProto),
                FullyQualifiedName("GetShoppingCart", shoppingCartProto),
                FullyQualifiedName("Cart", shoppingCartProto)
              )
            )
          )
        )
      )
    }.get
  }

  test("ValueEntity introspection") {
    val testFilesPath = Paths.get(getClass.getClassLoader.getResource("test-files").toURI)
    val descriptorFilePath =
      testFilesPath.resolve("descriptor-sets/value-shoppingcart.desc")

    val registry = ExtensionRegistry.newInstance()
    registry.add(com.akkaserverless.Annotations.service)
    registry.add(com.akkaserverless.Annotations.file)

    Using(new FileInputStream(descriptorFilePath.toFile)) { fis =>
      val fileDescSet = FileDescriptorSet.parseFrom(fis, registry)
      val fileList    = fileDescSet.getFileList.asScala

      val descriptors = fileList.map(Descriptors.FileDescriptor.buildFrom(_, Array.empty, true))

      val model = ModelBuilder.introspectProtobufClasses(
        descriptors
      )

      val shoppingCartProto =
        PackageNaming(
          "Shoppingcart",
          "com.example.valueentity.shoppingcart",
          Some(
            "github.com/lightbend/akkaserverless-go-sdk/example/valueentity/shoppingcart;shoppingcart"
          ),
          None,
          Some("ShoppingCart"),
          false
        )

      val domainProto =
        PackageNaming(
          "Domain",
          "com.example.valueentity.shoppingcart.persistence",
          Some(
            "github.com/lightbend/akkaserverless-go-sdk/example/valueentity/shoppingcart/persistence;persistence"
          ),
          None,
          None,
          false
        )

      val googleEmptyProto =
        PackageNaming(
          "Empty",
          "google.protobuf",
          Some("google.golang.org/protobuf/types/known/emptypb"),
          Some("com.google.protobuf"),
          Some("EmptyProto"),
          true
        )
      val entity = ModelBuilder.ValueEntity(
        FullyQualifiedName("ShoppingCart", domainProto),
        "ShoppingCart",
        ModelBuilder.State(FullyQualifiedName("Cart", domainProto))
      )

      assertEquals(
        model.entities,
        Map(entity.fqn.fullName -> entity)
      )

      assertEquals(
        model.services,
        Map(
          "com.example.valueentity.shoppingcart.ShoppingCartService" ->
          ModelBuilder.Service(
            FullyQualifiedName("ShoppingCartService", shoppingCartProto),
            entity.fqn.fullName,
            List(
              ModelBuilder.Command(
                FullyQualifiedName("AddItem", shoppingCartProto),
                FullyQualifiedName("AddLineItem", shoppingCartProto),
                FullyQualifiedName("Empty", googleEmptyProto)
              ),
              ModelBuilder.Command(
                FullyQualifiedName("RemoveItem", shoppingCartProto),
                FullyQualifiedName("RemoveLineItem", shoppingCartProto),
                FullyQualifiedName("Empty", googleEmptyProto)
              ),
              ModelBuilder.Command(
                FullyQualifiedName("GetCart", shoppingCartProto),
                FullyQualifiedName("GetShoppingCart", shoppingCartProto),
                FullyQualifiedName("Cart", shoppingCartProto)
              ),
              ModelBuilder.Command(
                FullyQualifiedName("RemoveCart", shoppingCartProto),
                FullyQualifiedName("RemoveShoppingCart", shoppingCartProto),
                FullyQualifiedName("Empty", googleEmptyProto)
              )
            )
          )
        )
      )
    }.get
  }

  test("deriving java package from proto options") {
    val name = "Name"
    val pkg  = "com.example"

    assertEquals(
      PackageNaming(name, pkg, None, None, None, false).javaPackage,
      pkg
    )
    assertEquals(
      PackageNaming(name, pkg, None, Some("override.package"), None, false).javaPackage,
      "override.package"
    )
  }

  test("resolving full names") {
    val pkg = "com.example"

    assertEquals(ModelBuilder.resolveFullName("Test", pkg), "com.example.Test")
    assertEquals(ModelBuilder.resolveFullName(".sub.Test", pkg), "com.example.sub.Test")
    assertEquals(ModelBuilder.resolveFullName("other.package.Test", pkg), "other.package.Test")
  }
}
