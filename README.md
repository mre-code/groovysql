# SqlClient - Groovy SQL Client

SqlClient is a database client designed primarily for batch SQL submission.
It is written in Groovy, which compiles to Java, and is compatible with 
vendor-provided Java database drivers.

It takes input from any one of standard input, command line, or file.
SQL statements normally must be terminated with a semi-colon although
for command-line input the semi-colon is optional.

Output formats supported are:

> tsxt
> csv
> html
> xml
> json

## Command Line Options

sqlclient [options]
 -c,--config <arg>           specify database configuration file
 -d,--database <arg>         specify database name
 -f,--filein <arg>           specify input filename
 -F,--format <arg>           specify format
 -h,--help                   usage information
 -n,--node <arg>             specify database node
 -o,--fileout <arg>          specify output filename
 -O,--overwrite              overwrite output file
 -p,--password <arg>         specify database password
 -s,--scheme <arg>           specify database scheme
 -S,--sql <arg>              specify SQL statement
 -t,--timestamps             timestamp output
 -T,--testconnection <arg>   run connection test
 -u,--user <arg>             specify database username
 -v,--verbose <arg>          specify verbose level
 -w,--width <arg>            limit text column width
 -x,--extended               specify extended display
