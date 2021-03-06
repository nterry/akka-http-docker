# Akka HTTP Docker 

`akka-http-docker` is a Docker client that is based on Akka HTTP. It's aim is to provide a Scala and Akka Streams-centric approach to interacting with Docker.

### Building

Use SBT:

    $ sbt package
    
### Running tests

Before running the tests, you need the `alpine:latest` docker image:

    $ docker pull alpine:latest
    
Then use SBT to run the tests:

    $ sbt test
    
#### On Mac OS X

If you are running Docker using [Docker Toolbox](https://docs.docker.com/mac/step_one/), 
then launch Docker Quickstart Terminal, navigate to this project's directory, and
run `sbt` from there.
    
### Including as a Dependency

To include the `akka-http-docker` in your project as a dependency, use the Sonatype Snapshot repositories:

```scala
    resolvers ++= Seq(
      Resolver.sonatypeRepo("snapshots")
    ),
    
    libraryDependencies ++= {
      Seq(
        // Akka HTTP Docker
        "com.jbrisbin.docker" %% "akka-http-docker" % "0.1.0-SNAPSHOT"
      )
    }
```
    
### Using

The API will be simple in order to express the maximum amount of information with the least amount of static. To list all containers, and map the IDs of those containers to another processing chain, you would do the following:

```scala
val docker = Docker()

docker.containers() onComplete {
  case Success(c) => c.map(_.Id)
  case Failure(ex) => log.error(ex.getMessage, ex)
}
```

#### Starting a Container

To start a container, you may need to create one first. You can map the output of the create into another step using `flatMap`, though, so it can all be done in one step.

To create and start a container, use something like this:

```scala
val docker = Docker()

docker.create(CreateContainer(
    Image="alpine", 
    Volumes = Map("/Users/johndoe/src/myapp" -> "/usr/lib/myapp")
  ))
  .flatMap(c => docker.start(c.Id))
  .map(c => c ! Exec(Seq("/usr/lib/myapp/bin/start.sh")))
```

You can also use a `for` comprehension:

```scala
val docker = Docker()

for {
  c <- docker.create(CreateContainer(Image="alpine", Volumes = Map("/Users/johndoe/src/myapp" -> "/usr/lib/myapp")))
  ref <- docker.start(c.Id)
  exec <- ref ! Exec(Seq("/usr/lib/myapp/bin/start.sh"))
} yield exec
```

The `start()` method returns a `Future[ActorRef]` that represents your link to the container. To interact with a running container, send it messages using `!` or `?`.

### Contributions Welcome!

This is a new project in its infancy. There is a LOT to do if this tool is to be useful to more than just a handful of use cases. If you have the time and inclination, please create a ticket or submit a PR with helpful changes that expand the client functionality and implement some new features that you need.

### License

Apache 2.0
