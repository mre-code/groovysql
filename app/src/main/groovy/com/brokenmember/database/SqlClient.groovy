package com.brokenmember.database

import groovy.cli.commons.CliBuilder
import groovy.cli.commons.OptionAccessor
import groovy.util.logging.Slf4j

@Slf4j
class SqlClient {

    static void main(String[] args) {

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
