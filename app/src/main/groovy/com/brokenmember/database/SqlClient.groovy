package com.brokenmember.database

import groovy.cli.commons.CliBuilder
import groovy.cli.commons.OptionAccessor
import groovy.util.logging.Slf4j

//import org.apache.logging.log4j.LogManager
//import org.apache.logging.log4j.Logger
//import org.apache.logging.log4j.Marker
//import org.apache.logging.log4j.MarkerManager

//import org.apache.logging.log4j.Level
//import org.apache.logging.log4j.core.Filter
//import org.apache.logging.log4j.core.config.Configurator
//import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder
//import org.apache.logging.log4j.core.config.builder.api.ComponentBuilder
//import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder
//import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory
//import org.apache.logging.log4j.core.config.builder.api.FilterComponentBuilder
//import org.apache.logging.log4j.core.config.builder.api.LayoutComponentBuilder
//import org.apache.logging.log4j.core.config.builder.api.LoggerComponentBuilder
//import org.apache.logging.log4j.core.config.builder.api.RootLoggerComponentBuilder

@Slf4j
class SqlClient {

//    protected static final Logger parentLogger = LogManager.getLogger()
//
//    private Logger logger = parentLogger
//
//    protected Logger getLogger() {
//        return logger
//    }
//
//    protected void setLogger(Logger logger) {
//        this.logger = logger
//    }
//
//
//    public void log(Marker marker) {
//        logger.debug(marker,"Parent log message")
//    }

    static void main(String[] args) {

//        // configure console appender
//        ConfigurationBuilder builder
//                = ConfigurationBuilderFactory.newConfigurationBuilder()
//
//        AppenderComponentBuilder console
//                = builder.newAppender("stdout", "Console")
//
//        builder.add(console)
//
//        // configure log file
//        AppenderComponentBuilder file
//                = builder.newAppender("log", "File")
//
//        file.addAttribute("fileName", "sqlclient.log")
//
//        builder.add(file)
//
//        // configure rolling file appender
//        AppenderComponentBuilder rollingFile
//                = builder.newAppender("rolling", "RollingFile")
//
//        rollingFile.addAttribute("fileName", "sqlclient.log")
//
//        rollingFile.addAttribute("filePattern", "sqlclient-%d{MM-dd-yy}.log.gz")
//
//        builder.add(rollingFile)
//
//        // configure filters
//        FilterComponentBuilder flow = builder.newFilter(
//                "MarkerFilter",
//                Filter.Result.ACCEPT,
//                Filter.Result.DENY)
//
//        flow.addAttribute("marker", "FLOW")
//
//        console.add(flow)
//
//        // configure log record format
//        LayoutComponentBuilder standard
//                = builder.newLayout("PatternLayout")
//
//        standard.addAttribute("pattern", "%d [%t] %-5level: %msg%n%throwable")
//
//        console.add(standard)
//
//        file.add(standard)
//
//        rollingFile.add(standard)
//
//        // configure rootLogger
//        RootLoggerComponentBuilder rootLogger
//                = builder.newRootLogger(Level.ERROR)
//
//        rootLogger.add(builder.newAppenderRef("stdout"))
//
//        builder.add(rootLogger)
//
//        // configuring additional loggers
//        LoggerComponentBuilder logger = builder.newLogger("com", Level.DEBUG)
//
//        logger.add(builder.newAppenderRef("log"))
//
//        logger.addAttribute("additivity", false)
//
//        builder.add(logger);
//
//        // configuring other components, e.g. triggering policy for rolling file appenders
//        ComponentBuilder triggeringPolicies = builder.newComponent("Policies")
//                .addComponent(builder.newComponent("CronTriggeringPolicy")
//                        .addAttribute("schedule", "0 0 0 * * ?"))
//                .addComponent(builder.newComponent("SizeBasedTriggeringPolicy")
//                        .addAttribute("size", "1M"))
//
//        rollingFile.addComponent(triggeringPolicies)
//
//        // initialize logger
//        Configurator.initialize(builder.build())
//
//        Marker marker = MarkerManager.getMarker("CLASS")
//
//        SqlClient.log(marker)
        var cli = new CliBuilder(usage: "sqlclient [options]", posix: true, header: "Groovy SQL Client CLI\nOptions:")

        cli.with {
            h(longOpt: 'help', 'usage information')
            v(longOpt: 'verbose', 'specify verbose level', args: 1, type: int, defaultValue: "-1")
            f(longOpt: 'filein', 'specify input filename', args: 1)
            o(longOpt: 'fileout', 'specify output filename', args: 1)
            s(longOpt: 'scheme', 'specify database scheme', args: 1)
            d(longOpt: 'database', 'specify database name', args: 1)
            n(longOpt: 'node', 'specify database node', args: 1)
            c(longOpt: 'config', 'specify database configuration file', args: 1)
            F(longOpt: 'format', 'specify format', args: 1)
            w(longOpt: 'width', 'limit text column width', args: 1, type: int)
            a(longOpt: 'append', 'output file append mode')
            u(longOpt: 'user', 'specify database username', args: 1)
            p(longOpt: 'password', 'specify database password', args: 1)
            t(longOpt: 'timestamps', 'timestamp output')
            S(longOpt: 'sql', 'specify SQL statement', args: 1, defaultValue: "")
            T(longOpt: 'testconnect', 'run connection test', args:1)
            i(longOpt: 'interactive', 'run in interactive mode')
//          A(longOpt: 'authentication', 'specify filename', args: 1)  // TODO
        }

        OptionAccessor opt = cli.parse(args)

        if (opt.help || args.size() == 0) {
            cli.usage()
            return
        }

        if (opt.arguments()) {
            System.err.println "ERROR - unrecognized input: ${opt.arguments()}"
        }

        if (opt.fileout) {
            if (new File(opt.fileout).exists() and !opt.append) {
                System.err.println "ERROR - file $opt.fileout already exists"
                return
            } else {
                try {
                    var outFile = new File(opt.fileout)
                    outFile.delete()
                    outFile.createNewFile()
                } catch (exception) {
                    System.err.println "ERROR - $exception (${opt.fileout})"
                    return
                }
            }
        }

        if (opt.verbose >= 4) {
            println "System.properties:"
            for (key in System.properties.keySet().sort()) {
                printf('%-30s   %s\n', key, System.properties[key])
            }
        }

        log.trace "slf4j trace message"
        log.debug "slf4j debug message"
        log.info "slf4j info message"
        log.warn "slf4j warn message"
        log.error "slf4j error message"

        Connection connection = new Connection(opt)

        if (opt.testconnect) {
            connection.flap(opt.testconnect)
        } else if (opt.sql) {
            connection.processUserInput()
        } else if (opt.interactive) {
            connection.interactive()
        } else {
            connection.processFileInput()
        }
    }
}
