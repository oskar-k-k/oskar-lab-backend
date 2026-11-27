package dev.oskar_lab.backend;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;
import java.util.ArrayList;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;

/** Describes the existing server-only bridge without publishing credentials. */
@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI platformApi() {
        return new OpenAPI().info(new Info().title("Oskar Lab API").version("1.0.0"))
            .components(new Components().addSecuritySchemes("bridge", new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-Auth-Bridge"))
                .addSecuritySchemes("session", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")));
    }

    @Bean
    OpenApiCustomizer bridgeSecurity() {
        return api -> {
          api.getPaths().forEach((path, item) -> {
            if (path.startsWith("/internal/")) item.readOperations().forEach(operation -> {
                operation.addSecurityItem(new SecurityRequirement().addList("bridge"));
                for (var status : List.of("400", "401", "403", "404", "409", "413", "415", "429")) {
                    operation.getResponses().addApiResponse(status, new ApiResponse().description("Request rejected; code is safe for localized feedback")
                        .content(new Content().addMediaType("application/json", new MediaType().schema(
                            new ObjectSchema().addProperty("code", new StringSchema()).required(List.of("code"))))));
                }
            });
          });
          var auth = api.getPaths().get("/internal/auth/{action}").getPost();
          auth.setDescription("Server-only account actions. complete, current and logout also require a backend session Bearer token. Input fields depend on the action and are validated by AccountService.");
          auth.getRequestBody().getContent().values().forEach(media -> media.getSchema().setAdditionalProperties(true));
          // These DTOs always serialize every field; validation annotations alone omit response requirements.
          var schemas = api.getComponents().getSchemas();
          for (var name : List.of("ExerciseDto", "Entry", "Plan", "PlanPage", "SavePlan", "SetResult", "SaveLog", "Log", "History", "AppEntity", "PageAppEntity",
                  "TravelTrip", "TravelTripPage", "TravelEntry", "TravelEntryPage", "TravelPoint", "TravelViewer", "TravelAttachment", "PackingList", "PackingPerson", "PackingGroup", "PackingItem", "TravelSavedPlace", "TravelSavedPlacePage")) {
              var schema = schemas.get(name);
              if (schema == null || schema.getProperties() == null) continue;
              var required = new ArrayList<String>(schema.getProperties().keySet());
              if (name.equals("Entry")) required.remove("trackingMode");
              schema.setRequired(required);
          }
          nullable(schemas.get("Entry"), "targetMin", "targetMax", "restMin", "restMax");
          nullable(schemas.get("SetResult"), "weight");
          nullable(schemas.get("AppEntity"), "title", "description");
        };
    }

    private static void nullable(io.swagger.v3.oas.models.media.Schema<?> schema, String... fields) {
        if (schema == null) return;
        for (var field : fields) schema.getProperties().get(field).setNullable(true);
    }
}
