package no.skatteetaten.aurora.databasehotel;

import static no.skatteetaten.aurora.databasehotel.EnvVarMapper.mapEnvironmentVarsToSystemProperties;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import no.skatteetaten.aurora.annotations.AuroraApplication;

@AuroraApplication
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
@EnableAsync
@EnableScheduling
public class Main implements InitializingBean {

    @Autowired
    private DbhInitializer dbhInitializer;

    public static void main(String[] args) throws Exception {

        mapEnvironmentVarsToSystemProperties();

        SpringApplication.run(Main.class, args);
    }

    @Override
    public void afterPropertiesSet() throws Exception {

        dbhInitializer.configure();
    }
}
