package de.mhus.nimbus.world.shared.redis;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    @Bean
    public RedisMessageListenerContainer worldRedisMessageListenerContainer(RedisConnectionFactory factory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        return container;
    }
}
