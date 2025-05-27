#!/bin/sh
./gradlew knoi-annotation:clean && ./gradlew knoi-annotation:assemble
./gradlew knoi-annotation:publish

./gradlew knoi-processor:clean && ./gradlew knoi-processor:assemble
./gradlew knoi-processor:publish

./gradlew knoi:clean && ./gradlew knoi:assemble
./gradlew knoi:publish

./gradlew knoi-gradle-plugin:clean && ./gradlew knoi-gradle-plugin:assemble
./gradlew knoi-gradle-plugin:publish