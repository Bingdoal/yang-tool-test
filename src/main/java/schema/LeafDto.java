package schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import schema.type.TypeProperty;

import java.util.List;
import java.util.Optional;

@Data
@JsonIgnoreProperties(value = {"array"})
public class LeafDto {
    private String type;
    private boolean config = true;
    private boolean isArray = false;
    private boolean mandatory = false;
    private Optional<String> defaultValue;
    private Optional<String> description;

    private TypeProperty typeProperty;
}
