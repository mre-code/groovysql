package com.brokenmember.database

import groovy.cli.commons.CliBuilder
import groovy.cli.commons.OptionAccessor

class SqlClient {

    static void main(String[] args) {

        def cli = new CliBuilder(usage: "sqlclient [options]", posix: true, header: "Groovy SQL Client CLI\nOptions:")

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
            O(longOpt: 'overwrite', 'overwrite output file')
            u(longOpt: 'user', 'specify database username', args: 1)
            p(longOpt: 'password', 'specify database password', args: 1)
            t(longOpt: 'timestamps', 'timestamp output')
            S(longOpt: 'sql', 'specify SQL statement', args: 1, defaultValue: "")
            T(longOpt: 'testconnect', 'run connection test', args:1)
//          a(longOpt: 'authentication', 'specify filename', args: 1)  // TODO
        }

        OptionAccessor opt = cli.parse(args)

        if (opt.help || args.size() == 0) {
            cli.usage()
            return
        }

        if (opt.arguments()) {
            System.err.println "ERROR - unrecognized input: ${opt.arguments()}"
        }

        if (opt.fileout && new File(opt.fileout).exists()) {
            if (!opt.overwrite) {
                System.err.println "ERROR - file $opt.fileout already exists"
                return
            }
        }

        if (opt.verbose >= 4) {
            for (key in System.properties.keySet().sort()) {
                printf('%-30s   %s\n', key, System.properties[key])
            }
       }

       Connection connection = new Connection(opt)

        if (opt.testconnect) {
            connection.flap(opt.testconnect)
        } else if (opt.sql) {
            connection.processUserInput()
        } else {
            connection.processFileInput()
        }

        connection.closeConnection()
    }
}
