FROM openjdk:8

WORKDIR /app

COPY . /app

COPY forex.jar /app/forex.jar

ENV SCALA_VERSION=2.13.8

ENV SBT_HOME /app/sbt/bin

ENV PATH ${PATH}:${SBT_HOME}

EXPOSE 8080

CMD sbt run