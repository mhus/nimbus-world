package de.mhus.nimbus.shared.utils;

import org.springframework.web.client.RestTemplate;

public class RestTemplateUtil {

    public static RestTemplate create(String token) {
        var template = new RestTemplate();
        if (token != null) {
            template.getInterceptors().add((request, body, execution) -> {
                request.getHeaders().set("Authorization", "Bearer " + token);
                return execution.execute(request, body);
            });
        }
        return template;
    }
}
