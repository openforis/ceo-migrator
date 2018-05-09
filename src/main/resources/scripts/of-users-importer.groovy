@GrabConfig(systemClassLoader=true)
@Grab(group='com.h2database', module='h2', version='1.4.197')
@Grab(group='com.google.code.gson', module='gson', version='2.8.4')
@Grab(group='commons-codec', module='commons-codec', version='1.11')

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.sql.*
import groovy.sql.Sql
import org.h2.jdbcx.JdbcConnectionPool
import org.h2.jdbc.JdbcSQLException
import java.security.MessageDigest
import org.apache.commons.codec.binary.Hex

Sql.LOG.level = java.util.logging.Level.SEVERE

def encodePassword(plainPassword) {
    MessageDigest messageDigest = MessageDigest.getInstance("MD5")
    byte[] digest = messageDigest.digest(plainPassword.getBytes())
    char[] resultChar = Hex.encodeHex(digest)
    return new String(resultChar)
}

def readJsonArrayFromFile(filename) {
    def file = new File(filename)
    assert file.exists() : "file not found"
    assert file.canRead() : "file cannot be read"
    def jsonString = file.getText("UTF-8")
    JsonParser jsonParser = new JsonParser()
    JsonElement jsonElement = jsonParser.parse(jsonString)
    JsonArray jsonArray = jsonElement.getAsJsonArray()
    return jsonArray
}

def importUsers(jsonArray, jdbcConnectionString, autoCommit) {
    JdbcConnectionPool cp = JdbcConnectionPool.create(jdbcConnectionString, "", "")
    Connection conn = cp.getConnection()
    conn.autoCommit = autoCommit
    jsonArray.each() {
        String password = encodePassword(it.password.getAsString())
        int userId = it.id.getAsInt()
        def sql = new Sql(conn)
        def insertSql = "INSERT INTO OF_USERS.OF_USER (ID, USERNAME, PASSWORD, ENABLED, LAT, LON, LOCATION, AFFILIATIONS, CREATION_DATE, RESET_KEY) VALUES (?, ?, ?, ?, ?, ?, ?, ?, now(), ?)"
        def params = [userId, it.email.getAsString(), password, true, null, null, "", "", ""]
        try {
            sql.executeInsert insertSql, params
        } catch (JdbcSQLException e) {
            println "ERROR! Impossible to insert User with ID = ${userId}"
        }
    }
    conn.close()
    cp.dispose()
}

def importGroups(jsonArray, jdbcConnectionString, autoCommit) {
    JdbcConnectionPool cp = JdbcConnectionPool.create(jdbcConnectionString, "", "")
    Connection conn = cp.getConnection()
    conn.autoCommit = autoCommit
    jsonArray.each() {
        int groupId = it.id.getAsInt()
        try {
            def sql = new Sql(conn)
            def insertSql = "INSERT INTO OF_USERS.OF_GROUP (ID, NAME, LABEL, DESCRIPTION, ENABLED, SYSTEM_DEFINED, VISIBILITY_CODE, LOGO, URL, CREATION_DATE, LOGO_CONTENT_TYPE) VALUES (?, ?, '', ?, true, true, ?, ?, ?, now(), ?)"
            def params = [groupId, it.name.getAsString(), it.description.getAsString(), "PUB", "", it.url.getAsString(), ""]
            sql.executeInsert insertSql, params
            def members = it.get('members').getAsJsonArray()
            members.each() {
                def insertSql1 = "INSERT INTO OF_USERS.OF_USER_GROUP (USER_ID, GROUP_ID, STATUS_CODE, ROLE_CODE) VALUES (?, ?, 'A', 'OPR')"
                def params1 = [it.getAsInt(), groupId]
                sql.executeInsert insertSql1, params1
            }
            def admins = it.get('admins').getAsJsonArray()
            admins.each() {
                def insertSql2 = "INSERT INTO OF_USERS.OF_USER_GROUP (USER_ID, GROUP_ID, STATUS_CODE, ROLE_CODE) VALUES (?, ?, 'A', 'ADM')"
                def params2 = [it.getAsInt(), groupId]
                sql.executeInsert insertSql2, params2
            }
            def pending = it.get('pending').getAsJsonArray()
            pending.each() {
                def insertSql3 = "INSERT INTO OF_USERS.OF_USER_GROUP (USER_ID, GROUP_ID, STATUS_CODE, ROLE_CODE) VALUES (?, ?, 'P', 'OPR')"
                def params3 = [it.getAsInt(), groupId]
                sql.executeInsert insertSql3, params3
            }
        } catch (JdbcSQLException e) {
            println "ERROR! Impossible to insert Group with ID = ${groupId}"
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
        int imageryId = it.id.getAsInt()
        int groupId = it.institution.getAsInt()
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

def cli = new CliBuilder(usage:'groovy of-users-importer.groovy [-h] {-u|-g|-i} <sourceFile> <jdbcConnectionString>')
cli.with {
    h longOpt: 'help', 'Show usage information'
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
def extraArguments = options.arguments()
if (extraArguments) {
    JsonArray jsonArray = readJsonArrayFromFile(extraArguments[0])
    def jdbcConnectionString = extraArguments[1]
    autoCommit = true
    if (options.u) {
        importUsers(jsonArray, jdbcConnectionString, autoCommit)
    } else if (options.g) {
        importGroups(jsonArray, jdbcConnectionString, autoCommit)
    } else if (options.r) {
        importImagery(jsonArray, jdbcConnectionString, autoCommit)
    }
}
