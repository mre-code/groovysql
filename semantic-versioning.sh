#!/bin/bash

# gitversion inspects the current git state and calculates a semantic version number
# https://semver.org
# https://gitversion.net

function errexit { echo "ERROR: $*" ; exit 255 ; }

function get_version {
	STR="$1"
	FILE="$2"
	echo ">>> $FILE:"
	grep --line-number "$STR" $FILE
	echo
}

function set_version {
	OLD="$1"
	NEW="$2"
	FILE="$3"
	echo ">>> $FILE:"
	sed --in-place "s/$OLD/$NEW/" $FILE
	grep --line-number "$NEW" $FILE # | sed 's/^/>>>> /'
	echo
}

function update_version {
	# easiest implementation requires that gitversion be run
	# at the top of the git work tree (where the .git folder is)
	if [[ -d .git ]]
	then NEWVERSION=$(gitversion -showvariable fullsemver 2>/dev/null)
	else errexit "no .git directory found"
	fi

	if [[ $NEWVERSION ]]
	then echo "setting version to $NEWVERSION"; echo
	else errexit "gitversion did not return a version"
	fi

	set_version "Version SEMANTIC_VERSION" "Version $NEWVERSION" README.yaml
	set_version "version=SEMANTIC_VERSION" "version=$NEWVERSION" gradle.properties
	set_version "GroovySQL SEMANTIC_VERSION" "GroovySQL $NEWVERSION" src/main/groovy/net/venturechain/database/Connection.groovy
	set_version "version SEMANTIC_VERSION" "version $NEWVERSION" src/main/groovy/net/venturechain/database/GroovySQL.groovy
	set_version "GROOVYSQL_VERSION=SEMANTIC_VERSION" "GROOVYSQL_VERSION=$NEWVERSION" tests/test.sh
}

function reset_version {
	OLDVERSION="$1"
	NEWVERSION=SEMANTIC_VERSION

	echo "setting version to $NEWVERSION"; echo
	set_version "Version $OLDVERSION" "Version $NEWVERSION" README.yaml
	set_version "version=$OLDVERSION" "version=$NEWVERSION" gradle.properties
	set_version "GroovySQL $OLDVERSION" "GroovySQL $NEWVERSION" src/main/groovy/net/venturechain/database/Connection.groovy
	set_version "version $OLDVERSION" "version $NEWVERSION" src/main/groovy/net/venturechain/database/GroovySQL.groovy
	set_version "GROOVYSQL_VERSION=$OLDVERSION" "GROOVYSQL_VERSION=$NEWVERSION" tests/test.sh
}

function show_version {
	get_version "Version " README.yaml
	get_version "version" gradle.properties
	get_version "GroovySQL " src/main/groovy/net/venturechain/database/Connection.groovy
	get_version "version " src/main/groovy/net/venturechain/database/GroovySQL.groovy
	get_version "GROOVYSQL_VERSION=" tests/test.sh
}

case $1 in
	show)	show_version ;;
	update)	update_version ;;
	reset)	reset_version "${2?"from-version required"}" ;;
	*)	echo "Usage: $0 { show | update | reset }" ;;
esac
