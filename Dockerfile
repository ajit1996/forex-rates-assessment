FROM openjdk:11
RUN apt-get -y update
RUN apt-get install -y curl

ENV SBT_VERSION 1.8.2
COPY . .

RUN curl -L -o sbt-$SBT_VERSION.zip https://github.com/sbt/sbt/releases/download/v1.5.5/sbt-$SBT_VERSION.zip
RUN chmod -R 777 sbt-$SBT_VERSION.zip
RUN unzip sbt-1.8.2 -d ops

EXPOSE 8080

CMD ops/sbt/bin/sbt run