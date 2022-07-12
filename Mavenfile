#-*- mode: ruby -*-

load File.expand_path('lib/jrjackson/build_info.rb', File.dirname(__FILE__))
VERSION = ::JrJackson::BuildInfo.jar_version
gemspec :jar => "jrjackson/jars/jrjackson-#{VERSION}.jar"

# overwrite groupId:artifacgtId:version from gem
id "com.jrjackson.jruby:jrjackson:#{VERSION}"
packaging :jar

properties 'project.build.sourceEncoding' => 'UTF-8',
           # create a pom.xml from this here
           'polyglot.dump.pom' => 'pom.xml'

jar 'junit:junit', '4.11', :scope => :test

jar 'org.jruby:jruby', '9.2.13.0', :scope => :provided

plugin :compiler, '3.1', :source => '1.8', :target => '1.8',
       :showDeprecation => false,
       :showWarnings => false,
       :executable => '${JAVA_HOME}/bin/javac',
       :fork => true

plugin :surefire, '2.17', :skipTests => true
