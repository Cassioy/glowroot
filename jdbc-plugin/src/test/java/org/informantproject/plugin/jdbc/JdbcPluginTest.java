/**
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.informantproject.plugin.jdbc;

import static org.fest.assertions.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.hsqldb.jdbc.JDBCDriver;
import org.informantproject.testkit.AppUnderTest;
import org.informantproject.testkit.Config.PluginConfig;
import org.informantproject.testkit.InformantContainer;
import org.informantproject.testkit.Trace;
import org.informantproject.testkit.Trace.Metric;
import org.informantproject.testkit.Trace.Span;
import org.informantproject.testkit.TraceMarker;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Basic test of the jdbc plugin.
 * 
 * @author Trask Stalnaker
 * @since 0.5
 */
public class JdbcPluginTest {

    private static final String PLUGIN_ID = "org.informantproject.plugins:jdbc-plugin";

    private static InformantContainer container;

    @BeforeClass
    public static void setUp() throws Exception {
        container = InformantContainer.create();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        container.closeAndDeleteFiles();
    }

    @Test
    public void testStatement() throws Exception {
        // given
        container.getInformant().setPersistenceThresholdMillis(0);
        // when
        container.executeAppUnderTest(ExecuteStatementAndIterateOverResults.class);
        // then
        Trace trace = container.getInformant().getLastTrace();
        assertThat(trace.getSpans()).hasSize(2);
        Span rootSpan = trace.getSpans().get(0);
        assertThat(rootSpan.getMessage().getText()).isEqualTo("mock trace marker");
        Span jdbcSpan = trace.getSpans().get(1);
        assertThat(jdbcSpan.getMessage().getText()).startsWith(
                "jdbc execution: select * from employee => 1 row [connection: ");
    }

    @Test
    public void testPreparedStatement() throws Exception {
        // given
        container.getInformant().setPersistenceThresholdMillis(0);
        // when
        container.executeAppUnderTest(ExecutePreparedStatementAndIterateOverResults.class);
        // then
        Trace trace = container.getInformant().getLastTrace();
        assertThat(trace.getSpans()).hasSize(2);
        Span rootSpan = trace.getSpans().get(0);
        assertThat(rootSpan.getMessage().getText()).isEqualTo("mock trace marker");
        Span jdbcSpan = trace.getSpans().get(1);
        assertThat(jdbcSpan.getMessage().getText()).startsWith(
                "jdbc execution: select * from employee"
                        + " where name like ? ['john%'] => 1 row [connection: ");
    }

    @Test
    public void testCommit() throws Exception {
        // given
        container.getInformant().setPersistenceThresholdMillis(0);
        // when
        container.executeAppUnderTest(ExecuteJdbcCommit.class);
        // then
        Trace trace = container.getInformant().getLastTrace();
        assertThat(trace.getSpans()).hasSize(3);
        Span rootSpan = trace.getSpans().get(0);
        assertThat(rootSpan.getMessage().getText()).isEqualTo("mock trace marker");
        Span jdbcInsertSpan = trace.getSpans().get(1);
        assertThat(jdbcInsertSpan.getMessage().getText()).startsWith(
                "jdbc execution: insert into employee (name) values ('john doe') [connection: ");
        Span jdbcCommitSpan = trace.getSpans().get(2);
        assertThat(jdbcCommitSpan.getMessage().getText()).startsWith("jdbc commit [connection: ");
        assertThat(trace.getMetrics()).hasSize(4);
        // ordering is by total desc, so not fixed (though root span will be first since it
        // encompasses all other timings)
        assertThat(trace.getMetrics().get(0).getName()).isEqualTo("mock trace marker");
        assertThat(trace.getMetricNames()).containsOnly("mock trace marker", "jdbc execute",
                "jdbc commit", "jdbc statement close");
    }

    @Test
    public void testResultSetValueMetric() throws Exception {
        // given
        container.getInformant().setPersistenceThresholdMillis(0);
        PluginConfig pluginConfig = container.getInformant().getPluginConfig(PLUGIN_ID);
        pluginConfig.setProperty("captureResultSetGet", true);
        container.getInformant().updatePluginConfig(PLUGIN_ID, pluginConfig);
        // when
        container.executeAppUnderTest(ExecuteStatementAndIterateOverResults.class);
        // then
        Trace trace = container.getInformant().getLastTraceSummary();
        boolean found = false;
        for (Metric metric : trace.getMetrics()) {
            if (metric.getName().equals("jdbc resultset value")) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    public void testMetadataMetric() throws Exception {
        // given
        container.getInformant().setPersistenceThresholdMillis(0);
        // when
        container.executeAppUnderTest(AccessMetaData.class);
        // then
        Trace trace = container.getInformant().getLastTrace();
        assertThat(trace.getSpans()).hasSize(1);
        Span rootSpan = trace.getSpans().get(0);
        assertThat(rootSpan.getMessage().getText()).isEqualTo("mock trace marker");
        assertThat(trace.getMetrics().size()).isEqualTo(2);
        assertThat(trace.getMetrics().get(0).getName()).isEqualTo("mock trace marker");
        assertThat(trace.getMetrics().get(1).getName()).isEqualTo("jdbc metadata");
    }

    // TODO testPreparedStatement
    // select * from employee where name like ?
    // [john%]

    // TODO testPreparedStatementWithSetNull
    // insert into employee (name) values (?)

    // TODO testCallableStatement
    // select * from employee where name = ?
    // [john%]

    // TODO make a release build profile that runs all tests against
    // Hsqldb, Oracle, SQLServer, MySQL, ...

    // TODO testSqlServerIssue
    // exploit issue with SQLServer PreparedStatement.getParameterMetaData(), result being that
    // getParameterMetaData() cannot be used in the jdbc plugin, this test (if it can be run
    // against SQLServer) ensures that getParameterMetaData() doesn't sneak back in the future
    // select * from employee where (name like ?)
    // [john%]

    private static Connection createConnection() throws SQLException {
        // set up database
        Connection connection = JDBCDriver.getConnection("jdbc:hsqldb:mem:test", null);
        Statement statement = connection.createStatement();
        try {
            statement.execute("create table employee (name varchar(100))");
            statement.execute("insert into employee (name) values ('john doe')");
        } finally {
            statement.close();
        }
        return connection;
    }

    private static void closeConnection(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        try {
            statement.execute("drop table employee");
        } finally {
            statement.close();
        }
        connection.close();
    }

    public static class ExecuteStatementAndIterateOverResults implements AppUnderTest, TraceMarker {

        private Connection connection;
        public void executeApp() throws Exception {
            connection = createConnection();
            try {
                traceMarker();
            } finally {
                closeConnection(connection);
            }
        }
        public void traceMarker() throws Exception {
            Statement statement = connection.createStatement();
            try {
                statement.execute("select * from employee");
                ResultSet rs = statement.getResultSet();
                while (rs.next()) {
                    rs.getString(1);
                }
            } finally {
                statement.close();
            }
        }
    }

    public static class ExecutePreparedStatementAndIterateOverResults implements AppUnderTest,
            TraceMarker {

        private Connection connection;
        public void executeApp() throws Exception {
            connection = createConnection();
            try {
                traceMarker();
            } finally {
                closeConnection(connection);
            }
        }
        public void traceMarker() throws Exception {
            PreparedStatement preparedStatement = connection
                    .prepareStatement("select * from employee where name like ?");
            preparedStatement.setString(1, "john%");
            try {
                preparedStatement.execute();
                ResultSet rs = preparedStatement.getResultSet();
                while (rs.next()) {
                    rs.getString(1);
                }
            } finally {
                preparedStatement.close();
            }
        }
    }

    public static class ExecuteJdbcCommit implements AppUnderTest, TraceMarker {
        private Connection connection;
        public void executeApp() throws Exception {
            connection = createConnection();
            connection.setAutoCommit(false);
            try {
                traceMarker();
            } finally {
                closeConnection(connection);
            }
        }
        public void traceMarker() throws Exception {
            Statement statement = connection.createStatement();
            try {
                statement.execute("insert into employee (name) values ('john doe')");
            } finally {
                statement.close();
            }
            connection.commit();
        }
    }

    public static class AccessMetaData implements AppUnderTest, TraceMarker {
        private Connection connection;
        public void executeApp() throws Exception {
            connection = createConnection();
            connection.setAutoCommit(false);
            try {
                traceMarker();
            } finally {
                closeConnection(connection);
            }
        }
        public void traceMarker() throws Exception {
            connection.getMetaData().getTables(null, null, null, null);
        }
    }
}
