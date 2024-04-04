package com.brokenmember.database

import groovy.sql.Sql

import groovy.cli.commons.OptionAccessor

import groovy.json.JsonBuilder
import groovy.toml.TomlSlurper
import groovy.xml.MarkupBuilder
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter

import org.jline.reader.EndOfFileException
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.impl.DefaultParser
import org.jline.reader.impl.completer.StringsCompleter
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.jline.widget.AutopairWidgets

import org.apache.commons.lang3.SystemUtils

import java.sql.SQLException

class Connection {

    var m_connection

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
    Boolean m_append

    Integer m_verbose

    String m_format
    Integer m_width

    String m_sqlStatement

    var m_dbConfigFile
    Map m_dbConfig = [:]
    String m_dbDriverVersion

    Map m_connectionParameters = [:]

    String m_historyFile
    String m_historyIgnore = "exit*:quit*:.format *:.output *:.width *:.append *"

    static String timestamp() {
        return new Date().format("yyyy-MM-dd HH:mm:ss")
    }

    void displayOutput(Double level, msg) {
        println "... level = $level; msg = $msg"
        if (Math.abs(m_verbose) >= level) {
            String message = msg
            message.replaceAll("\t","    ").split('\n').each { String fragment ->
                println "... level = $level; frag = $fragment"
                if (m_timestamps) print "${timestamp()} :: "
                String displayLevel = String.valueOf(level)
                Integer displayIndent = displayLevel.substring(displayLevel.indexOf(".")+1) as Integer
                println "... displayIndent=$displayIndent"
                println " " * displayIndent + fragment
            }
        }
    }

    void errorExit(String msg) {
        if (m_timestamps) print "${timestamp()} :: "
        println ">>> ERROR: $msg"
        System.exit(2)
    }

    static String getDbDriverVersion(String propertyResource, String[] props) {
        Properties appProps = new Properties().tap {
            load(Thread.currentThread().contextClassLoader.getResourceAsStream(propertyResource))
        }
        List values = []
        props.each { String property ->
            values << appProps.getProperty(property)
        }
        values.collect().join("-")
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
        m_append = options.append

        m_format = (options.format ?: "text").toLowerCase()
        m_width = (options.width ?: 30)

        m_verbose = options.verbose

        Sql.LOG.level = java.util.logging.Level.OFF     // turn off groovy.sql default logging

        if (m_dbConfigFile) {
            m_dbConfig = new TomlSlurper().parse(new File(m_dbConfigFile))
            m_dbUser = m_dbConfig.dbUser
            m_dbPassword = m_dbConfig.dbPassword
            m_dbScheme = m_dbConfig.dbScheme
            m_dbHost = m_dbConfig.dbHost
            m_dbName = m_dbConfig.dbName
            m_dbClass = m_dbConfig.dbClass
            m_dbOptions = m_dbConfig.dbOptions.collect { it.value }.join('&')
        }

        switch (m_dbScheme) {
            case "vdb":
                m_dbClass = "com.denodo.vdp.jdbc.Driver"
                m_dbOptions = m_dbOptions ?:
                        "reuseRegistrySocket=true" +                    // for load balancer set to false
                                "&wanOptimizedCalls=false" +                     // optimize for WAN
                                "&queryTimeout=0" +                              // 0 ms = infinite
                                "&chunkTimeout=1000" +                           // fetch flush @ 10 secs
                                "&chunkSize=5000"                                // fetch flush @ 500 rows
                m_dbOptions = "?" + m_dbOptions
                m_dbUrl = "jdbc:${m_dbScheme}://${m_dbHost}/${m_dbName}"
                m_dbDriverVersion = "Denodo JDBC " +
                        getDbDriverVersion("conf/DriverConfig.properties",
                                "VDBJDBCDatabaseMetadata.driverVersion", "VDBJDBCDatabaseMetadata.driverUpdateVersion")
                break
            case "snowflake":
                m_dbClass = "net.snowflake.client.jdbc.SnowflakeDriver"
                m_dbName = "?db=${m_dbName}"
                m_dbOptions = m_dbOptions ?:
                        "queryTimeout=0"
                m_dbOptions = "&" + m_dbOptions
                m_dbUrl = "jdbc:${m_dbScheme}://${m_dbHost}/${m_dbName}"
                m_dbDriverVersion = "Snowflake JDBC " +
                        getDbDriverVersion("net/snowflake/client/jdbc/version.properties", "version")
                break
            default:
                errorExit("dbscheme not recognized (${m_dbScheme})")
        }

        if (m_verbose >= 1) {
            displayOutput(1,"SqlClient 2.0 powered by Groovy ${GroovySystem.version} with ${m_dbDriverVersion}")
        }

        m_connectionParameters = [
                url     : "${m_dbUrl}${m_dbOptions}",
                user    : m_dbUser,
                password: m_dbPassword,
                driver  : m_dbClass
        ]

        if (!options.testconnect) {
            displayOutput(1, "opening connection to ${m_dbUrl}")

            m_dbOptions.tokenize('?&').each {
                displayOutput(2, "dbOptions: $it")
            }

            try {
                m_connection = Sql.newInstance(m_connectionParameters)
                displayOutput(2, "successfully opened connection to ${m_dbUrl}")
            } catch (SQLException sqlException) {
                displayOutput(0, ">>> unable to open dbconnection to ${m_dbUrl}; error:")
                displayOutput(0.4, sqlException)
                displayOutput(0.4, "user=${m_dbUser}, word=${m_dbPassword.take(1)}****${m_dbPassword.reverse().take(1).reverse()}")
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
            displayOutput(2, "dbOptions: $it")
        }
        List tokens = frequency.split("@")
        Integer iterations = tokens[0] as Integer
        Integer interval = tokens[1] as Integer
        interval = (interval ?: 1)
        for (Integer i = 1; i <= iterations; ++i) {
            displayOutput(1, ">>> iteration ${i} of ${iterations} with a ${interval} sec delay")
            m_connection = Sql.newInstance(m_connectionParameters)
            displayOutput(1, "opened connection to ${m_dbUrl}")
            displayOutput(1, "sending query")
            m_connection.eachRow("select 1") {
                displayOutput(1, "processed and discarded result successfully")
            }
            closeConnection()
            if (i < iterations) {
                displayOutput(1, "waiting ${interval} sec")
                Thread.sleep(interval*1000)
            }
        }
    }

    void formatTextResults(resultSet, columnNames, colWidths, colTypes) {

        // limit each column display to a max of width bytes
        colWidths.eachWithIndex { var width, int i ->
            if (m_verbose >= 3) print "column '${columnNames[i]}' width = $width (width option=$m_width)"
            colWidths[i] = Math.min(width, m_width)
            if (m_verbose >= 3) println "... set to ${colWidths[i]}" +
                    ((colTypes[i] in [-6,-5,2,3,4,5,6,7,8]) ? " right " : " left ") +
                    "justified (type=${colTypes[i]})"
        }

        new FileWriter(m_fileOut, m_append).withWriter { writer ->

            // output column heading, limit heading width to field width as sql may not
            columnNames.eachWithIndex { var col, int i ->
                var limit = (colWidths[i] > m_width) ? m_width : colWidths[i]
                writer.printf("%-${colWidths[i]}.${limit}s ", col)
            }
            writer.write("\n")

            // output column heading underline
            columnNames.eachWithIndex { var col, int i ->
                writer.write("-" * colWidths[i] + " ")
            }
            writer.write("\n")

            // output result data
            String fmt
            resultSet.each { row ->
                row.eachWithIndex { var entry, int i ->
                    fmt = switch (colTypes[i]) {
                        case -6..-5 -> "%${colWidths[i]}d "             // tinyint, bigint
                        case 2 -> "%${colWidths[i]}.0f "                // numeric
                        case 3 -> "%${colWidths[i]}.2f "                // decimal
                        case 4..5 -> "%${colWidths[i]}d "               // integer, smallint
                        case 6..7 -> "%${colWidths[i]}.4f "             // float, real
                        case 8 -> "%${colWidths[i]}.1f "                // double
                        default -> "%-${colWidths[i]}.${m_width}s "     // everything else
                    }
                    writer.printf(fmt, row[i])
                }
                writer.write("\n")
            }
        }
    }

    void formatCSVResults(resultSet, columnNames) {
        new FileWriter(m_fileOut, m_append).withWriter { writer ->
            new CSVPrinter(writer, CSVFormat.DEFAULT).with {
                printRecord(columnNames)
                resultSet.each { row ->
                    printRecord(row.values())
                }
            }
        }
    }

    void formatXMLResults(resultSet, columnNames) {
        new FileWriter(m_fileOut, m_append).withWriter { writer ->
            new MarkupBuilder(writer).with {
                rows {
                    resultSet.eachWithIndex { var row, int rowid ->
                        rowResult {
                            mkp.comment("row: ${rowid + 1}")
                            columnNames.each { col ->
                                "$col"(row[col])
                            }
                        }
                    }
                }
            }
            writer.write("\n")
        }
    }

    void formatHTMLResults(resultSet, columnNames) {
        new FileWriter(m_fileOut, m_append).withWriter { writer ->
            new MarkupBuilder(writer).with {
                table {
                    thead {
                        tr {
                            columnNames.each { col ->
                                th(col)
                            }
                        }
                    }
                    tbody {
                        resultSet.each { row ->
                            tr {
                                columnNames.each { col ->
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

    void formatJSONResults(resultSet, columnNames) {

        var json = new JsonBuilder()

// alternative 1: (quotes all values)

        json {
            rows(
                    resultSet.collect { row ->
                        columnNames.collectEntries { col ->
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
//                        row.subMap(columnNames)
//                    }
//            )
//        }

// alternative 3: (doesn't quote numeric values)
//
//        json {
//            rows(
// 	              resultSet*.subMap(columnNames)
//            )
//        }

        new FileWriter(m_fileOut, m_append).withWriter { writer ->
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
                if (new File(m_fileOut).exists() && !m_append) {
                    errorExit("file $m_fileOut already exists")
                }
                displayOutput(1, "output file set to: $m_fileOut")
                break
            case ".format":
                m_format = tokens[1]
                displayOutput(1, "output format set to: $m_format")
                break
            case ".append":
                m_append = tokens[1]
                displayOutput(1, "append set to: $m_append")
                break
            case ".width":
                m_width = tokens[1] as Integer
                displayOutput(1, "width set to: $m_width")
                break
            default:
                errorExit("unrecognized command input: $command")
                break
        }
    }

    void processSQL(String sql) {

        List data = []
        List colNames = []
        List colWidths = []
        List colTypes = []

        var metaClosure = { metadata ->
            colNames = (1..metadata.columnCount).collect { metadata.getColumnLabel(it) }
            colWidths = (1..metadata.columnCount).collect { metadata.getColumnDisplaySize(it) }
            colTypes = (1..metadata.columnCount).collect { metadata.getColumnType(it) }
        }

        displayOutput(2.4, "executing: $sql")

        try {
            m_connection.execute(sql, metaClosure) { isResultSet, result ->
                if (isResultSet) {
                    data = result
                } else {
                    displayOutput(0, "updated rowcount: $result")
                }
            }
        } catch (exception) {
            displayOutput(0, ">>> error:")
            displayOutput(0.4, exception)
            displayOutput(0, " ")
            return
        }

        if (data.size() == 0) {
            return
        }

        switch (m_format) {
            case "text":
                formatTextResults(data, colNames, colWidths, colTypes)
                break
            case "csv":
                formatCSVResults(data, colNames)
                break
            case "html":
                formatHTMLResults(data, colNames)
                break
            case "xml":
                formatXMLResults(data, colNames)
                break
            case "json":
                formatJSONResults(data, colNames)
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
        if (m_fileIn != "/dev/stdin" && !new File(m_fileIn).exists()) {
            errorExit("input file not found: $m_fileIn")
        }

        new FileReader(m_fileIn).withReader { reader ->
            reader.eachLine { line, lineno ->
                if (m_verbose >= 3) displayOutput(3, sprintf('input line %2d: %s', lineno, line))
                if (line.startsWith(".")) {
                    displayOutput(3, ">>> line $lineno: CONTROL RECORD: $line")
                    if (m_sqlStatement != "") errorExit("(line $lineno) discarding unterminated SQL = $m_sqlStatement")

                    processCommandInput(line)

                } else if (line ==~ /.*[^\\];\s*$|^;/) {
                    // non-escaped semicolon followed by only whitespace to eol
                    m_sqlStatement += "\n$line"

                    processSQL(m_sqlStatement)

                    m_sqlStatement = ""
                } else {
                    m_sqlStatement += "\n$line"
                }
            }
        }

        if (m_fileOut != "/dev/stdout") displayOutput(1, "output: $m_fileOut")
        if (m_sqlStatement != "") errorExit("discarding unterminated SQL = $m_sqlStatement")
        closeConnection()
    }

    void interactive() {

        m_historyFile = "${SystemUtils.getUserHome()}/.sqlclient_history"

        Terminal terminal = TerminalBuilder.terminal()

        DefaultParser parser = new DefaultParser()
        parser.setEofOnUnclosedBracket(DefaultParser.Bracket.CURLY,
                DefaultParser.Bracket.ROUND, DefaultParser.Bracket.SQUARE)

        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(new StringsCompleter("select", "insert", "update", "delete", "create", "drop"))
                .parser(parser)
                .variable(LineReader.SECONDARY_PROMPT_PATTERN, "%M%P > ")
                .variable(LineReader.INDENTATION, 2)
                .option(LineReader.Option.INSERT_BRACKET, true)
                .variable(LineReader.HISTORY_FILE, m_historyFile)
                .variable(LineReader.HISTORY_FILE_SIZE, 10_000)
                .variable(LineReader.HISTORY_IGNORE, m_historyIgnore)
                .build()

        AutopairWidgets autopairWidgets = new AutopairWidgets(reader, true)

        autopairWidgets.enable()

        String line

        while (true) {

            try {
                line = reader.readLine("> ")
                if (line == null || line.equalsIgnoreCase("quit")) {
                    break
                }
            } catch (exception) {
                if (exception = EndOfFileException) return
            }

            reader.getHistory().add(line)

            if (line.startsWith(".")) {
                processCommandInput(line)
            } else if (line ==~ /.*[^\\];\s*$|^;/) {
                // statement terminated (non-escaped semicolon followed by only whitespace to eol)
                m_sqlStatement += line

                processSQL(m_sqlStatement)

                m_sqlStatement = ""
            } else {
                m_sqlStatement += "$line\n"
            }

            reader.getHistory().save()
        }
    }
}
