# SqlClient - Groovy SQL Client

SqlClient is a database client designed primarily for batch SQL submission.
It is written in Groovy, which compiles to Java, and is compatible with 
vendor-provided Java database drivers.

It takes input from any one of standard input, command line, or file.
SQL statements normally must be terminated with a semi-colon although
for command-line input the semi-colon is optional.

Output formats supported are:

    text
    csv
    html
    xml
    json

SqlClient is careful to avoid overwriting any existing files and will abort
in the event of a conflict unless the _overwrite_ option is in effect.

## Command Line Options

**sqlclient [options]**

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
    -T,--testconnect <arg>      run connection test
    -u,--user <arg>             specify database username
    -v,--verbose <arg>          specify verbose level
    -w,--width <arg>            limit text column width

## Usage

SqlClient reads SQL, submits it to a connected database, and formats the results
in one of the output formats selected.  

The SQL input can come from a disk file, the command line through the --sql option, 
or from standard input (keyboard or pipe).

SqlClient also supports a control record capability.  Control records allow 
directives to be processed during the SQL processing.  

Directives supported are:

    .format <type>
    .output <filename>
    .overwrite

SqlClient supports various verbose levels as well as a timestamp option
for runtime feedback.

## Verbose levels

    level 0 - no messages (except data of course) 
    level 1 - basic messages (open/close)
    level 2 - enhanced messages (adds open/close success, query)
    level 3 - debug messages (adds input trace, text format field adjustments)
    level 4 - debug messages (adds system.properties display)

## Config file format

Config files are optional files containing all the database connection parameters.
They are written in TOML format and support the following parameters:

    dbUser      - the database username
    dbPassword  - the database password
    dbScheme    - the JDBC scheme (tested with "vdb" for Denodo and "snowflake" for Snowflake)
    dbHost      - the TCP network address (hostname:port)
    dbName      - the database name
    dbClass     - the database driver class name (optional, defaults based on dbScheme setting) 
                    (tested with com.denodo.vdp.jdbc.Driver and net.snowflake.client.jdbc.SnowflakeDriver) 

## Examples

**File itemdb.config**

    dbUser = "appuser"
    dbPassword = "appword"
    dbScheme = "vdb"
    dbHost = "dbhost.mydomain.com:9999"
    dbName = "itemdb"
    dbClass = "com.denodo.vdp.jdbc.Driver"

**File item-extract.sql:**

    .format json
    .output ../extract/items.json
    .overwrite
    SELECT * FROM ITEM;

**Executing item-extract.sql**

`sqlclient --config=itemdb.config --filein=item-extract.sql`

**Executing a SQL statement**

`sqlclient --config=itemdb.config --sql "SELECT * FROM ITEM"`

`echo "SELECT * FROM ITEM" | sqlclient --config=itemdb.config`

Either of these examples will run the query and send the output to the screen.

**File txn_audit.sql**

    select
      100                           -- unnamed integer field
    , 200 as "named field 1"        -- field name longer than field width
    , audit_dt as "audit date"      -- date/time field with named field with space
    , audit_reason                  -- standard varchar field
    , item_id                       -- short varchar field (6)
    , price                         -- double
    
    from
        txn_audit
    join
        txn
    on
        txn.txn_id = txn_audit.txn_id
    ;

Executing txn_audit.sql to produce txn_audit.xml in XML format (shown without --config option)

`sqlclient -s vdb -n dbhost.mydomain.com:9999 -d itemdb -u appuser -p appword -f txn_audit.sql -o txn_audit.xml -F xml`

Sample execution

    $ sqlclient --config=itemdb.config --sql "SELECT ITEM_ID, DESCRIPTION FROM ITEM LIMIT 3"
    
    item_id     description
    ----------- ------------------------------
         986461 Magnotta - Bel Paese White
         316882 Beer - Fruli,IPA
         887875 Marlbourough Sauv Blanc
