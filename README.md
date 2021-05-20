# AES RoCC Accelerator/Co-Processor --- EE290C Spring 2021
AES RoCC Accelerator for baremetal machines.

## Team Members
Anson Tsai (TsaiAnson), Eric Wu (ericwu13), Daniel Fan (gobears)

## Table of Contents
[Brief Intro](#brief-intro)

[Installation Instructions](#installation-instructions)

## Brief Intro
The AES RoCC Accelerator enables hardware accelerated AES block cipher operations for baremetal machines.
It is built using [Secwork's open-source AES core](https://github.com/secworks/aes) and additional hardware logic for RoCC integration.
The accelerator also comes with a custom software stack that can be found in the EE290C software stack repo 
[here](https://bwrcrepo.eecs.berkeley.edu/EE290C_EE194_tstech28/ee290c-software-stack).

To add the accelerator to a rocket configuration, simply import the `aes` project (installation instructions below) and add the accelerator configuration:
```
import aes._

...

class BaremetalRocketConfig extends Config(
  ...
  new aes.WithAESAccel ++
  ...
)
```

### Top-Level Diagram and Implementation
The top-level accelerator block diagram is shown below:

![diagram](https://github.com/ucberkeley-ee290c/sp21-aes-rocc-accel/blob/master/diagrams/AESAccelTopLevelDiagram.png?raw=true)

For a brief description, the AES RoCC Accelerator communicates with the rocket core via RISC-V instructions transmitted on `RoCCIO` interface and connects to the memory bus via a `TileLink` interface.
Accelerator instructions from the CPU are processed by the `RoCC Decoupler`, which processes instructions in a non-blocking fashion and retains important information for the `Controller`.
The `Controller` is responsible for taking the information from the `RoCC Decoupler` and operating the `SecWorks AES core` by performing the necessary setup steps and initiating the core. 
To fetch the input data (key and text data) and write back output data (encrypted/decrypted text), the controller sends memory requests to the DMA, which interfaces with the memory bus.

### More Documentation/Spec
For more information on the implementation of the accelerator, documentation can be found in the chip spec 
[here](https://docs.google.com/document/d/1J9azqokkR0AsUUAkwU-hotsNtb-0KX5duK7d7f_3MhI/edit?usp=sharing) (you may need to request read access).


## Requirements
The AES RoCC Accelerator utilizes a DMA generator and the chisel verification library.
As such, this accelerator generator must be built alongside Chipyard.

### Installing Chipyard
The Chipyard repo and installation instructions can be found at: https://github.com/ucb-bar/chipyard.
Note that the installation instructions below require Chipyard version 1.3.0 or later.

### Installing Chisel Verification Library
Note that we start in the chipyard root directory.
```
~/chipyard> cd tools
~/chipyard/tools> git submodule add https://github.com/TsaiAnson/verif.git
```

### Installing the DMA Generator
Note that we start in the chipyard root directory.
```
~/chipyard> cd generators
~/chipyard/generators> git submodule add https://github.com/ucberkeley-ee290c/sp21-dma
```


### Installing the AES RoCC Accelerator Generator
Note that we start in the chipyard root directory.
```
~/chipyard> cd generators
~/chipyard/generators> git submodule add https://github.com/ucberkeley-ee290c/sp21-aes-rocc-accel
```

### Modifying your build.sbt
Add the following snippet to the end of `chipyard/build.sbt`:
```
val directoryLayout = Seq(
  scalaSource in Compile := baseDirectory.value / "src",
  javaSource in Compile := baseDirectory.value / "resources",
  resourceDirectory in Compile := baseDirectory.value / "resources",
  scalaSource in Test := baseDirectory.value / "test",
  javaSource in Test := baseDirectory.value / "resources",
  resourceDirectory in Test := baseDirectory.value / "resources",
)

val verifSettings = Seq(
  resolvers ++= Seq(
    Resolver.sonatypeRepo("snapshots"),
    Resolver.sonatypeRepo("releases"),
    Resolver.mavenLocal
  ),
  scalacOptions := Seq("-deprecation", "-unchecked", "-Xsource:2.11", "-language:reflectiveCalls"),
  libraryDependencies += "edu.berkeley.cs" %% "chiseltest" % "0.3.1",
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.+" % "test"
)

lazy val verifCore = (project in file("./tools/verif/core"))
  .settings(directoryLayout)
  .sourceDependency(chiselRef, chiselLib)
  .dependsOn(rocketchip, dsptools, `rocket-dsptools`)
  .settings(commonSettings)
  .settings(verifSettings)

lazy val verifTL = (project in file("./tools/verif/tilelink"))
  .settings(directoryLayout)
  .sourceDependency(chiselRef, chiselLib)
  .dependsOn(verifCore)
  .settings(commonSettings)
  .settings(verifSettings)

lazy val verifGemmini = (project in file("./tools/verif/cosim"))
  .settings(directoryLayout)
  .sourceDependency(chiselRef, chiselLib)
  .dependsOn(verifCore, verifTL)
  .settings(commonSettings)
  .settings(verifSettings)
  .settings(libraryDependencies += "com.google.protobuf" % "protobuf-java" % "3.14.0")
  .settings(libraryDependencies += "com.google.protobuf" % "protobuf-java-util" % "3.14.0")

lazy val dma = (project in file("generators/dma"))
  .sourceDependency(chiselRef, chiselLib)
  .dependsOn(verifCore, verifTL, verifGemmini)
  .settings(commonSettings)

lazy val aes = (project in file("generators/aes"))
  .sourceDependency(chiselRef, chiselLib)
  .dependsOn(verifCore, verifTL, verifGemmini, dma)
  .settings(commonSettings)
  .settings(verifSettings)
```

### Compiling and running the tests
Note that we start in the chipyard root directory.
```
~/chipyard> cd sims/verilator
~/chipyard/sims/verilator> make launch-sbt
sbt:chipyardRoot> project aes
sbt:aes> compile                       // If you just want to compile src code
sbt:aes> test:compile                  // If you just want to compile test code
sbt:aes> testOnly aes.dcplrSanityTest  // Compiles all dependencies and runs test
```
