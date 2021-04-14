/*
 * Copyright (c) Lightbend Inc. 2021
 *
 */

package com.lightbend.akkasls.codegen
package js

import java.nio.file.{ Files, Paths }
import org.apache.commons.io.FileUtils

class SourceGeneratorSuite extends munit.FunSuite {
  val protoRef =
    PackageNaming(
      "com.example.service",
      None,
      None,
      None
    )
  test("generate") {
    val protoSourceDirectory = Files.createTempDirectory("proto-source-generator-test")
    try {
      val sourceDirectory = Files.createTempDirectory("source-generator-test")
      try {

        val testSourceDirectory = Files.createTempDirectory("test-source-generator-test")

        try {

          val protoSource1     = protoSourceDirectory.resolve("myservice1.proto")
          val protoSourceFile1 = protoSource1.toFile
          FileUtils.forceMkdir(protoSourceFile1.getParentFile)
          FileUtils.touch(protoSourceFile1)

          val source1     = sourceDirectory.resolve("myservice1.js")
          val sourceFile1 = source1.toFile
          FileUtils.forceMkdir(sourceFile1.getParentFile)
          FileUtils.touch(sourceFile1)

          val testSource2     = testSourceDirectory.resolve("myservice2.test.js")
          val testSourceFile2 = testSource2.toFile
          FileUtils.forceMkdir(testSourceFile2.getParentFile)
          FileUtils.touch(testSourceFile2)

          val entities = List(
            ModelBuilder.EventSourcedEntity(
              FullyQualifiedName("MyService1", protoRef),
              "MyService1",
              Some(ModelBuilder.State(FullyQualifiedName("State1", protoRef))),
              List(
                ModelBuilder.Command(
                  "com.lightbend.MyService.Set",
                  FullyQualifiedName("SetValue", protoRef),
                  FullyQualifiedName("Empty", protoRef)
                ),
                ModelBuilder.Command(
                  "com.lightbend.MyService.Get",
                  FullyQualifiedName("GetValue", protoRef),
                  FullyQualifiedName("MyState", protoRef)
                )
              ),
              List.empty
            ),
            ModelBuilder.EventSourcedEntity(
              FullyQualifiedName("MyService2", protoRef),
              "MyService2",
              Some(ModelBuilder.State(FullyQualifiedName("State2", protoRef))),
              List(
                ModelBuilder.Command(
                  "com.lightbend.MyService.Set",
                  FullyQualifiedName("SetValue", protoRef),
                  FullyQualifiedName("Empty", protoRef)
                ),
                ModelBuilder.Command(
                  "com.lightbend.MyService.Get",
                  FullyQualifiedName("GetValue", protoRef),
                  FullyQualifiedName("MyState", protoRef)
                )
              ),
              List.empty
            ),
            ModelBuilder.EventSourcedEntity(
              FullyQualifiedName("MyService3", protoRef),
              "MyService3",
              Some(ModelBuilder.State(FullyQualifiedName("State3", protoRef))),
              List(
                ModelBuilder.Command(
                  "com.lightbend.MyService.Set",
                  FullyQualifiedName("SetValue", protoRef),
                  FullyQualifiedName("Empty", protoRef)
                ),
                ModelBuilder.Command(
                  "com.lightbend.MyService.Get",
                  FullyQualifiedName("GetValue", protoRef),
                  FullyQualifiedName("MyState", protoRef)
                )
              ),
              List.empty
            )
          )

          val sources = SourceGenerator.generate(
            sourceDirectory.resolve("some.desc"),
            entities,
            protoSourceDirectory,
            sourceDirectory,
            testSourceDirectory,
            "index.js"
          )

          assertEquals(Files.size(source1), 0L)
          assertEquals(Files.size(testSource2), 0L)

          assertEquals(
            sources,
            List(
              sourceDirectory.resolve("myservice2.js"),
              sourceDirectory.resolve("myservice3.js"),
              testSourceDirectory.resolve("myservice3.test.js"),
              sourceDirectory.resolve("index.js")
            )
          )

          // Test that the main, source and test files are being written to
          assertEquals(Files.readAllBytes(sources.head).head.toChar, 'i')
          assertEquals(Files.readAllBytes(sources.drop(1).head).head.toChar, 'i')
          assertEquals(Files.readAllBytes(sources.drop(3).head).head.toChar, 'i')

        } finally FileUtils.deleteDirectory(testSourceDirectory.toFile)
      } finally FileUtils.deleteDirectory(sourceDirectory.toFile)
    } finally FileUtils.deleteDirectory(protoSourceDirectory.toFile)
  }

  test("source") {
    val entity = ModelBuilder.EventSourcedEntity(
      FullyQualifiedName("MyServiceEntity", protoRef),
      "MyServiceEntity",
      Some(ModelBuilder.State(FullyQualifiedName("MyState", protoRef))),
      List(
        ModelBuilder.Command(
          "com.lightbend.MyServiceEntity.Set",
          FullyQualifiedName("SetValue", protoRef),
          FullyQualifiedName("Empty", protoRef)
        ),
        ModelBuilder.Command(
          "com.lightbend.MyServiceEntity.Get",
          FullyQualifiedName("GetValue", protoRef),
          FullyQualifiedName("MyState", protoRef)
        )
      ),
      List(
        ModelBuilder.Event(FullyQualifiedName("SetEvent", protoRef))
      )
    )

    val protoSources            = List(Paths.get("myentity1.proto"), Paths.get("someother.proto"))
    val protobufSourceDirectory = Paths.get("./src/proto")
    val sourceDirectory         = Paths.get("./src/js")

    val sourceDoc =
      SourceGenerator.source(protoSources, protobufSourceDirectory, sourceDirectory, entity)
    assertEquals(
      sourceDoc.layout.replace("\\", "/"), // Cope with windows testing
      """import { EventSourcedEntity } from "@lightbend/akkaserverless-javascript-sdk";
        |
        |const entity = new EventSourcedEntity(
        |  [
        |    "myentity1.proto",
        |    "someother.proto"
        |  ],
        |  "com.example.service.MyServiceEntity",
        |  "myserviceentity",
        |  {
        |    includeDirs: ["./src/proto"],
        |    serializeFallbackToJson: true
        |  }
        |);
        |
        |entity.setInitial(entityId => ({}));
        |
        |const commandHandlers = {
        |  Set(command, state, ctx) {
        |    ctx.fail("The command handler for `Set` is not implemented, yet");
        |  },
        |  
        |  Get(command, state, ctx) {
        |    ctx.fail("The command handler for `Get` is not implemented, yet");
        |  }
        |}
        |
        |const eventHandlers = {
        |  SetEvent(event, state) {
        |    return state;
        |  }
        |}
        |
        |entity.setBehavior(state => {
        |  return {
        |    commandHandlers,
        |    eventHandlers
        |  };
        |});
        |
        |export default entity;""".stripMargin
    )
  }

  test("test source") {
    val entity =
      ModelBuilder.EventSourcedEntity(
        FullyQualifiedName("MyService1", protoRef),
        "MyService1",
        Some(ModelBuilder.State(FullyQualifiedName("MyState", protoRef))),
        List(
          ModelBuilder.Command(
            "com.lightbend.MyService.Set",
            FullyQualifiedName("SetValue", protoRef),
            FullyQualifiedName("protobuf.Empty", protoRef)
          ),
          ModelBuilder.Command(
            "com.lightbend.MyService.Get",
            FullyQualifiedName("GetValue", protoRef),
            FullyQualifiedName("MyState", protoRef)
          )
        ),
        List.empty
      )

    val testSourceDirectory = Paths.get("./test/js");
    val sourceDirectory     = Paths.get("./src/js");
    val sourceDoc           = SourceGenerator.testSource(entity, testSourceDirectory, sourceDirectory)
    assertEquals(
      sourceDoc.layout.replace("\\", "/"), // Cope with windows testing
      """import { MockEventSourcedEntity } from "./testkit.js";
        |import { expect } from "chai";
        |import myservice1 from "../../src/js/myservice1.js";
        |
        |describe("MyService1", () => {
        |  const entityId = "entityId";
        |  
        |  describe("Set", () => {
        |    it("should...", () => {
        |      const entity = new MockEventSourcedEntity(myservice1, entityId);
        |      // TODO: you may want to set fields in addition to the entity id
        |      // const result = entity.handleCommand("Set", { entityId });
        |      
        |      // expect(result).to.deep.equal({});
        |      // expect(entity.error).to.be.undefined;
        |      // expect(entity.state).to.deep.equal({});
        |      // expect(entity.events).to.deep.equal([]);
        |    });
        |  });
        |  
        |  describe("Get", () => {
        |    it("should...", () => {
        |      const entity = new MockEventSourcedEntity(myservice1, entityId);
        |      // TODO: you may want to set fields in addition to the entity id
        |      // const result = entity.handleCommand("Get", { entityId });
        |      
        |      // expect(result).to.deep.equal({});
        |      // expect(entity.error).to.be.undefined;
        |      // expect(entity.state).to.deep.equal({});
        |      // expect(entity.events).to.deep.equal([]);
        |    });
        |  });
        |});""".stripMargin
    )
  }

  test("index source") {
    val entities = List(
      ModelBuilder.EventSourcedEntity(
        FullyQualifiedName("MyService1", protoRef),
        "MyService1",
        Some(ModelBuilder.State(FullyQualifiedName("MyState", protoRef))),
        List(
          ModelBuilder.Command(
            "com.lightbend.MyService.Set",
            FullyQualifiedName("SetValue", protoRef),
            FullyQualifiedName("protobuf.Empty", protoRef)
          ),
          ModelBuilder.Command(
            "com.lightbend.MyService.Get",
            FullyQualifiedName("GetValue", protoRef),
            FullyQualifiedName("MyState", protoRef)
          )
        ),
        List.empty
      )
    )

    val sourceDoc = SourceGenerator.indexSource(entities)
    assertEquals(
      sourceDoc.layout,
      """import myservice1 from "./myservice1.js";
        |
        |myservice1.start();""".stripMargin
    )
  }
}
