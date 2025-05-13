#!/bin/bash

# README.yaml:author: "Version 2.6"
# 
# gradle.properties:version=2.6
# 
# src/templates/md2pdf:# 		--variable author="Version 2.6"
# 
# src/main/groovy/net/venturechain/database/Connection.groovy:            displayOutput(1, "GroovySQL 2.6 powered by Groovy " +
# 
# src/main/groovy/net/venturechain/database/GroovySQL.groovy:                header: "Groovy SQL Client CLI version 2.6 ...
# 
# tests/.dev/mwe.md:author: "Version 2.6"
# 
# tests/test.sh:GROOVYSQL_VERSION=2.6

function set_version {
	OLD=$1
	NEW=$2
	FILE=$3

	echo; echo "updating $FILE"

	sed --in-place "s/$OLD/$NEW/" $FILE

	grep "$NEW" $FILE | sed 's/^/>>>> /'

}

NEWVERSION=${1?Usage: $0 new-version}

echo "setting version to $NEWVERSION"

set_version "Version 2.6" "Version $NEWVERSION" README.yaml

set_version "version=2.6" "version=$NEWVERSION" gradle.properties

set_version "Version 2.6" "Version $NEWVERSION" src/templates/md2pdf

set_version "GroovySQL 2.6" "GroovySQL $NEWVERSION" src/main/groovy/net/venturechain/database/Connection.groovy

set_version "version 2.6" "version $NEWVERSION" src/main/groovy/net/venturechain/database/GroovySQL.groovy

