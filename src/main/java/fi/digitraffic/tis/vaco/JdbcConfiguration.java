package fi.digitraffic.tis.vaco;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

@Configuration
public class JdbcConfiguration extends AbstractJdbcConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcConfiguration.class);

    @Override
    protected List<?> userConverters() {
        return Arrays.asList(new JsonNodeToPgObjectConverter(), new PgObjectToJsonNodeConverter());
    }

    @WritingConverter
    static class JsonNodeToPgObjectConverter implements Converter<JsonNode, PGobject> {
        @Override
        public PGobject convert(JsonNode source) {
            PGobject jsonObject = new PGobject();
            jsonObject.setType("jsonb");
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                jsonObject.setValue(objectMapper.writeValueAsString(source));
            } catch (SQLException | JsonProcessingException e) {
                LOGGER.error("Failed Jdbc conversion from JsonNode to PGobject", e);
            }
            return jsonObject;
        }
    }

    @ReadingConverter
    static class PgObjectToJsonNodeConverter implements Converter<PGobject, JsonNode> {
        @Override
        public JsonNode convert(PGobject source) {
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                return objectMapper.readValue(source.getValue(), new TypeReference<>() {});
            } catch (IOException e) {
                LOGGER.error("Failed Jdbc conversion from PGobject to JsonNode", e);
            }
            return null;
        }
    }
}
