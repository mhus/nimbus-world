package de.mhus.nimbus.world.shared.redis;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

@Configuration
@EnableConfigurationProperties(WorldRedisProperties.class)
public class WorldRedisConfig {

    @Bean
    public LettuceConnectionFactory worldRedisConnectionFactory(WorldRedisProperties props) {
        RedisStandaloneConfiguration conf = new RedisStandaloneConfiguration();
        conf.setHostName(props.getHost());
        conf.setPort(props.getPort());
        conf.setDatabase(props.getDatabase());
        if (props.getPassword() != null && !props.getPassword().isBlank()) {
            conf.setPassword(props.getPassword());
        }
        return new LettuceConnectionFactory(conf);
    }

    @Bean
    public StringRedisTemplate worldRedisTemplate(LettuceConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    // @Primary: Boot 4's Redis auto-configuration also contributes a
    // RedisMessageListenerContainer bean, so injection by type would be ambiguous.
    @Bean
    @Primary
    public RedisMessageListenerContainer worldRedisMessageListenerContainer(RedisConnectionFactory factory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        return container;
    }
}
