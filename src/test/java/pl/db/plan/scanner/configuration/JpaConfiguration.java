package pl.db.plan.scanner.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import pl.db.plan.scanner.inspector.SqlCaptureInspector;

@TestConfiguration
public class JpaConfiguration {

    @Autowired
    private SqlCaptureInspector inspector;

    @Bean
    public HibernatePropertiesCustomizer testHibernateCustomizer() {
        return props -> props.put("hibernate.session_factory.statement_inspector", inspector);
    }
}
