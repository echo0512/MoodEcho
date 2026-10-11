package com.example.moodecho;

import com.example.moodecho.mapper.DiaryEmbeddingJobMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** 使用独立MySQL测试库；随机前缀表，仅创建/删除本测试自己的表。 */
@EnabledIfEnvironmentVariable(named = "SEMANTIC_TEST_JDBC_URL", matches = ".+")
class EmbeddingJobSqlIntegrationTest {
    @Test void validatesQueueTransactionsRevisionFencingLeasesAndCascade() throws Exception {
        String prefix = "eval_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String diaryTable = prefix + "_diary";
        String jobTable = prefix + "_job";
        try (Connection connection = connect(); Connection other = connect()) {
            try {
                execute(connection, "CREATE TABLE " + diaryTable + " (id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, deleted INT DEFAULT 0, status INT DEFAULT 1) ENGINE=InnoDB");
                String schema;
                try (var stream = getClass().getResourceAsStream("/sql/migrations/20261008_embedding_jobs.sql")) {
                    schema = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                            .replaceAll("(?m)^--.*$", "").trim();
                }
                schema = schema.replace("diary_embedding_job", jobTable)
                        .replace("REFERENCES diary(id)", "REFERENCES " + diaryTable + "(id)")
                        .replace("fk_embedding_job_diary", prefix + "_fk");
                execute(connection, schema);
                // 迁移可重复执行。
                execute(connection, schema);
                execute(connection, "INSERT INTO " + diaryTable + " (id, user_id) VALUES (1, 7), (2, 7), (3, 8)");
                String enqueue = sql("enqueue", diaryTable, jobTable, Map.of("diaryId", "1", "userId", "7"));
                connection.setAutoCommit(false);
                execute(connection, enqueue);
                connection.rollback();
                assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM " + jobTable));
                connection.setAutoCommit(true);
                execute(connection, enqueue);

                // 身份隔离：其他用户不能提交或看到这篇日记的任务。
                execute(connection, sql("enqueue", diaryTable, jobTable, Map.of("diaryId", "2", "userId", "8")));
                assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM " + jobTable));
                String status = sql("status", diaryTable, jobTable, Map.of("userId", "8"));
                assertEquals(0, scalar(connection, status));

                connection.setAutoCommit(false);
                String next = sql("nextJob", diaryTable, jobTable, Map.of("maxAttempts", "5"));
                assertEquals(1, scalar(connection, next));
                other.setAutoCommit(false);
                try (var statement = other.createStatement(); ResultSet result = statement.executeQuery(next)) {
                    assertFalse(result.next(), "第二个worker应跳过已锁定任务");
                }
                other.rollback();
                other.setAutoCommit(true);
                execute(connection, sql("claim", diaryTable, jobTable,
                        Map.of("diaryId", "1", "revision", "1", "token", "'old'", "leaseSeconds", "1800")));
                connection.commit();
                connection.setAutoCommit(true);

                // 新编辑产生revision=2。revision=1的回执必须失效。
                execute(connection, enqueue);
                String oldFinish = sql("finish", diaryTable, jobTable, Map.of("job.diaryId", "1", "job.revision", "1",
                        "job.claimToken", "'old'", "state", "'ready'", "errorCode", "NULL", "delaySeconds", "0"));
                assertEquals(0, execute(connection, oldFinish));
                assertEquals(2, scalar(connection, "SELECT revision FROM " + jobTable + " WHERE diary_id=1"));
                // 模拟重启后租约过期，可重新领取；新token使旧worker失效。
                execute(connection, "UPDATE " + jobTable + " SET state='processing', attempts=1, claim_token='abandoned', lease_until=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP(6)) WHERE diary_id=1");
                connection.setAutoCommit(false);
                assertEquals(1, scalar(connection, next));
                execute(connection, sql("claim", diaryTable, jobTable,
                        Map.of("diaryId", "1", "revision", "2", "token", "'new'", "leaseSeconds", "1800")));
                connection.commit();
                connection.setAutoCommit(true);
                String staleToken = sql("finish", diaryTable, jobTable, Map.of("job.diaryId", "1", "job.revision", "2",
                        "job.claimToken", "'abandoned'", "state", "'ready'", "errorCode", "NULL", "delaySeconds", "0"));
                assertEquals(0, execute(connection, staleToken));
                String newFinish = staleToken.replace("'abandoned'", "'new'");
                assertEquals(1, execute(connection, newFinish));

                execute(connection, sql("enqueueAll", diaryTable, jobTable, Map.of("userId", "7")));
                assertEquals(2, scalar(connection, "SELECT COUNT(*) FROM " + jobTable));
                execute(connection, "UPDATE " + jobTable + " SET state='processing', attempts=5, lease_until=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP(6)) WHERE diary_id=1");
                execute(connection, sql("failExhausted", diaryTable, jobTable, Map.of("maxAttempts", "5")));
                assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM " + jobTable + " WHERE state='failed'"));
                execute(connection, "DELETE FROM " + diaryTable + " WHERE id=1");
                assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM " + jobTable + " WHERE diary_id=1"));
            } finally {
                if (!other.getAutoCommit()) other.rollback();
                if (!connection.getAutoCommit()) connection.rollback();
                connection.setAutoCommit(true);
                execute(connection, "DROP TABLE IF EXISTS " + jobTable);
                execute(connection, "DROP TABLE IF EXISTS " + diaryTable);
            }
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(System.getenv("SEMANTIC_TEST_JDBC_URL"),
                System.getenv().getOrDefault("SEMANTIC_TEST_DB_USER", "root"),
                System.getenv().getOrDefault("SEMANTIC_TEST_DB_PASSWORD", ""));
    }

    // 直接读取生产Mapper SQL，避免测试维护另一份SQL实现。
    private String sql(String method, String diaryTable, String jobTable, Map<String, String> values) {
        var declaration = Arrays.stream(DiaryEmbeddingJobMapper.class.getMethods())
                .filter(candidate -> candidate.getName().equals(method)).findFirst().orElseThrow();
        String[] lines = declaration.isAnnotationPresent(Insert.class) ? declaration.getAnnotation(Insert.class).value()
                : declaration.isAnnotationPresent(Select.class) ? declaration.getAnnotation(Select.class).value()
                : declaration.getAnnotation(Update.class).value();
        String sql = String.join(" ", lines).replace("diary_embedding_job", jobTable).replaceAll("\\bdiary\\b", diaryTable);
        var matcher = Pattern.compile("#\\{([^}]+)}").matcher(sql);
        return matcher.replaceAll(match -> java.util.regex.Matcher.quoteReplacement(requireParameter(values, match.group(1))));
    }

    private String requireParameter(Map<String, String> values, String key) {
        return java.util.Objects.requireNonNull(values.get(key), key);
    }

    private int execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { return statement.executeUpdate(sql); }
    }

    private long scalar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }
}
