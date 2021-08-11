package yang.testtools.yangparser.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import yang.testtools.yangparser.schema.type.TypeProperty;

import java.util.Optional;

@Data
@JsonIgnoreProperties(value = {"array"})
public class LeafDto extends BaseNodeDto {
    private String type;
    private boolean mandatory = false;
    private Optional<String> defaultValue;

    private TypeProperty typeProperty;
}
