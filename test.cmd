call mvn clean package

call java -javaagent:agent/target/aprof.jar=track=com.devexperts.aproftest.Test$Tracked -cp core/target/test-classes com.devexperts.aproftest.Test

call java -cp core/target/test-classes;agent/target/aprof.jar com.devexperts.aproftest.Test
