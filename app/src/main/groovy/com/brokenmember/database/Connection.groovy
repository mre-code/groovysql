package com.brokenmember.database

import groovy.cli.commons.OptionAccessor
import groovy.json.JsonBuilder
import groovy.sql.Sql
import groovy.toml.TomlSlurper
import groovy.xml.MarkupBuilder
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter

import java.sql.SQLException
// import groovy.util.logging.Log

class Connection {

    def m_connection

    String m_dbHost
    String m_dbUser
    String m_dbPassword
    String m_dbName
    String m_dbClass
    String m_dbScheme
    String m_dbUrl
    String m_dbOptions

    Boolean m_timestamps

    String m_fileIn
    String m_fileOut
    Boolean m_overwrite

    Integer m_verbose

    String m_format
    Integer m_width

    String m_sqlStatement

    def m_dbConfigFile
    Map m_dbConfig = [:]

    Map m_connectionParameters = [:]

    static String timestamp() {
        return new Date().format("yyyy-MM-dd HH:mm:ss")
    }

    void displayOutput(Integer level, msg) {
        if (Math.abs(m_verbose) >= level) {
            if (m_timestamps) print "${timestamp()} :: "
            println msg
        }
    }

    void errorExit(String msg) {
        if (m_timestamps) print "${timestamp()} :: "
        println ">>> ERROR: $msg"
        System.exit(1)
    }

    Connection(OptionAccessor options) {

        m_dbUser = options.user
        m_dbPassword = options.password
        m_dbName = options.database
        m_dbHost = options.node
        m_dbConfigFile = options.config
        m_dbScheme = options.scheme
        m_sqlStatement = (options.sql ?: "") as String

        m_timestamps = (options.timestamps ?: false)

        m_fileIn = (options.filein ?: "/dev/stdin")
        m_fileOut = (options.fileout ?: "/dev/stdout")
        m_overwrite = options.overwrite

        m_format = (options.format ?: "text").toLowerCase()
        m_width = (options.width ?: 30)

        m_verbose = options.verbose

//        if (m_verbose >= 3) {
//            Sql.LOG.level = java.util.logging.Level.FINE
//            Logger.getLogger('groovy.sql').level = Level.FINE
//        }

        if (m_dbConfigFile) {
            m_dbConfig = new TomlSlurper().parse(new File(m_dbConfigFile))
            m_dbUser = m_dbConfig.dbUser
            m_dbPassword = m_dbConfig.dbPassword
            m_dbScheme = m_dbConfig.dbScheme
            m_dbHost = m_dbConfig.dbHost
            m_dbName = m_dbConfig.dbName
            m_dbClass = m_dbConfig.dbClass
            m_dbOptions = m_dbConfig.dbOptions
        }

        switch (m_dbScheme) {
            case "vdb":
                m_dbClass = "com.denodo.vdp.jdbc.Driver"
                m_dbOptions = m_dbOptions ?:
                        "?reuseRegistrySocket=true" +                    // for load balancer set to false
                        "&wanOptimizedCalls=false" +                     // optimize for WAN
                        "&queryTimeout=0" +                              // 0 ms = infinite
                        "&chunkTimeout=1000" +                           // fetch flush @ 10 secs
                        "&chunkSize=5000"                                // fetch flush @ 500 rows
                m_dbUrl = "jdbc:${m_dbScheme}://${m_dbHost}/${m_dbName}"
                break
            case "snowflake":
                m_dbClass = "net.snowflake.client.jdbc.SnowflakeDriver"
                m_dbName = "?db=${m_dbName}"
                m_dbOptions = m_dbOptions ?: "?queryTimeout=0"
                m_dbUrl = "jdbc:${m_dbScheme}://${m_dbHost}/${m_dbName}"
                break
            default:
                errorExit("dbscheme not specified")
        }

        m_connectionParameters = [
                url     : "${m_dbUrl}${m_dbOptions}",
                user    : m_dbUser,
                password: m_dbPassword,
                driver  : m_dbClass
        ]

        if (! options.testconnect) {
            displayOutput(1, "opening connection to ${m_dbUrl}")

            m_dbOptions.tokenize('?&').each {
                displayOutput(2,"dbOptions: $it")
            }

            try {
                m_connection = Sql.newInstance(m_connectionParameters)
                displayOutput(2, "successfully opened connection to ${m_dbUrl}")
            } catch (SQLException sqe) {
                displayOutput(0,">>> unable to open dbconnection to ${m_connectionParameters.url}; error:")
                displayOutput(0,sqe)
                displayOutput(0,"user=${m_dbUser}, word=${m_dbPassword.take(1)}****${m_dbPassword.reverse().take(1).reverse()}")
                System.exit(1)
            }
        }
    }

    void closeConnection() {
        displayOutput(1, "closing connection to ${m_dbUrl}")
        m_connection.close()
        displayOutput(2, "successfully closed connection to ${m_dbUrl}")
    }

    void flap(frequency) {
        displayOutput(1, "testing connection to ${m_dbUrl}")
        m_dbOptions.tokenize('?&').each {
            displayOutput(2,"dbOptions: $it")
        }
        List tokens = frequency.split("@")
        Integer iterations = tokens[0] as Integer
        Integer interval = tokens[1] as Integer
        interval = (interval ?: 1000)
        for (Integer i = 1; i <= iterations; ++i) {
            displayOutput(1, ">>> iteration ${i} of ${iterations} with a ${interval}ms delay")
            m_connection = Sql.newInstance(m_connectionParameters)
            displayOutput(1, "opened connection to ${m_dbUrl}")
            displayOutput(1, "sending query")
            m_connection.eachRow("select 1") {
                displayOutput(1, "processed and discarded result successfully")
            }
            closeConnection()
            if (i < iterations) {
                displayOutput(1, "waiting ${interval}ms")
                Thread.sleep(interval)
            }
        }
    }

    void formatTextResults(columns, resultSet, colWidths, colTypes) {
        // limit each column display to a max of width bytes
        colWidths.eachWithIndex { it, inx ->
            if (m_verbose >= 3) print "column '${columns[inx]}' width = $it (width option=$m_width)"
            colWidths[inx] = Math.min(it, m_width)
            if (m_verbose >= 3) println "... set to ${colWidths[inx]}" + ((colTypes[inx] <= 10) ? " right " : " left ") +
                    "justified"
        }
        new FileWriter(m_fileOut).withWriter { writer ->
            // output column heading, limit heading width to field width as sql does not
            columns.eachWithIndex { col, inx ->
                def limit = (colWidths[inx] > m_width) ? m_width : colWidths[inx]
                writer.printf("%-${colWidths[inx]}.${limit}s ", col)
            }
            writer.write("\n")
            // output column heading underline
            columns.eachWithIndex { col, inx ->
                writer.print("-" * colWidths[inx] + " ")
            }
            writer.write("\n")
            // output columns for each row, jdbcTypes > 10 are strings, otherwise numbers
            resultSet.each { row ->
                row.eachWithIndex { element, inx ->
                    writer.printf("%" + ((colTypes[inx] > 10) ? "-" : "") + "${colWidths[inx]}.${m_width}s ",
                            element.value)
                }
                writer.write("\n")
            }
        }
    }

    void formatCSVResults(columns, resultSet) {
        new FileWriter(m_fileOut).withWriter { writer ->
            new CSVPrinter(writer, CSVFormat.DEFAULT).with {
                printRecord(columns)
                resultSet.each { row ->
                    printRecord(row.values())
                }
            }
        }
    }

    void formatXMLResults(columns, resultSet) {
        new FileWriter(m_fileOut).withWriter { writer ->
            new MarkupBuilder(writer).with {
                rows {
                    resultSet.eachWithIndex { row, inx ->
                        rowResult {
                            mkp.comment("row: ${inx + 1}")
                            columns.each { col ->
                                "$col"(row[col])
                            }
                        }
                    }
                }
            }
            writer.write("\n")
        }
    }

    void formatHTMLResults(columns, resultSet) {
        new FileWriter(m_fileOut).withWriter { writer ->
            new MarkupBuilder(writer).with {
                table {
                    thead {
                        tr {
                            columns.each { col ->
                                th(col)
                            }
                        }
                    }
                    tbody {
                        resultSet.each { row ->
                            tr {
                                columns.each { col ->
                                    td(row[col])
                                }
                            }
                        }
                    }
                }
            }
            writer.write("\n")
        }
    }

    void formatJSONResults(columns, resultSet) {

        def json = new JsonBuilder()

// alternative 1: (quotes all values)

        json {
            rows(
                    resultSet.collect { row ->
                        columns.collectEntries { col ->
                            [col, row[col] as String]
                        }
                    }
            )
        }

// alternative 2: (doesn't quote numeric values)
//
//        json {
//            rows(
//                    resultSet.collect { row ->
//                        row.subMap(columns)
//                    }
//            )
//        }

// alternative 3: (doesn't quote numeric values)
//
//        json {
//            rows(
// 	              resultSet*.subMap(columns)
//            )
//        }

        new FileWriter(m_fileOut).withWriter { writer ->
            writer.write(json.toPrettyString())
            writer.write("\n")
        }
    }

    void processCommandInput(String command) {
        // process .command file input
        displayOutput(1, "processing command input: $command")
        List tokens = command.split(" ")
        switch (tokens[0]) {
            case ".output":
                m_fileOut = tokens[1]
                if (new File(m_fileOut).exists()) {
                    if (! m_overwrite) {
                        System.err.println "ERROR - file $m_fileOut already exists"
                        System.exit(1)
                    }
                }
                displayOutput(1, "output file set to: $m_fileOut")
                break
            case ".format":
                m_format = tokens[1]
                displayOutput(1, "output format set to: $m_format")
                break
            case ".overwrite":
                m_overwrite = tokens[1]
                displayOutput(1, "overwrite set to: $m_overwrite")
                break
            default:
                errorExit("unrecognized command input: $command")
                break
        }
    }

    void processSQL(String sql) {

        List data = []
        List cols = []
        List colw = []
        List colt = []

        displayOutput(2, "executing: $sql")

        try {
            data = m_connection.rows(sql) { metadata ->
                cols = (1..metadata.columnCount).collect { metadata.getColumnLabel(it) }
                colw = (1..metadata.columnCount).collect { metadata.getColumnDisplaySize(it) }
                colt = (1..metadata.columnCount).collect { metadata.getColumnType(it) }
            }
        } catch (SQLException sqe) {
            println sqe.getMessage()
            return
        }

        if (m_fileOut != "/dev/stdout") {
            def outFile = new File(m_fileOut)
            outFile.delete()
            outFile.createNewFile()
        }

        if (data.size() == 0) {
            return
        }

        switch (m_format) {
            case "text":
                formatTextResults(cols, data, colw, colt)
                break
            case "csv":
                formatCSVResults(cols, data)
                break
            case "html":
                formatHTMLResults(cols, data)
                break
            case "xml":
                formatXMLResults(cols, data)
                break
            case "json":
                formatJSONResults(cols, data)
                break
            default:
                displayOutput(0, "unrecognized format: $m_format")
                break
        }
    }

    void processUserInput() {
        // handle sql command line input
        processSQL(m_sqlStatement)
        m_sqlStatement = ""
        if (m_fileOut != "/dev/stdout") displayOutput(1, "output: $m_fileOut")
        closeConnection()
    }

    void processFileInput() {
        // handle file input
        if (m_fileIn != "/dev/stdin" && ! new File(m_fileIn).exists()) {
            errorExit("input file not found: $m_fileIn")
        }

        new FileReader(m_fileIn).withReader { reader ->
            reader.eachLine { line, lineno ->
                if (m_verbose >= 3) printf('input line %2d: %s\n', lineno, line)
                if (line.startsWith(".")) {
                    displayOutput(3, ">>> line $lineno: CONTROL RECORD: $line")
                    if (m_sqlStatement != "") errorExit("(line $lineno) discarding unterminated SQL = $m_sqlStatement")

                    processCommandInput(line)

                } else if (line ==~ /.*[^\\];\s*$|^;/) {       // non-escaped semicolon followed by only whitespace to eol
                    m_sqlStatement += " $line"

                    processSQL(m_sqlStatement)

                    m_sqlStatement = ""
                } else {
                    m_sqlStatement += " $line"
                }
            }
        }

        if (m_fileOut != "/dev/stdout") displayOutput(1, "output: $m_fileOut")
        if (m_sqlStatement != "") errorExit("discarding unterminated SQL = $m_sqlStatement")
        closeConnection()
    }
}
