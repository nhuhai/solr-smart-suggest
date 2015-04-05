# solr-smart-suggest

You need to include the following jar files to classpath in order to compile solr-smart-suggest components:
- lucene-core-4.10.3-SNAPSHOT.jar
- lucene-analyzers-common-4.10.3-SNAPSHOT.jar
- lucene-suggest-4.10.3-SNAPSHOT.jar
- solr-core-4.10.3-SNAPSHOT.jar
- solr-solrj-4.10.3-SNAPSHOT.jar
- slf4j-api-1.7.6.jar


From solr-smart-suggest directory, run:

javac -d ./bin -cp ../build/lucene-libs/lucene-core-4.10.3-SNAPSHOT.jar:../build/lucene-libs/lucene-analyzers-common-4.10.3-SNAPSHOT.jar:../build/lucene-libs/lucene-suggest-4.10.3-SNAPSHOT.jar:../build/solr-core/solr-core-4.10.3-SNAPSHOT.jar:../dist/solr-solrj-4.10.3-SNAPSHOT.jar:/Users/hainguyen/.ivy2/cache/org.slf4j/slf4j-api/jars/slf4j-api-1.7.6.jar src/java/org/apache/solr/spelling/suggest/*.java src/java/org/apache/lucene/search/suggest/analyzing/SmartAnalyzingSuggester.java

cd bin
jar cf temp.jar org 


javac -d ./bin -cp ../build/lucene-libs/lucene-core-4.10.3-SNAPSHOT.jar:../build/lucene-libs/lucene-analyzers-common-4.10.3-SNAPSHOT.jar:../build/lucene-libs/lucene-suggest-4.10.3-SNAPSHOT.jar:../build/solr-core/solr-core-4.10.3-SNAPSHOT.jar:../dist/solr-solrj-4.10.3-SNAPSHOT.jar:/Users/hainguyen/.ivy2/cache/org.slf4j/slf4j-api/jars/slf4j-api-1.7.6.jar:bin/temp.jar src/java/org/apache/solr/handler/component/SmartSuggestComponent.java

cd bin
jar cf smart.jar org


Changes in Solr Code base:
- org.apache.lucene.search.suggest.analyzing.FSTUtil:  add "public" to "T output" (reason: same package name but different classloader, so it doesn't allow to access private field at run time.)


Commands to start SolrCloud, 2 shards

1st instance
java -Dcollection.configName=logmill -DzkRun -DnumShards=2 -Dbootstrap_confdir=./solr/logmill/conf -jar start.jar


2nd instance
java -DzkHost=localhost:9983 -Djetty.port=8984 -jar start.jar 







