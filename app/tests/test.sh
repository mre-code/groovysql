#!/bin/bash

function run_file_tests() {
    FORMAT=$1

    for TEST in ${TESTS[@]} 
    do
        TESTNAME=${TEST/*./}

	echo "... running $TESTNAME test (format=$FORMAT, options=$OPTIONS)"
	echo "... $EXEC $SQLCLIENT"

        $EXEC $SQLCLIENT                                          \
             --config  $TESTBASE/venture1.config                  \
             --filein  $TESTBASE/sqltest.$TESTNAME                \
             --fileout $TESTBASE/results-$TESTNAME.$FORMAT        \
             --format $FORMAT                                     \
             --verbose $VERBOSE                                   \
             $OPTIONS
    done
}

function run_cmdline_test() {
    FORMAT=$1

    echo "... running cmdline test (format=$FORMAT, options=$OPTIONS)"
    echo "... $EXEC $SQLCLIENT"

    SQL=$(cat $TESTBASE/sqltest.audit)

    $EXEC $SQLCLIENT                                         \
        --config  $TESTBASE/venture1.config                  \
        --sql "$SQL"                                         \
	      --format $FORMAT                               \
        --verbose $VERBOSE                                   \
        $OPTIONS
}

function run_stdio_test() {
    FORMAT=$1

    echo "... running stdio test (format=$FORMAT, options=$OPTIONS)"
    echo "... $EXEC $SQLCLIENT"

    cat $TESTBASE/sqltest.audit |
    $EXEC $SQLCLIENT                                         \
        --config  $TESTBASE/venture1.config                  \
        --format $FORMAT                                     \
        --verbose $VERBOSE                                   \
        $OPTIONS
}

function run_connection_test() {

    echo "... running connection test ($FREQUENCY, options=$OPTIONS)"
    echo "... $EXEC $SQLCLIENT"

    $EXEC $SQLCLIENT                                         \
        --config  $TESTBASE/venture1.config                  \
        --testconnect $FREQUENCY                             \
        $OPTIONS

}

MYNAME=~+/$0
VERBOSE=0
PROJECTBASE=$HOME/dox/repos/sqlclient
GROOVYBASE=$PROJECTBASE/app/src/main/groovy
TESTBASE=$PROJECTBASE/app/tests
WANTJAR=0

cd $PROJECTBASE || exit

cd $GROOVYBASE || exit

usage() { pod2usage -verbose 0 $MYNAME ; exit 1 ; }

while getopts :jFCST:hO:v: OPT
do
	case "$OPT" in
	F)	ACTION=run_file_tests ;;
	C)	ACTION=run_cmdline_test ;;
	S)	ACTION=run_stdio_test ;;
	T)	ACTION=run_connection_test ; FREQUENCY=$OPTARG ;;
	j)	WANTJAR=1 ;;
	O)	OPTIONS+=" --$OPTARG" ;;
	v)	VERBOSE=$OPTARG ;;
	h)  pod2usage -verbose 2 $MYNAME ; exit 0 ;;
	:)  echo "$MYNAME: Option $OPTARG requires a value" >&2; exit 2 ;;
	*)  usage;;
	esac
done
shift $(( OPTIND - 1 ))

if [[ $WANTJAR == 1 ]]
then
	EXEC="java -jar"
	SQLCLIENT=$PROJECTBASE/app/build/libs/app-all.jar
else
	EXEC="groovy"
	SQLCLIENT=com/brokenmember/database/SqlClient.groovy

	export CLASSPATH
	CLASSPATH+=:/app/d7/tools/client-drivers/jdbc/denodo-vdp-jdbcdriver.jar
	CLASSPATH+=:/app/denodo/lib/commons-csv-1.10.0.jar
fi

case $ACTION in
	*file*)
		if [ $1 ]
		then TESTS=($1); shift
		else TESTS=($TESTBASE/sqltest.* missing_input_file)
		fi
		;;
	*connect*)
		run_connection_test
		exit
		;;
	"")
		usage
		;;
esac

if [ $1 ]
then FORMATS=($@)
else FORMATS=(text csv xml html json)
fi

for FORMAT in ${FORMATS[@]}
do $ACTION $FORMAT
done

exit

##############################################################################

=head1 NAME

test.sh - SqlClient test suite

=head1 SYNOPSIS

    test.sh [-h] [-FCST] [-O options] [-v level] [test] [format]

=head1 DESCRIPTION

Provides a command line interface to run the various tests for SqlClient.

Formats supported:

  text
  csv
  html
  xml
  json

=head1 OPTIONS

=over 4

=item -h

Display this help information.

=item -F

Selects file-based tests.
By default runs all file-based tests in all formats.

=item -C

Selects command-line-based tests.
By default run a command-line-based test in all formats.

=item -S

Selects standard-input-based tests.
By default run a standard-input-based test in all formats.

=item -O options

Specifies options of "timestamps", "overwrite", and "extended".
Can be used more than once.
Extended option enables some debugging output.

=item -v level

Sets verbose level.
Default is 0 which disables all messages.
Level 1 enables informational messages and level 2 adds additional information.

=item -T frequency

Selects connection test.
Frequency set as <iterations>@<interval> where iterations are
the number of connections to be attempted and interval is
the number of milliseconds between attempts.
Iterations are required.
Interval defaults to 1000 (1 second) if not specifed.
Verbose level set to 1 by default.

=back

=head1 EXAMPLES

To run all file-based tests in all formats

  test.sh -F

To run a single file-based test with JSON output

  test.sh -F customer json

To run a stdio-based test in all formats with verbose level 2, and timestamps and extended options

  test.sh -S -v 2 -O timestamps -O extended

To run a command-line test in XML format

  test.sh -C xml

To run a connection test with 5 database connections attempted at 1 second intervals

  test.sh -T 5 -v 2

  To run a connection test with 3 database connections attempted at 30 second intervals

  test.sh -T 3@30000 -v 2