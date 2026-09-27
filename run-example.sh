#!/bin/sh
set -eu
classes="${TMPDIR:-/tmp}/creator-digest-classes"
mkdir -p "$classes"
javac -d "$classes" $(find src/main/java -name '*.java')
java -cp "$classes" example.digest.ScheduleCreatorDigest
