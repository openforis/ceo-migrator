@GrabConfig(systemClassLoader=true)
@Grab(group='com.h2database', module='h2', version='1.4.197')
@Grab(group='commons-codec', module='commons-codec', version='1.11')
@Grab(group='commons-io', module='commons-io', version='2.5')

import java.sql.*
import org.h2.jdbcx.JdbcConnectionPool
import org.h2.jdbc.JdbcSQLException
import java.security.MessageDigest
import org.apache.commons.codec.binary.Hex
import org.apache.commons.io.IOUtils
import groovy.sql.Sql
import groovy.json.JsonSlurper 

Sql.LOG.level = java.util.logging.Level.SEVERE

def encodePassword(plainPassword) {
    MessageDigest messageDigest = MessageDigest.getInstance("MD5")
    byte[] digest = messageDigest.digest(plainPassword.getBytes())
    char[] resultChar = Hex.encodeHex(digest)
    return new String(resultChar)
}

def readJsonStringFromFile(filename) {
    def file = new File(filename)
    assert file.exists(): "file not found"
    assert file.canRead(): "file cannot be read"
    def jsonString = file.getText("UTF-8")
    return jsonString
}

def importUsers(jsonArray, jdbcConnectionString, autoCommit) {
    JdbcConnectionPool cp = JdbcConnectionPool.create(jdbcConnectionString, "", "")
    Connection conn = cp.getConnection()
    conn.autoCommit = autoCommit
    jsonArray.each() {
        String password = encodePassword(it.password)
        int userId = it.id
        def sql = new Sql(conn)
        def insertSql = "INSERT INTO OF_USERS.OF_USER (ID, USERNAME, PASSWORD, ENABLED, LAT, LON, LOCATION, AFFILIATIONS, CREATION_DATE, RESET_KEY) VALUES (?, ?, ?, ?, ?, ?, ?, ?, now(), ?)"
        def params = [userId, it.email, password, true, null, null, "", "", ""]
        try {
            sql.executeInsert insertSql, params
        } catch (JdbcSQLException e) {
            println "ERROR! Impossible to insert User with ID = ${userId}"
        }
    }
    conn.close()
    cp.dispose()
}

def importGroups(jsonArray, jdbcConnectionString, logosPath, autoCommit) {
    JdbcConnectionPool cp = JdbcConnectionPool.create(jdbcConnectionString, "", "")
    Connection conn = cp.getConnection()
    conn.autoCommit = autoCommit
    jsonArray.each() {
        int groupId = it.id
        try {
            def sql = new Sql(conn)
            def exists = sql.firstRow("SELECT ID FROM OF_USERS.OF_GROUP WHERE ID = ${groupId}")?.ID
            if (!exists) {
                def enabled = it.archived || false
                def insertSql = "INSERT INTO OF_USERS.OF_GROUP (ID, NAME, LABEL, DESCRIPTION, ENABLED, SYSTEM_DEFINED, VISIBILITY_CODE, LOGO, URL, CREATION_DATE, LOGO_CONTENT_TYPE) VALUES (?, ?, ?, ?, ?, true, ?, ?, ?, now(), ?)"
                def params = [groupId, it.name, it.name, it.description, enabled, "PUB", "", it.url, ""]
                sql.executeInsert insertSql, params
            }
            def members = it.get('members')
            def admins = it.get('admins')
            def pending = it.get('pending')
            assert members instanceof List
            assert admins instanceof List
            assert pending instanceof List
            members.each() {
                exists = sql.firstRow("SELECT USER_ID FROM OF_USERS.OF_USER_GROUP WHERE USER_ID = ${it} AND GROUP_ID = ${groupId}")?.USER_ID
                if (!exists) {
                    sql.executeInsert "INSERT INTO OF_USERS.OF_USER_GROUP (USER_ID, GROUP_ID, STATUS_CODE, ROLE_CODE) VALUES (?, ?, 'A', 'OPR')", [it, groupId]
                }
            }
            admins.each() {
                exists = sql.firstRow("SELECT USER_ID FROM OF_USERS.OF_USER_GROUP WHERE USER_ID = ${it} AND GROUP_ID = ${groupId}")?.USER_ID
                if (!exists) {
                    sql.executeInsert "INSERT INTO OF_USERS.OF_USER_GROUP (USER_ID, GROUP_ID, STATUS_CODE, ROLE_CODE) VALUES (?, ?, 'A', 'ADM')", [it, groupId]
                } else {
                    sql.executeUpdate "UPDATE OF_USERS.OF_USER_GROUP SET ROLE_CODE = 'ADM' WHERE USER_ID = ? AND GROUP_ID = ?", [it, groupId]
                }
            }
            pending.each() {
                exists = sql.firstRow("SELECT USER_ID FROM OF_USERS.OF_USER_GROUP WHERE USER_ID = ${it} AND GROUP_ID = ${groupId}")?.USER_ID
                if (!exists) {
                    sql.executeInsert "INSERT INTO OF_USERS.OF_USER_GROUP (USER_ID, GROUP_ID, STATUS_CODE, ROLE_CODE) VALUES (?, ?, 'P', 'OPR')", [it, groupId]
                } else {
                    sql.executeUpdate "UPDATE OF_USERS.OF_USER_GROUP SET STATUS_CODE = 'P' WHERE USER_ID = ? AND GROUP_ID = ?", [it, groupId]
                }
            }
            if (logosPath && it.logo) {
                def file = new File(logosPath, it.logo.tokenize('/').last())
                if (file.exists() && file.canRead()) {
                    def ext = it.logo.tokenize('.').last()
                    def mimeType = 'image/' + ext
                    InputStream inputStream = new FileInputStream(file)
                    byte[] byteArray = IOUtils.toByteArray(inputStream)
                    sql.executeUpdate "UPDATE OF_USERS.OF_GROUP SET LOGO = ?, LOGO_CONTENT_TYPE = ? WHERE ID = ?", [byteArray, mimeType, groupId]
                }
            }
        } catch (JdbcSQLException e) {
            println "ERROR! Impossible to insert Group with ID = ${groupId}"
            print e
        }
    }
    conn.close()
    cp.dispose()
}

def importImagery(jsonArray, jdbcConnectionString, autoCommit) {
    JdbcConnectionPool cp = JdbcConnectionPool.create(jdbcConnectionString, "", "")
    Connection conn = cp.getConnection()
    conn.autoCommit = autoCommit
    jsonArray.each() {
        int imageryId = it.id
        int groupId = it.institution
        try {
            def sql = new Sql(conn)
            def insertSql = "INSERT INTO OF_USERS.OF_RESOURCE_GROUP (RESOURCE_TYPE, RESOURCE_ID, GROUP_ID) VALUES ('IMAGERY', ?, ?)"
            def params = [imageryId, groupId]
            sql.executeInsert insertSql, params
        } catch (JdbcSQLException e) {
            println "ERROR! Impossible to insert Imagery with ID = ${imageryId}"
        }
    }
    conn.close()
    cp.dispose()
}

def cli = new CliBuilder(usage:'groovy of-users-importer.groovy [-hd] {-u|-g|-i} <sourceFile> <jdbcConnectionString> {logosPath}')
cli.with {
    h longOpt: 'help', 'Show usage information'
    d longOpt: 'demo', 'DEMO (data will be not saved into the database)'
    u longOpt: 'process-users', 'Process Users'
    g longOpt: 'process-groups', 'Process Groups'
    r longOpt: 'process-resources', 'Process Resources (Imagery)'
}
def options = cli.parse(args)
if (!options) {
    return
}
if (options.h) {
    cli.usage()
    return
}
autoCommit = true
if (options.d) {
    autoCommit = false
}
def extraArguments = options.arguments()
if (extraArguments) {
    def jsonString = readJsonStringFromFile(extraArguments[0])
    def jsonSlurper = new JsonSlurper()
    def jsonArray = jsonSlurper.parseText(jsonString)
    assert jsonArray instanceof List
    def jdbcConnectionString = extraArguments[1]
    if (options.u) {
        importUsers(jsonArray, jdbcConnectionString, autoCommit)
    } else if (options.g) {
        def logosPath = extraArguments[2]
        importGroups(jsonArray, jdbcConnectionString, logosPath, autoCommit)
    } else if (options.r) {
        importImagery(jsonArray, jdbcConnectionString, autoCommit)
    }
}
