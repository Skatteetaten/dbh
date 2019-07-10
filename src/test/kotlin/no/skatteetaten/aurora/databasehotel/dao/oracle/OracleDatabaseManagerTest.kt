package no.skatteetaten.aurora.databasehotel.dao.oracle

import assertk.assertThat
import assertk.assertions.contains
import org.junit.jupiter.api.Test
import java.sql.SQLException

class OracleDatabaseManagerTest {

    @Test
    fun `Get db error message`() {
        val dbError = "ORA-00059: maximum number of DB_FILES exceeded"
        val message = RuntimeException("test test", RuntimeException(SQLException(dbError)))
            .getDbMessage()
        assertThat(message).contains(dbError)
    }
}