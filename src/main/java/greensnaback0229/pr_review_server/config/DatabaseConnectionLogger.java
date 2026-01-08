package greensnaback0229.pr_review_server.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DatabaseConnectionLogger {
    
    @Value("${spring.datasource.url}")
    private String datasourceUrl;
    
    @Value("${spring.datasource.username}")
    private String datasourceUsername;
    
    @Value("${MYSQL_HOST:localhost}")
    private String mysqlHost;
    
    @Value("${MYSQL_PORT:3306}")
    private String mysqlPort;
    
    @Value("${MYSQL_DATABASE:pr_review}")
    private String mysqlDatabase;
    
    @EventListener(ApplicationReadyEvent.class)
    public void logDatabaseConnectionInfo() {
        log.info("=== Database Connection Configuration ===");
        log.info("JDBC URL: {}", datasourceUrl);
        log.info("Username: {}", datasourceUsername);
        log.info("MYSQL_HOST: {}", mysqlHost);
        log.info("MYSQL_PORT: {}", mysqlPort);
        log.info("MYSQL_DATABASE: {}", mysqlDatabase);
        log.info("=========================================");
    }
}
